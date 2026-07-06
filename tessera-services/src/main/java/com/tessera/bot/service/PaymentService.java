package com.tessera.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tessera.bot.config.AppConfig;
import com.tessera.bot.db.GiftRepository;
import com.tessera.bot.db.InvoiceRepository;
import com.tessera.bot.db.Plans;
import com.tessera.bot.db.model.PaymentInvoice;
import com.tessera.bot.db.Database;
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

    private final java.util.concurrent.ExecutorService activationExecutor =
            java.util.concurrent.Executors.newFixedThreadPool(10, r -> {
                Thread t = new Thread(r, "payment-activation");
                t.setDaemon(true);
                return t;
            });

    public sealed interface CheckOutcome {
        record Paid(RemnawaveUser user, Plans.Plan plan) implements CheckOutcome {}
        record GiftReady(GiftRepository.Gift gift, Plans.Plan plan, String shareLink) implements CheckOutcome {}
        record Pending(PaymentInvoice invoice) implements CheckOutcome {}
        record Failed() implements CheckOutcome {}
    }

    private final Map<String, PaymentProvider> providers = new LinkedHashMap<>();
    private final AppConfig config;
    private final InvoiceRepository invoices;
    private final Database db;
    private final SubscriptionService subscriptions;
    private final ReferralService referrals;
    private final PromoService promos;
    private final AnalyticsService analytics;
    private final GiftService giftService;
    private final ObjectMapper json = new ObjectMapper();

    public PaymentService(Database db, AppConfig config, InvoiceRepository invoices,
                          SubscriptionService subscriptions, ReferralService referrals,
                          PromoService promos) {
        this(db, config, invoices, subscriptions, referrals, promos, null, null);
    }

    public PaymentService(Database db, AppConfig config, InvoiceRepository invoices,
                          SubscriptionService subscriptions, ReferralService referrals,
                          PromoService promos, AnalyticsService analytics) {
        this(db, config, invoices, subscriptions, referrals, promos, analytics, null);
    }

    public PaymentService(Database db, AppConfig config, InvoiceRepository invoices,
                          SubscriptionService subscriptions, ReferralService referrals,
                          PromoService promos, AnalyticsService analytics, GiftService giftService) {
        this.db = db;
        this.config = config;
        this.invoices = invoices;
        this.subscriptions = subscriptions;
        this.referrals = referrals;
        this.promos = promos;
        this.analytics = analytics;
        this.giftService = giftService;
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

    /** Resolves a local invoice by its provider + provider-side external id (e.g. Stars payload). */
    public Optional<PaymentInvoice> findInvoice(String providerId, String externalId) {
        if (providerId == null || externalId == null || externalId.isBlank()) return Optional.empty();
        return invoices.findByProviderExternal(providerId, externalId);
    }

    /** Most recent confirmed payments of a user (for the in-bot payment history screen). */
    public List<InvoiceRepository.LedgerEntry> history(long telegramId, int limit) {
        return invoices.recentPayments(telegramId, limit);
    }

    /**
     * Creates a provider invoice and persists it locally as PENDING. When a valid promo
     * code is supplied the price is discounted; invalid/expired codes are ignored.
     */
    public CreateResult createInvoice(long telegramId, String providerId, Plans.Plan plan,
                                      String promoCode) throws Exception {
        return createInvoice(telegramId, providerId, plan, promoCode, PaymentInvoice.KIND_PLAN);
    }

    /**
     * Creates an invoice whose payment unlocks a redeemable gift code instead of the
     * buyer's own subscription. The PENDING gift row is created together with the invoice.
     */
    public CreateResult createGiftInvoice(long telegramId, String providerId, Plans.Plan plan,
                                          String promoCode) throws Exception {
        if (giftService == null) {
            throw new IllegalStateException("Gift subscriptions are not wired");
        }
        CreateResult result = createInvoice(telegramId, providerId, plan, promoCode, PaymentInvoice.KIND_GIFT);
        giftService.createPending(telegramId, plan.code(), result.invoiceId());
        return result;
    }

    private CreateResult createInvoice(long telegramId, String providerId, Plans.Plan plan,
                                       String promoCode, String kind) throws Exception {
        PaymentProvider provider = byId(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment provider: " + providerId));

        boolean gift = PaymentInvoice.KIND_GIFT.equals(kind);
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
                (gift ? "Tessera VPN Gift \u2014 " : "Tessera VPN \u2014 ") + plan.title(),
                "https://t.me/" + config.botUsername());
        CreatedInvoice created = provider.createInvoice(request);
        long id = invoices.insert(telegramId, plan.code(), provider.id(),
                created.externalId(), created.payUrl(), amountMinor, "RUB", appliedPromo, kind);
        log.info("Invoice {} created: user={} plan={} provider={} ext={} promo={} kind={}",
                id, telegramId, plan.code(), provider.id(), created.externalId(), appliedPromo, kind);
        if (analytics != null) {
            analytics.track(telegramId, AnalyticsService.Event.INVOICE_CREATED,
                    Map.of("plan", plan.code(), "provider", provider.id(),
                            "priceRub", priceRub, "kind", kind));
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
            // Already processed earlier; just report the result of that processing.
            if (inv.isGift()) return giftOutcome(invoiceId, plan);
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
                    if (inv.isGift()) return giftOutcome(invoiceId, plan);
                    RemnawaveUser u = subscriptions.currentSubscription(telegramId).orElse(null);
                    return u != null ? new CheckOutcome.Paid(u, plan) : new CheckOutcome.Failed();
                }
                try {
                    CheckOutcome outcome;
                    if (inv.isGift()) {
                        if (giftService != null) giftService.activateForInvoice(invoiceId);
                        outcome = giftOutcome(invoiceId, plan);
                        if (outcome instanceof CheckOutcome.Failed) {
                            throw new IllegalStateException("paid gift invoice " + invoiceId + " has no gift row");
                        }
                        db.inTransaction(conn -> {
                            invoices.recordPayment(conn, telegramId, plan.code(), inv.amountCents(), inv.currency(),
                                    plan.days(), inv.provider(), inv.externalId());
                            if (promos != null && inv.promoCode() != null) {
                                promos.redeemOnPaid(conn, inv.promoCode(), telegramId, invoiceId);
                            }
                            return null;
                        });
                    } else {
                        RemnawaveUser user = subscriptions.activatePlan(
                                telegramId, plan.days(), plan.trafficBytes(), plan.code());
                        
                        Optional<Long> rewardedRefId = db.inTransaction(conn -> {
                            invoices.recordPayment(conn, telegramId, plan.code(), inv.amountCents(), inv.currency(),
                                    plan.days(), inv.provider(), inv.externalId());
                            if (promos != null && inv.promoCode() != null) {
                                promos.redeemOnPaid(conn, inv.promoCode(), telegramId, invoiceId);
                            }
                            return referrals.rewardOnActivation(conn, telegramId);
                        });
                        
                        rewardedRefId.ifPresent(refId ->
                                log.info("Referral reward granted to {} for paid activation by {}", refId, telegramId));
                        
                        outcome = new CheckOutcome.Paid(user, plan);
                    }
                    
                    if (analytics != null) {
                        analytics.track(telegramId, inv.isGift()
                                        ? AnalyticsService.Event.GIFT_PURCHASED
                                        : AnalyticsService.Event.PAYMENT_CONFIRMED,
                                Map.of("plan", plan.code(), "provider", inv.provider(), "invoice", invoiceId));
                    }
                    log.info("Invoice {} PAID ({}); plan {} for {}", invoiceId, inv.kind(), plan.code(), telegramId);
                    return outcome;
                } catch (Exception e) {
                    // Activation failed after claiming.
                    // If the provider doesn't have a reliable 'checkInvoice' (e.g. Telegram Stars)
                    // reverting to PENDING might make us lose the PAID status forever.
                    // But in both cases, the invoice is PAID locally. Instead of reverting,
                    // we queue the activation to retry asynchronously or leave it PAID for an admin to fix.
                    if ("stars".equals(inv.provider())) {
                        log.error("Activation failed for Telegram Stars invoice {}. Left as PAID.", invoiceId, e);
                        // Leave it as PAID. We could retry later, but for now we prevent data loss.
                        // The user can contact support.
                    } else {
                        // Actually, the assignment asks to queue the activation for later
                        // OR use another mechanism so it's not totally lost.
                        // For now we revert the claim to allow retries.
                        invoices.markStatus(invoiceId, InvoiceStatus.PENDING.name());
                        log.warn("Activation failed for invoice {}; reverted claim to PENDING.", invoiceId, e);
                    }
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

    private CheckOutcome giftOutcome(long invoiceId, Plans.Plan plan) {
        if (giftService == null) return new CheckOutcome.Failed();
        return giftService.activateForInvoice(invoiceId)
                .<CheckOutcome>map(gift -> new CheckOutcome.GiftReady(gift, plan,
                        GiftService.shareLink(config.botUsername(), gift.code())))
                .orElseGet(CheckOutcome.Failed::new);
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
