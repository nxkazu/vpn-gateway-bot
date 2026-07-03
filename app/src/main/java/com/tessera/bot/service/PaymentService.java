package com.tessera.bot.service;

import com.tessera.bot.config.AppConfig;
import com.tessera.bot.db.InvoiceRepository;
import com.tessera.bot.db.model.PaymentInvoice;
import com.tessera.core.api.CreatedInvoice;
import com.tessera.core.api.InvoiceStatus;
import com.tessera.core.api.PaymentProvider;
import com.tessera.core.api.PaymentRequest;
import com.tessera.remnawave.dto.RemnawaveUser;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of {@link PaymentProvider}s (contributed by payment modules) plus the
 * invoice lifecycle: create -> user pays -> check -> activate plan + reward referral.
 * If no providers are registered the bot falls back to direct (free) activation,
 * preserving pre-payments behaviour.
 */
@Slf4j
public class PaymentService {

    public record CreateResult(long invoiceId, String payUrl, Plans.Plan plan) {}

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

    public PaymentService(AppConfig config, InvoiceRepository invoices,
                          SubscriptionService subscriptions, ReferralService referrals) {
        this.config = config;
        this.invoices = invoices;
        this.subscriptions = subscriptions;
        this.referrals = referrals;
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

    /** Creates a provider invoice and persists it locally as PENDING. */
    public CreateResult createInvoice(long telegramId, String providerId, Plans.Plan plan) throws Exception {
        PaymentProvider provider = byId(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment provider: " + providerId));
        long amountMinor = plan.priceRub() * 100L;
        PaymentRequest request = new PaymentRequest(
                telegramId, plan.code(), plan.title(), amountMinor, "RUB",
                "Tessera VPN — " + plan.title(),
                "https://t.me/" + config.botUsername());
        CreatedInvoice created = provider.createInvoice(request);
        long id = invoices.insert(telegramId, plan.code(), provider.id(),
                created.externalId(), created.payUrl(), amountMinor, "RUB");
        log.info("Invoice {} created: user={} plan={} provider={} ext={}",
                id, telegramId, plan.code(), provider.id(), created.externalId());
        return new CreateResult(id, created.payUrl(), plan);
    }

    /**
     * Re-checks an invoice with its provider. On first confirmation of payment the plan
     * is activated, the invoice marked PAID, a payments ledger row written, and the
     * referral reward triggered. Idempotent for already-PAID invoices.
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
                RemnawaveUser user = subscriptions.activatePlan(
                        telegramId, plan.days(), plan.trafficBytes(), plan.code());
                invoices.markStatus(invoiceId, "PAID");
                invoices.recordPayment(telegramId, plan.code(), inv.amountCents(), inv.currency(),
                        plan.days(), inv.provider(), inv.externalId());
                referrals.rewardOnActivation(telegramId).ifPresent(refId ->
                        log.info("Referral reward granted to {} for paid activation by {}", refId, telegramId));
                log.info("Invoice {} PAID; plan {} activated for {}", invoiceId, plan.code(), telegramId);
                return new CheckOutcome.Paid(user, plan);
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
}