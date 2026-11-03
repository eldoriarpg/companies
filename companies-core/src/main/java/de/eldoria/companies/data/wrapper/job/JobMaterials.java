/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.job;

import org.jetbrains.annotations.Nullable;

/**
 * Where the materials of a dig, terraform or build job go and come from, as the owner chose when posting.
 *
 * @param dugToStock whether the blocks the company digs go into the job's stock for the owner instead of to the company
 * @param fill       the block the owner supplies to fill a terraform job below its surface, null when the company
 *                   brings its fill
 */
public record JobMaterials(boolean dugToStock, @Nullable String fill) {
    /**
     * The company keeps what it digs and brings what it fills with.
     */
    public static final JobMaterials COMPANY = new JobMaterials(false, null);
}
