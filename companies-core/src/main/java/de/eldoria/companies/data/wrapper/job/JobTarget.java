/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.job;

import org.jetbrains.annotations.Nullable;

/**
 * The shape a terraform job asks for: every column of the area is solid up to a height and empty above it.
 *
 * @param y       the height of the top solid block of every column
 * @param surface the block the top layer must be made of, as a material name, null for any solid block
 */
public record JobTarget(int y, @Nullable String surface) {}
