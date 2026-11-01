/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.price;

/**
 * Takes the trades of a {@link PriceSource} as they happen. It may be called from any thread and returns at once;
 * the trades are stored later. Trades of a source that is not registered or is switched off are dropped.
 */
public interface PriceRecorder {
    /**
     * Records a trade. A trade of an item that is not plain, without items or without a price is dropped, as is one
     * where the player traded with their own shop: a source that can tell leaves those out.
     *
     * @param trade the trade
     */
    void trade(ShopTrade trade);
}
