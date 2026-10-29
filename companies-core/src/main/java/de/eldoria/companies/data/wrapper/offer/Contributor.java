/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.offer;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A company or a player working alone that delivered chunks of a goal.
 *
 * @param company    id of the company, 0 for a player working alone
 * @param name       name of the company, null when it no longer exists or for a player working alone
 * @param soloWorker the player working alone, null for a company
 * @param delivered  items delivered
 * @param chunks     chunks delivered
 */
public record Contributor(
        int company, @Nullable String name, @Nullable UUID soloWorker, int delivered, int chunks) {}
