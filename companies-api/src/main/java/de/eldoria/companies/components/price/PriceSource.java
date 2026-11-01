/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.price;

import java.util.Collection;
import java.util.List;

/**
 * Reads the prices of one shop plugin, so Companies knows what items cost on the server before its own orders do.
 * Companies ships sources for several shop plugins; other plugins register their own through
 * {@link de.eldoria.companies.api.PriceApi#register(org.bukkit.plugin.Plugin, PriceSource)}.
 * <p>
 * A source offers either or both of:
 * <ul>
 *   <li>a snapshot of the prices its shops offer, {@link #quotes()}, which Companies asks for at an interval</li>
 *   <li>the trades as they happen, handed to the {@link PriceRecorder} it gets in {@link #record(PriceRecorder)},
 *       usually from its plugin's transaction event</li>
 * </ul>
 * Prices are in the server's main currency; a source leaves out prices in other currencies. A method may throw when
 * the plugin behind it fails; Companies logs it and goes on without the source for that call.
 */
public interface PriceSource {
    /**
     * The id of the source.
     *
     * @return the stable id of the source: 1 to 64 lower case letters, digits, {@code _}, {@code -} and {@code .}, such
     *     as {@code chestshop}
     */
    String id();

    /**
     * The name of the source.
     *
     * @return the name shown to admins, such as {@code ChestShop}
     */
    String name();

    /**
     * The prices the shops of the plugin offer right now: open player shops, open auctions and the prices of server
     * shops. Called on the main thread, at the interval of {@code sampleMinutes} in {@code prices.yml}. A source that
     * can not tell right now, for example because its plugin is still loading, returns null: its last sample is kept.
     *
     * @return the current offers, empty by default, null to keep the last sample
     */
    default Collection<ShopQuote> quotes() {
        return List.of();
    }

    /**
     * Called once when the source is registered, with the recorder that takes its trades.
     *
     * @param recorder takes the trades of this source
     */
    default void record(PriceRecorder recorder) {}
}
