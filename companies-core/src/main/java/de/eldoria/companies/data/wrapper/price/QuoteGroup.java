/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.price;

import de.eldoria.companies.components.price.ShopOrigin;
import de.eldoria.companies.components.price.ShopSide;

import java.time.LocalDateTime;

/**
 * What the offers of one material, side and origin of a source on one server came to when they were last sampled.
 *
 * @param source     id of the source, lower case
 * @param material   name of the material, upper case
 * @param side       whether the players buy or sell at these prices
 * @param origin     who set the prices
 * @param quotes     number of offers
 * @param items      number of items offered
 * @param medianUnit median price of one item over the offers
 * @param minUnit    lowest price of one item
 * @param maxUnit    highest price of one item
 * @param sampledAt  when the offers were sampled, in UTC
 */
public record QuoteGroup(
        String source,
        String material,
        ShopSide side,
        ShopOrigin origin,
        int quotes,
        long items,
        double medianUnit,
        double minUnit,
        double maxUnit,
        LocalDateTime sampledAt) {}
