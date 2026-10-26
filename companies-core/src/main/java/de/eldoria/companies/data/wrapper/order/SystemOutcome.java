/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;

/**
 * How a system order ended, with the decision that placed it.
 *
 * @param decision the decision
 * @param outcome  how it ended, such as {@code TAKEN}, null while it has not ended or ended unnoticed
 * @param endedAt  when it ended, null with no outcome
 */
public record SystemOutcome(
        SystemDecision decision,
        @Nullable String outcome,
        @Nullable LocalDateTime endedAt) {}
