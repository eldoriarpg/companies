/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs.hooks;

import de.eldoria.companies.data.wrapper.job.JobArea;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Reads the area a player selected, such as with WorldEdit's {@code //pos1} and {@code //pos2}. Runs on the main
 * thread.
 */
public interface SelectionHook {
    /**
     * @param player the player
     * @return the selected box, empty when the player selected none or not a box
     */
    Optional<JobArea> selection(Player player);
}
