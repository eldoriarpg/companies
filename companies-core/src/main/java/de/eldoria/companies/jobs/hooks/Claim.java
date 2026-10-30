/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs.hooks;

import de.eldoria.companies.data.wrapper.job.JobLand;
import org.jetbrains.annotations.Nullable;

/**
 * What a land plugin says about the area of a new job for its owner.
 */
public sealed interface Claim {
    /**
     * The plugin has no land in the area.
     */
    record None() implements Claim {}

    /**
     * The whole area lies in land of the owner that the owner may change.
     *
     * @param land what kind of land it is
     * @param ref  the plot or region, as the plugin names it
     */
    record Owned(JobLand land, String ref) implements Claim {}

    /**
     * The area touches land the owner may not change.
     *
     * @param ref the plot or region, as the plugin names it, null when it has no name
     */
    record Foreign(@Nullable String ref) implements Claim {}
}
