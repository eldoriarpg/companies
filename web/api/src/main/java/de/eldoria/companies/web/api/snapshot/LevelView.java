/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

import org.jspecify.annotations.Nullable;

/**
 * A company's level.
 *
 * @param level the level number
 * @param name its name as plain text
 * @param progress the progress to the next level from 0 to 1, none at the highest level
 */
public record LevelView(int level, String name, @Nullable Double progress) {}
