/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.order.ItemCriteria;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.offer.ContestDelivery;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferKind;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.orders.Collector;
import de.eldoria.companies.orders.DeliveryPart;
import de.eldoria.companies.orders.Payout;
import de.eldoria.companies.orders.criteria.CriteriaSyntax;
import de.eldoria.companies.services.TreasuryService;
import de.eldoria.companies.util.Money;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lets companies take part in contests and pays what they deliver into them. Runs on every node.
 * <p>
 * A company joins a contest that has not closed with the {@code manage_orders} permission, like accepting an order.
 * Joining opens when the contest starts. While the contest runs or is paused it uses one of the company's order
 * slots, until the company leaves or the contest closes: the slots its level allows count the orders it works on and
 * these contests together. Only companies take part; players working alone do not.
 * <p>
 * A delivery takes what the contest misses and its money, less the prizes kept back, can pay, see
 * {@link AOfferData#deliver}. The delivering worker is paid at once at the contest's price, like a payout of an order;
 * each delivery is paid the rounded price of all items delivered so far less that of those before, so the deliveries
 * add up to exactly what the whole amount costs: the tax of the company's level goes to the collector, the company's treasury share to its treasury, the
 * rest to the worker. The money comes from what the contest's account paid in when it started, or is minted.
 * Deliveries into contests do not count for company levels, rankings or member statistics, which are built from
 * orders.
 */
public final class ContestService {
    private final AOfferData offers;
    private final AOrderData orders;
    private final Payments payments;
    private final ToIntFunction<CompanyProfile> maxOrders;
    private final Clock clock;
    private final Logger logger;

    /**
     * @param offers    offer repository
     * @param orders    order repository, counting the order slots of companies
     * @param payments  pays the workers, the collector and the treasuries
     * @param maxOrders the order slots the level of a company allows
     * @param clock     clock giving the current time in UTC
     * @param logger    reports payments that failed
     */
    public ContestService(
            AOfferData offers,
            AOrderData orders,
            Payments payments,
            ToIntFunction<CompanyProfile> maxOrders,
            Clock clock,
            Logger logger) {
        this.offers = offers;
        this.orders = orders;
        this.payments = payments;
        this.maxOrders = maxOrders;
        this.clock = clock;
        this.logger = logger;
    }

    /**
     * The service of a running plugin: workers are paid through Vault on the main thread, taxes go to the collector,
     * treasury shares to the company treasuries, and a company's level decides its tax and its order slots.
     *
     * @param plugin        the plugin
     * @param offers        offer repository
     * @param orders        order repository
     * @param economy       economy paying the workers
     * @param configuration configuration of the plugin
     * @param treasuries    the company treasuries
     * @return the service
     */
    public static ContestService create(
            Plugin plugin,
            AOfferData offers,
            AOrderData orders,
            Economy economy,
            Configuration configuration,
            TreasuryService treasuries) {
        var collector = new Collector(plugin, economy, configuration);
        var payments = new Payments() {
            @Override
            public double taxPercent(CompanyProfile company) {
                return configuration.companySettings().taxPercent(company.level());
            }

            @Override
            public int share(CompanyProfile company) {
                return treasuries.effectiveShare(company.id()).join();
            }

            @Override
            public void pay(UUID worker, double amount) {
                if (!(amount > 0)) return;
                var player = plugin.getServer().getOfflinePlayer(worker);
                Threading.mainThread(plugin).execute(() -> economy.depositPlayer(player, amount));
            }

            @Override
            public void collect(double amount) {
                collector.collect(amount);
            }

            @Override
            public void keep(CompanyProfile company, int offer, long cents) {
                treasuries.keepContestShare(company, offer, cents);
            }
        };
        return new ContestService(
                offers,
                orders,
                payments,
                company -> configuration
                        .companySettings()
                        .levelSettings(company.level())
                        .maxOrders(),
                Times.UTC,
                plugin.getLogger());
    }

    /**
     * Pays the prizes of contests into the company treasuries, or to the owners while treasuries are off.
     *
     * @param companies  company repository
     * @param treasuries the company treasuries
     * @return the prizes
     */
    public static EventOfferService.Prizes prizes(ACompanyData companies, TreasuryService treasuries) {
        return (company, offer, amount) -> companies
                .retrieveCompanyById(company)
                .asFuture()
                .join()
                .flatMap(found ->
                        companies.retrieveCompanyProfile(found).asFuture().join())
                .map(profile -> treasuries
                        .prize(profile, offer, Money.toCents(BigDecimal.valueOf(amount)))
                        .join())
                .orElse(false);
    }

    /**
     * Lets the company of a member take part in a contest. Blocks; call it off the main thread.
     *
     * @param company the member's company
     * @param member  the member joining for it
     * @param offer   id of the contest
     * @return what came of it
     */
    public Joining join(CompanyProfile company, UUID member, int offer) {
        if (!mayManage(company, member)) return Joining.NO_PERMISSION;
        var contest = contest(offer);
        if (contest == null) return Joining.UNKNOWN;
        if (contest.state().closed()) return Joining.CLOSED;
        var entry = offers.entry(offer, company.id());
        if (entry.isPresent() && entry.get().active()) return Joining.ALREADY;
        if (contest.started() == null) return Joining.NOT_STARTED;
        var used = orders.retrieveCompanySlotCount(company).join();
        if (used != null && used >= maxOrders.applyAsInt(company)) return Joining.LIMIT;
        return offers.join(offer, company.id(), now()) ? Joining.JOINED : Joining.ALREADY;
    }

    /**
     * Lets the company of a member leave a contest; it keeps its place. Blocks; call it off the main thread.
     *
     * @param company the member's company
     * @param member  the member leaving for it
     * @param offer   id of the contest
     * @return what came of it
     */
    public Joining leave(CompanyProfile company, UUID member, int offer) {
        if (!mayManage(company, member)) return Joining.NO_PERMISSION;
        if (contest(offer) == null) return Joining.UNKNOWN;
        return offers.leave(offer, company.id(), now()) ? Joining.LEFT : Joining.NOT_JOINED;
    }

    /**
     * Takes items a worker offers into a contest and pays the worker for what was taken. Blocks; call it off the main
     * thread. Once the contest took the items this never fails: a payment that failed is logged and the delivery
     * stays, so only a failure before that hands the items back.
     *
     * @param company the worker's company
     * @param worker  the delivering worker
     * @param offer   id of the contest
     * @param items   items offered
     * @return what was taken and paid
     */
    public Paid deliver(CompanyProfile company, UUID worker, int offer, int items) {
        if (items <= 0) return new Paid(ContestDelivery.refused(ContestDelivery.Outcome.FULL, OfferState.ACTIVE), null);
        var delivery = offers.deliver(offer, company.id(), worker, items, now());
        if (delivery.outcome() != ContestDelivery.Outcome.DELIVERED) return new Paid(delivery, null);
        Payout payout = null;
        try {
            payout = Payout.split(delivery.gross(), payments.taxPercent(company), payments.share(company));
            payments.pay(worker, payout.net());
            payments.collect(payout.tax());
            if (payout.shareCents() > 0) payments.keep(company, offer, payout.shareCents());
        } catch (RuntimeException e) {
            logger.log(
                    Level.SEVERE,
                    "Could not pay " + delivery.gross() + " for the delivery of " + worker + " into contest " + offer
                            + "; the items were taken.",
                    e);
        }
        return new Paid(delivery, payout);
    }

    /**
     * What a contest takes, as a delivery looks for it.
     *
     * @param offer the contest
     * @return the item a delivery matches and the criteria of a criteria line, empty when the line names nothing
     */
    public static Optional<Wanted> wanted(EventOffer offer) {
        var line = offer.line();
        var item = line.item();
        if (item != null) return Optional.of(new Wanted(item, null));
        var criteria = line.criteria();
        if (criteria != null) {
            try {
                var parsed = CriteriaSyntax.parse(criteria);
                return Optional.of(new Wanted(parsed.template(), parsed));
            } catch (CommandFailure e) {
                return Optional.empty();
            }
        }
        var name = line.material();
        var material = name == null ? null : Material.matchMaterial(name);
        if (material == null || !material.isItem() || material.isAir()) return Optional.empty();
        return Optional.of(new Wanted(new ItemStack(material), null));
    }

    /**
     * The items of every part a contest did not take, the last parts first.
     *
     * @param parts the items taken from the worker
     * @param taken how many of them the contest took
     * @return the parts with items left, with the amount left
     */
    public static List<DeliveryPart> surplus(List<DeliveryPart> parts, int taken) {
        List<DeliveryPart> left = new ArrayList<>();
        var keep = taken;
        for (var part : parts) {
            var used = Math.min(keep, part.amount());
            keep -= used;
            if (part.amount() > used) left.add(new DeliveryPart(part.line(), part.stack(), part.amount() - used));
        }
        return left;
    }

    private boolean mayManage(CompanyProfile company, UUID member) {
        return company.member(member)
                .map(found -> found.hasPermission(CompanyPermission.MANAGE_ORDERS))
                .orElse(false);
    }

    private @Nullable EventOffer contest(int offer) {
        return offers.offer(offer)
                .filter(found -> found.kind() == OfferKind.CONTEST)
                .orElse(null);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /**
     * Where the money of deliveries into contests goes.
     */
    public interface Payments {
        /**
         * @param company the delivering company
         * @return the tax of its level in percent
         */
        double taxPercent(CompanyProfile company);

        /**
         * @param company the delivering company
         * @return the share of each payout it keeps in its treasury, in percent
         */
        int share(CompanyProfile company);

        /**
         * Pays a worker.
         *
         * @param worker the worker
         * @param amount what the worker gets after tax and share
         */
        void pay(UUID worker, double amount);

        /**
         * Hands the tax to the collector.
         *
         * @param amount the tax
         */
        void collect(double amount);

        /**
         * Credits the treasury share to the company.
         *
         * @param company the company
         * @param offer   id of the contest
         * @param cents   the share in cents
         */
        void keep(CompanyProfile company, int offer, long cents);
    }

    /**
     * What a contest takes.
     *
     * @param template the item a delivery matches
     * @param criteria the criteria of a criteria line, null otherwise
     */
    public record Wanted(ItemStack template, @Nullable ItemCriteria criteria) {}

    /**
     * What came of a delivery and how it was paid.
     *
     * @param delivery what was taken
     * @param payout   how its money was split, null when nothing was taken
     */
    public record Paid(ContestDelivery delivery, @Nullable Payout payout) {}

    /**
     * What came of joining or leaving a contest.
     */
    public enum Joining {
        /**
         * The company takes part now.
         */
        JOINED,
        /**
         * The company left.
         */
        LEFT,
        /**
         * The company takes part already.
         */
        ALREADY,
        /**
         * The company does not take part.
         */
        NOT_JOINED,
        /**
         * The member may not manage the company's orders.
         */
        NO_PERMISSION,
        /**
         * There is no such contest.
         */
        UNKNOWN,
        /**
         * The contest closed.
         */
        CLOSED,
        /**
         * The company uses all order slots its level allows.
         */
        LIMIT,
        /**
         * The contest has not started; joining opens at its start.
         */
        NOT_STARTED
    }
}
