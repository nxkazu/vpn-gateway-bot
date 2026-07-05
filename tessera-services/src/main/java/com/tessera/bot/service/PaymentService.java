package com.tessera.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tessera.bot.config.AppConfig;
import com.tessera.bot.db.InvoiceRepository;
import com.tessera.bot.db.Plans;
import com.tessera.bot.db.model.PaymentInvoice;
import com.tessera.core.api.CreatedInvoice;
import com.tessera.core.api.InvoiceStatus;
import com.tessera.core.api.PaymentProvider;
import com.tessera.core.api.PaymentRequest;
import com.tessera.bot.remnawave.dto.RemnawaveUser;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of {@link PaymentProvider}s (contributed by payment modules) plus the
 * invoice lifecycle: create -> user pays -> check -> activate plan + reward referral.
 * Promo codes discount the invoice amount and are redeemed exactly once, when the
 * invoice is first confirmed as paid.
 */
@Slf4j
public class PaymentService {

    public record CreateResult(long invoiceId, String payUrl, Plans.Plan plan, long priceRub) {}
    public record WebhookResult(boolean accepted, String provider, String externalId,
                                PaymentInvoice invoice, CheckOutcome outcome) {}

    public sealed interface CheckOutcome {
        record Paid(RemnawaveUser user, Plans.Plan plan) implements CheckOutcome {}
        record Pending(PaymentInvoice invoice) implements CheckOutcome {}
        record Failed() implements CheckOutcome {}
    }

    private final Map<String, PaymentProvider> providers = new LinkedHashMap<>();
    private final AppConfig config;
    private final InvoiceRepository invoices;
    private final SubscriptionService subscriptions;
    private final ReferralService referrals;
    private final PromoService promos;
    private final AnalyticsService analytics;
    private final ObjectMapper json = new ObjectMapper();

    public PaymentService(AppConfig config, InvoiceRepository invoices,
                          SubscriptionService subscriptions, ReferralService referrals,
                          PromoService promos) {
        this(config, invoices, subscriptions, referrals, promos, null);
    }

    public PaymentService(AppConfig config, InvoiceRepository invoices,
                          SubscriptionService subscriptions, ReferralService referrals,
                          PromoService promos, AnalyticsService analytics) {
        this.config = config;
        this.invoices = invoices;
        this.subscriptions = subscriptions;
        this.referrals = referrals;
        this.promos = promos;
        this.analytics = analytics;
    }

    /** Called by payment modules (via ModuleContext) during startup. */
    public synchronized void register(PaymentProvider provider) {
        providers.put(provider.id(), provider);
        log.info("Payment provider registered: {} ({})", provider.id(), provider.displayName());
    }

    public List<PaymentProvider> providers() {
        return new ArrayList<>(providers.values());
    }

    public boolean hasProviders() {
        return !providers.isEmpty();
    }

    public Optional<PaymentProvider> byId(String id) {
        return Optional.ofNullable(providers.get(id));
    }

