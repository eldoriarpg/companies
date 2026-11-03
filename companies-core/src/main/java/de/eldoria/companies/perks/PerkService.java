/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.perks;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.PerkDefinition;
import de.eldoria.companies.configuration.elements.perks.PerkSettings;
import de.eldoria.companies.data.repository.APerkData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.perk.CompanyPerk;
import de.eldoria.companies.data.wrapper.perk.OwnedPerk;
import de.eldoria.companies.events.company.CompanyPerkChangeEvent;
import de.eldoria.companies.services.TreasuryService;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The perks companies buy: which are registered, what they cost and from which level, which a company owns and
 * whether it may use them. A perk is offered while it is registered and {@code perks.yml} does not switch it off. A
 * company owns a perk once bought; it may use it while the perk is offered and the company has reached its minimum
 * level, and keeps it paused otherwise. Buying needs {@link CompanyPermission#MANAGE_PERKS} and is paid from the
 * treasury; a free perk is stored without a payment. The methods that read the database block; call them off the
 * main thread.
 */
public final class PerkService {
    private final APerkData data;
    private final Consumer<Event> events;
    private final Supplier<LocalDateTime> now;
    private final Map<String, PerkDefinition> perks = new LinkedHashMap<>();
    private volatile Supplier<PerkSettings> settings = PerkSettings::new;
    private volatile @Nullable Payment payment;

    /**
     * The service registers perks at once; it reads {@code perks.yml} once {@link #settings(Supplier)} is given and
     * sells perks once {@link #payment(Payment)} is given, when the treasury is set up.
     *
     * @param data   the perks companies own
     * @param events fires the events of bought perks
     * @param now    the current time in UTC
     */
    public PerkService(APerkData data, Consumer<Event> events, Supplier<LocalDateTime> now) {
        this.data = data;
        this.events = events;
        this.now = now;
    }

    /**
     * @param settings the price, level and switch of each perk
     */
    public void settings(Supplier<PerkSettings> settings) {
        this.settings = settings;
    }

    /**
     * @param payment pays a perk from the treasury of a company
     */
    public void payment(Payment payment) {
        this.payment = payment;
    }

    /**
     * Pays a perk from the treasury of a company, as {@link TreasuryService#spend} does.
     */
    @FunctionalInterface
    public interface Payment {
        /**
         * @param company  the company paying
         * @param actor    the member who buys
         * @param amount   the price
         * @param note     the id of the perk
         * @param purchase stores the perk, completing with false when the company owns it already
         * @return the outcome of the payment
         */
        CompletableFuture<TreasuryService.Result> pay(
                CompanyProfile company,
                UUID actor,
                BigDecimal amount,
                String note,
                Supplier<CompletableFuture<Boolean>> purchase);
    }

    /**
     * Registers a perk, replacing one with the same id.
     *
     * @param perk the perk
     */
    public synchronized void register(PerkDefinition perk) {
        perks.put(perk.id(), perk);
    }

    /**
     * @param id id of a perk, matched ignoring case
     * @return the registered perk
     */
    public synchronized Optional<PerkDefinition> registered(String id) {
        return Optional.ofNullable(perks.get(id.toLowerCase(Locale.ROOT)));
    }

    /**
     * @return the registered perks, in the order they were registered
     */
    public synchronized List<PerkDefinition> registered() {
        return List.copyOf(perks.values());
    }

    /**
     * @param perk a registered perk
     * @return whether it is offered: not switched off in {@code perks.yml}
     */
    public boolean offered(PerkDefinition perk) {
        return settings.get().perk(perk.id()).map(PerkSettings.Perk::enabled).orElse(true);
    }

    /**
     * @param perk a registered perk
     * @return what it costs, rounded to cents
     */
    public BigDecimal price(PerkDefinition perk) {
        var price =
                settings.get().perk(perk.id()).flatMap(PerkSettings.Perk::price).orElse(perk.price());
        return BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * @param perk a registered perk
     * @return the company level from which it can be bought and used
     */
    public int minLevel(PerkDefinition perk) {
        return settings.get()
                .perk(perk.id())
                .flatMap(PerkSettings.Perk::minLevel)
                .orElse(perk.minLevel());
    }

    /**
     * @param company a company
     * @return the perks it owns, oldest first, with whether it may use each now
     */
    public List<CompanyPerk> perks(CompanyProfile company) {
        return data.perks(company.id()).stream()
                .map(owned -> new CompanyPerk(owned.company(), owned.perk(), owned.bought(), usable(company, owned)))
                .toList();
    }

    /**
     * @param company a company
     * @param perk    id of a perk
     * @return whether the company owns the perk and may use it now
     */
    public boolean usable(CompanyProfile company, String perk) {
        var id = perk.toLowerCase(Locale.ROOT);
        return data.perks(company.id()).stream().anyMatch(owned -> owned.perk().equals(id) && usable(company, owned));
    }

    private boolean usable(CompanyProfile company, OwnedPerk owned) {
        return registered(owned.perk())
                .filter(this::offered)
                .filter(perk -> company.level() >= minLevel(perk))
                .isPresent();
    }

    /**
     * What a company sees of a perk.
     *
     * @param perk     the perk
     * @param price    what it costs
     * @param minLevel the level from which it can be bought and used
     * @param state    whether the company owns it, may buy it or not yet
     * @param bought   when the company got it, null unless it owns it
     */
    public record Offer(
            PerkDefinition perk,
            BigDecimal price,
            int minLevel,
            State state,
            @Nullable LocalDateTime bought) {}

    /**
     * Where a company stands with a perk.
     */
    public enum State {
        /**
         * It owns the perk and may use it.
         */
        OWNED,
        /**
         * It owns the perk but may not use it: its level is too low, or the perk is switched off.
         */
        PAUSED,
        /**
         * It may buy the perk.
         */
        AVAILABLE,
        /**
         * Its level is too low to buy the perk.
         */
        LOCKED
    }

    /**
     * @param company a company
     * @return every registered perk that is offered or that the company owns, in the order they were registered
     */
    public List<Offer> offers(CompanyProfile company) {
        Map<String, OwnedPerk> owned = new LinkedHashMap<>();
        for (var perk : data.perks(company.id())) owned.put(perk.perk(), perk);
        List<Offer> offers = new ArrayList<>();
        for (var perk : registered()) {
            var mine = owned.get(perk.id());
            var offered = offered(perk);
            if (mine == null && !offered) continue;
            var level = minLevel(perk);
            var reached = company.level() >= level;
            State state;
            if (mine != null) {
                state = offered && reached ? State.OWNED : State.PAUSED;
            } else {
                state = reached ? State.AVAILABLE : State.LOCKED;
            }
            offers.add(new Offer(perk, price(perk), level, state, mine == null ? null : mine.bought()));
        }
        return offers;
    }

    /**
     * What came of buying a perk.
     *
     * @param outcome  how it ended
     * @param treasury what the treasury answered, null unless it was asked
     */
    public record Bought(Outcome outcome, TreasuryService.@Nullable Result treasury) {
        static Bought of(Outcome outcome) {
            return new Bought(outcome, null);
        }
    }

    /**
     * How buying a perk ended.
     */
    public enum Outcome {
        /**
         * The company owns the perk now.
         */
        DONE,
        /**
         * No perk with this id is offered.
         */
        UNKNOWN,
        /**
         * The member may not buy perks.
         */
        NO_PERMISSION,
        /**
         * The company owns the perk already.
         */
        OWNED,
        /**
         * The company's level is below the perk's minimum level.
         */
        LEVEL,
        /**
         * The treasury did not pay, or the perk could not be stored and the price went back; see
         * {@link Bought#treasury()}.
         */
        TREASURY
    }

    /**
     * Buys a perk for a company, paid from its treasury.
     *
     * @param company the company
     * @param actor   the member who buys
     * @param id      id of the perk, matched ignoring case
     * @return what came of it
     */
    public CompletableFuture<Bought> buy(CompanyProfile company, UUID actor, String id) {
        var perk = registered(id).filter(this::offered);
        if (perk.isEmpty()) return CompletableFuture.completedFuture(Bought.of(Outcome.UNKNOWN));
        var member = company.member(actor);
        if (member.isEmpty() || !member.get().hasPermission(CompanyPermission.MANAGE_PERKS)) {
            return CompletableFuture.completedFuture(Bought.of(Outcome.NO_PERMISSION));
        }
        if (owns(company.id(), perk.get().id())) return CompletableFuture.completedFuture(Bought.of(Outcome.OWNED));
        if (company.level() < minLevel(perk.get())) {
            return CompletableFuture.completedFuture(Bought.of(Outcome.LEVEL));
        }
        var price = price(perk.get());
        var key = perk.get().id();
        if (price.signum() <= 0) {
            if (!data.add(company.id(), key, now.get())) {
                return CompletableFuture.completedFuture(Bought.of(Outcome.OWNED));
            }
            changed(company, key, actor);
            return CompletableFuture.completedFuture(Bought.of(Outcome.DONE));
        }
        var pays = payment;
        if (pays == null) {
            return CompletableFuture.completedFuture(
                    new Bought(Outcome.TREASURY, TreasuryService.Result.of(TreasuryService.Outcome.DISABLED)));
        }
        return pays.pay(
                        company,
                        actor,
                        price,
                        key,
                        () -> CompletableFuture.completedFuture(data.add(company.id(), key, now.get())))
                .thenApply(result -> {
                    if (result.done()) {
                        changed(company, key, actor);
                        return Bought.of(Outcome.DONE);
                    }
                    if (result.outcome() == TreasuryService.Outcome.PURCHASE_FAILED && owns(company.id(), key)) {
                        return Bought.of(Outcome.OWNED);
                    }
                    return new Bought(Outcome.TREASURY, result);
                });
    }

    private boolean owns(int company, String perk) {
        return data.perks(company).stream().anyMatch(owned -> owned.perk().equals(perk));
    }

    private void changed(CompanyProfile company, String perk, UUID actor) {
        events.accept(new CompanyPerkChangeEvent(company, perk, CompanyPerkChangeEvent.Change.BOUGHT, actor));
    }
}
