/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Why and how the server placed one of its orders, kept after the order ended.
 *
 * @param order     id of the order
 * @param rule      id of the rule that placed it
 * @param kind      how it was decided, such as {@code DEFINED}
 * @param decidedAt when it was placed
 * @param account   economy account that paid it, null when the money is minted
 * @param paid      what was put into the order
 * @param record    the numbers the decision stood on, as JSON
 */
public record SystemDecision(
        int order,
        String rule,
        String kind,
        LocalDateTime decidedAt,
        @Nullable String account,
        double paid,
        String record) {
    /**
     * @return the economy account that paid the order, empty when the money is minted
     */
    public Optional<String> paidFrom() {
        return Optional.ofNullable(account);
    }
}
