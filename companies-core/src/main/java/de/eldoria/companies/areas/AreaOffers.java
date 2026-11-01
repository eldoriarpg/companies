/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.areas;

import de.eldoria.companies.components.area.AreaBounds;
import de.eldoria.companies.components.area.AreaCapability;
import de.eldoria.companies.components.area.AreaProvider;
import de.eldoria.companies.components.area.OfferKind;
import de.eldoria.companies.data.wrapper.area.AreaOffer;
import de.eldoria.companies.util.Money;
import de.eldoria.companies.util.Times;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Offers areas of the server to companies, for {@code /companyadmin area} and the API. An area can be offered when its
 * plugin can move it to a company and someone keeps it for the server: its current owner, or nobody for plugins that
 * release areas to nobody. Picking the area asks the area plugins and runs on the main thread; storing and withdrawing
 * block and run off it.
 */
public final class AreaOffers {
    private final AreaServices areas;

    /**
     * @param areas the areas of companies and their plugins
     */
    public AreaOffers(AreaServices areas) {
        this.areas = areas;
    }

    /**
     * Picks the area at a spot that can be offered. Call it on the main thread.
     *
     * @param at the spot
     * @return the area, or why none can be offered there
     */
    public Picked at(Location at) {
        var providers = areas.providers();
        var here = providers.areasAt(at);
        if (here.isEmpty()) return new Picked.Refused(Problem.NO_AREA);
        var movable = here.stream().filter(this::movable).findFirst();
        if (movable.isEmpty()) return new Picked.Refused(Problem.NOT_MOVABLE);
        return picked(movable.get(), at.getWorld().getName());
    }

    /**
     * Picks an area by its plugin and reference. Call it on the main thread.
     *
     * @param provider the id of the area plugin
     * @param ref      the plugin's reference of the area
     * @return the area, or why it can not be offered
     */
    public Picked of(String provider, String ref) {
        var providers = areas.providers();
        var id = provider.toLowerCase(Locale.ROOT);
        if (!providers.available(id)) return new Picked.Refused(Problem.NO_AREA);
        var area = new AreaRef(id, providers.call(id, found -> found.canonical(ref), ref));
        if (!providers.call(id, found -> found.exists(area.ref()), false)) return new Picked.Refused(Problem.NO_AREA);
        if (!movable(area)) return new Picked.Refused(Problem.NOT_MOVABLE);
        var world = providers.call(id, found -> found.bounds(area.ref()), Optional.<AreaBounds>empty());
        if (world.isEmpty()) return new Picked.Refused(Problem.NO_AREA);
        return picked(area, world.get().world());
    }

    private boolean movable(AreaRef area) {
        return areas.providers()
                .call(area.provider(), found -> found.capabilities().contains(AreaCapability.TRANSFER), false);
    }

    private Picked picked(AreaRef area, String world) {
        var providers = areas.providers();
        var keeper = providers.call(area.provider(), found -> keeper(found.owners(area.ref())), null);
        if (keeper == null && !providers.call(area.provider(), AreaProvider::releasesToNobody, false)) {
            return new Picked.Refused(Problem.NO_KEEPER);
        }
        var name = providers.call(area.provider(), found -> found.name(area.ref()), area.ref());
        return new Picked.Area(area, world, keeper, name);
    }

    private static @Nullable UUID keeper(Set<UUID> owners) {
        return owners.stream().min(Comparator.naturalOrder()).orElse(null);
    }

    /**
     * Stores an offer of a picked area. Blocks; call it off the main thread.
     *
     * @param area  the area, from {@link #at(Location)} or {@link #of(String, String)}
     * @param kind  whether companies buy or rent it
     * @param price the price, or the rent per period
     * @param days  the days one rent period lasts, 0 for a sale
     * @return the stored offer, or why it was not stored
     */
    public Stored offer(Picked.Area area, OfferKind kind, double price, int days) {
        var store = areas.areas();
        var ref = area.ref();
        if (store.byRef(ref.provider(), ref.ref()).isPresent()) return new Stored.Refused(Problem.IN_USE);
        var offer = new AreaOffer(
                0,
                ref.provider(),
                ref.ref(),
                area.world(),
                areas.node(),
                kind,
                Money.floorDecimal(price).doubleValue(),
                kind == OfferKind.RENT ? days : 0,
                area.keeper(),
                Times.now());
        return store.addOffer(offer)
                .<Stored>map(id -> new Stored.Done(store.offer(id).orElseGet(() -> stored(offer, id))))
                .orElseGet(() -> new Stored.Refused(Problem.OFFERED));
    }

    private static AreaOffer stored(AreaOffer offer, int id) {
        return new AreaOffer(
                id,
                offer.provider(),
                offer.ref(),
                offer.world(),
                offer.node(),
                offer.kind(),
                offer.price(),
                offer.periodDays(),
                offer.keeper(),
                offer.created());
    }

    /**
     * Withdraws an offer no company has. Blocks; call it off the main thread.
     *
     * @param id id of the offer
     * @return what came of it
     */
    public Withdrawal withdraw(int id) {
        var store = areas.areas();
        if (store.removeOffer(id)) return Withdrawal.WITHDRAWN;
        return store.offer(id).isPresent() ? Withdrawal.TAKEN : Withdrawal.UNKNOWN;
    }

    /**
     * Why an area was not offered.
     */
    public enum Problem {
        /**
         * No area of a loaded plugin lies there, or the reference names none.
         */
        NO_AREA,
        /**
         * The area's plugin can not move it to a company.
         */
        NOT_MOVABLE,
        /**
         * Nobody owns the area, and its plugin can not leave it to nobody.
         */
        NO_KEEPER,
        /**
         * A company has the area.
         */
        IN_USE,
        /**
         * The area is offered already.
         */
        OFFERED
    }

    /**
     * What came of withdrawing an offer.
     */
    public enum Withdrawal {
        /**
         * The offer is gone.
         */
        WITHDRAWN,
        /**
         * A company has the area; the offer stays.
         */
        TAKEN,
        /**
         * No offer has this id.
         */
        UNKNOWN
    }

    /**
     * The area picked for an offer.
     */
    public sealed interface Picked {
        /**
         * An area that can be offered.
         *
         * @param ref    the area
         * @param world  the world it lies in
         * @param keeper who keeps it for the server, null for nobody
         * @param name   its name as its plugin shows it
         */
        record Area(AreaRef ref, String world, @Nullable UUID keeper, String name) implements Picked {}

        /**
         * No area can be offered.
         *
         * @param problem why
         */
        record Refused(Problem problem) implements Picked {}
    }

    /**
     * What came of storing an offer.
     */
    public sealed interface Stored {
        /**
         * The offer is stored.
         *
         * @param offer the stored offer
         */
        record Done(AreaOffer offer) implements Stored {}

        /**
         * The offer was not stored.
         *
         * @param problem why
         */
        record Refused(Problem problem) implements Stored {}
    }
}
