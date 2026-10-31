/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import java.util.Optional;

/**
 * What the placeholders show about a company. The statistics are all time.
 *
 * @param id           id of the company
 * @param name         name of the company
 * @param level        current level
 * @param levelName    name of the current level
 * @param members      number of members
 * @param activeOrders orders the company works on
 * @param owner        name of the owner, empty when unknown
 * @param tag          tag, or empty without one
 * @param color        stored profile colour, or empty without one
 * @param orders       completed orders, failed ones subtracted
 * @param earned       money earned with completed orders, before tax
 * @param delivered    items delivered to completed orders
 * @param treasury     balance of the treasury
 * @param payoutShare  share of each payout kept in the treasury, limited by the current cap, in percent
 * @param areas        areas the company uses
 * @param homeServer   proxy name of the server of the company's home, empty without a home or a name
 */
public record CompanyView(
        int id,
        String name,
        int level,
        String levelName,
        int members,
        int activeOrders,
        String owner,
        Optional<String> tag,
        Optional<String> color,
        int orders,
        double earned,
        int delivered,
        double treasury,
        int payoutShare,
        int areas,
        String homeServer) {}
