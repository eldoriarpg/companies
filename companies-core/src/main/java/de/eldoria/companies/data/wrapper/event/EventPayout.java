/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.event;

import de.eldoria.companies.data.wrapper.stats.PayoutRecord;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The payout record of one worker of a completed order together with the server events that paid its extra.
 *
 * @param record  the payout record, its {@link PayoutRecord#eventExtra()} the sum of {@code byEvent}
 * @param byEvent what each event paid on top, by event id; empty when no event paid
 */
public record EventPayout(PayoutRecord record, Map<Integer, Double> byEvent) {
    /**
     * @param record  the payout record
     * @param byEvent what each event paid on top
     */
    public EventPayout {
        byEvent = Collections.unmodifiableMap(new LinkedHashMap<>(byEvent));
    }
}