    /**
     * Creates a provider invoice and persists it locally as PENDING. When a valid promo
     * code is supplied the price is discounted; invalid/expired codes are ignored.
     */
    public CreateResult createInvoice(long telegramId, String providerId, Plans.Plan plan,
                                      String promoCode) throws Exception {
        PaymentProvider provider = byId(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment provider: " + providerId));

        long priceRub = plan.priceRub();
        String appliedPromo = null;
        if (promoCode != null && promos != null) {
            Optional<PromoService.Promo> p = promos.validate(promoCode, telegramId);
            if (p.isPresent()) {
                priceRub = PromoService.discountedRub(priceRub, p.get().discountPercent());
                appliedPromo = p.get().code();
            }
        }

        long amountMinor = priceRub * 100L;
        PaymentRequest request = new PaymentRequest(
                telegramId, plan.code(), plan.title(), amountMinor, "RUB",
                "Tessera VPN \u2014 " + plan.title(),
                "https://t.me/" + config.botUsername());
        CreatedInvoice created = provider.createInvoice(request);
        long id = invoices.insert(telegramId, plan.code(), provider.id(),
                created.externalId(), created.payUrl(), amountMinor, "RUB", appliedPromo);
        log.info("Invoice {} created: user={} plan={} provider={} ext={} promo={}",
                id, telegramId, plan.code(), provider.id(), created.externalId(), appliedPromo);
        if (analytics != null) {
            analytics.track(telegramId, AnalyticsService.Event.INVOICE_CREATED,
                    Map.of("plan", plan.code(), "provider", provider.id(), "priceRub", priceRub));
        }
        return new CreateResult(id, created.payUrl(), plan, priceRub);
    }

    /** Backwards-compatible overload without a promo code. */
    public CreateResult createInvoice(long telegramId, String providerId, Plans.Plan plan) throws Exception {
        return createInvoice(telegramId, providerId, plan, null);
    }

    /**
     * Re-checks an invoice with its provider. On first confirmation of payment the plan
     * is activated, the invoice marked PAID, a payments ledger row written, the promo
     * code (if any) redeemed, and the referral reward triggered. Idempotent for
     * already-PAID invoices.
     */
    public CheckOutcome checkAndActivate(long telegramId, long invoiceId) throws Exception {
        Optional<PaymentInvoice> found = invoices.find(invoiceId);
        if (found.isEmpty() || found.get().telegramId() != telegramId) {
            return new CheckOutcome.Failed();
        }
        PaymentInvoice inv = found.get();
        Plans.Plan plan = Plans.byCode(inv.planCode());
        if (plan == null) return new CheckOutcome.Failed();

        if ("PAID".equals(inv.status())) {
            // Already processed earlier; just report the current subscription.
            RemnawaveUser u = subscriptions.currentSubscription(telegramId).orElse(null);
            return u != null ? new CheckOutcome.Paid(u, plan) : new CheckOutcome.Failed();
        }

        PaymentProvider provider = byId(inv.provider()).orElse(null);
        if (provider == null) return new CheckOutcome.Failed();

        InvoiceStatus status = provider.checkInvoice(inv.externalId());
        switch (status) {
            case PAID -> {
                // Atomically claim the invoice BEFORE activating anything. Only the caller that
                // wins the PENDING -> PAID transition activates the plan and grants rewards, so a
                // manual "I paid" check racing the background PaymentWatcher can never
                // double-activate the subscription or double-reward the referrer.
                if (!invoices.markPaidIfPending(invoiceId)) {
                    RemnawaveUser u = subscriptions.currentSubscription(telegramId).orElse(null);
                    return u != null ? new CheckOutcome.Paid(u, plan) : new CheckOutcome.Failed();
                }
                try {
                    RemnawaveUser user = subscriptions.activatePlan(
                            telegramId, plan.days(), plan.trafficBytes(), plan.code());
                    invoices.recordPayment(telegramId, plan.code(), inv.amountCents(), inv.currency(),
                            plan.days(), inv.provider(), inv.externalId());
                    if (promos != null && inv.promoCode() != null) {
                        promos.redeemOnPaid(inv.promoCode(), telegramId, invoiceId);
                    }
                    referrals.rewardOnActivation(telegramId).ifPresent(refId ->
                            log.info("Referral reward granted to {} for paid activation by {}", refId, telegramId));
                    if (analytics != null) {
                        analytics.track(telegramId, AnalyticsService.Event.PAYMENT_CONFIRMED,
                                Map.of("plan", plan.code(), "provider", inv.provider(), "invoice", invoiceId));
                    }
                    log.info("Invoice {} PAID; plan {} activated for {}", invoiceId, plan.code(), telegramId);
                    return new CheckOutcome.Paid(user, plan);
                } catch (Exception e) {
                    // Activation failed after claiming — revert to PENDING so a later check retries.
                    invoices.markStatus(invoiceId, "PENDING");
                    throw e;
                }
            }
            case PENDING -> {
                return new CheckOutcome.Pending(inv);
            }
            default -> {
                invoices.markStatus(invoiceId, status.name());
                return new CheckOutcome.Failed();
            }
        }
    }

    /**
     * Handles a provider webhook by resolving the external invoice id, then using the
     * normal provider status check. The webhook payload itself is never trusted as proof
     * of payment.
     */
    public WebhookResult handleWebhook(String providerId, String explicitExternalId, String body) throws Exception {
        String externalId = explicitExternalId == null || explicitExternalId.isBlank()
                ? extractExternalId(body)
                : explicitExternalId.trim();
        if (providerId == null || providerId.isBlank() || externalId == null || externalId.isBlank()) {
            return new WebhookResult(false, providerId, externalId, null, new CheckOutcome.Failed());
        }
        Optional<PaymentInvoice> invoice = invoices.findByProviderExternal(providerId, externalId);
        if (invoice.isEmpty()) {
            return new WebhookResult(false, providerId, externalId, null, new CheckOutcome.Failed());
        }
        PaymentInvoice inv = invoice.get();
        CheckOutcome outcome = checkAndActivate(inv.telegramId(), inv.id());
        return new WebhookResult(true, providerId, externalId, inv, outcome);
    }

    private String extractExternalId(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode root = json.readTree(body);
            return findText(root, "transactionId", "invoice_id", "invoiceId", "external_id", "externalId", "id");
        } catch (Exception e) {
            return null;
        }
    }

    private String findText(JsonNode node, String... keys) {
        if (node == null || node.isNull()) return null;
        if (node.isObject()) {
            for (String key : keys) {
                JsonNode value = node.get(key);
                if (value != null && !value.isNull()) {
                    String text = value.asText("");
                    if (!text.isBlank()) return text;
                }
            }
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                String found = findText(entry.getValue(), keys);
                if (found != null && !found.isBlank()) return found;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findText(child, keys);
                if (found != null && !found.isBlank()) return found;
            }
        }
        return null;
    }
}
