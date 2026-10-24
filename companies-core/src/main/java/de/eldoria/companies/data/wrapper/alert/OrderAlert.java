/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.alert;

import de.eldoria.companies.orders.query.OrderFilter;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * A saved order search of a company that notifies its members about new matching orders.
 *
 * @param id           id of the alert
 * @param company      id of the company
 * @param name         name of the alert, unique within the company
 * @param filter       orders the alert matches
 * @param creator      member who saved the alert
 * @param enabled      whether the alert notifies
 * @param created      when the alert was saved
 * @param lastNotified when the alert last notified, or empty when it never did
 * @param pending      matches held back by the rate limit since the last notification
 */
public record OrderAlert(
        int id,
        int company,
        String name,
        OrderFilter filter,
        UUID creator,
        boolean enabled,
        LocalDateTime created,
        Optional<LocalDateTime> lastNotified,
        int pending) {}
