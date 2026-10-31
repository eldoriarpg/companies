/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs.hooks;

import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A plugin that owns land, such as WorldGuard or PlotSquared: it says whether the owner of a job may give its area
 * away to work on, and gives the working company's members access to it while the job runs. Every method runs on the
 * main thread.
 */
public interface JobLandHook {
    /**
     * @return the name of the plugin
     */
    String plugin();

    /**
     * @param owner the player creating the job, online or not
     * @param area  the area of the job
     * @return what the plugin's land says about the area
     */
    Claim claim(OfflinePlayer owner, JobArea area);

    /**
     * Gives exactly these players access to the area of a running job, as its kind needs, replacing the access of an
     * earlier call. A hook leaves jobs on land it does not handle alone and answers an empty set for them.
     *
     * @param job     the job
     * @param members the members of the working company
     * @param granted the players the hook gave access to before, as it answered last time
     * @return the players the hook gave access to and takes back later; never a player who had access before the job
     */
    Set<UUID> grant(Job job, Collection<UUID> members, Set<UUID> granted);

    /**
     * The players the hook would have given access to after {@link #grant} with these members, without changing
     * anything, so they are stored before they get access.
     *
     * @param job     the job
     * @param members the members of the working company
     * @param granted the players the hook gave access to before
     * @return the players it will have given access to, at least those of {@code granted}
     */
    default Set<UUID> planned(Job job, Collection<UUID> members, Set<UUID> granted) {
        return granted;
    }

    /**
     * Takes every access the hook gave for a job back. Calling it for a job without access does nothing.
     *
     * @param job     the job
     * @param granted the players the hook gave access to, as it answered last time
     * @return true when the access is gone; false when the hook could not act now, such as while the job's world is
     *     not loaded, so it is asked again later
     */
    boolean revoke(Job job, Set<UUID> granted);

    /**
     * Whether the land of a job is still as it was posted: the plot or region still exists and belongs to the job's
     * owner, or unowned land is still nobody's. A hook answers true for land it does not handle and when it can not
     * tell, such as while the world is not loaded.
     *
     * @param job the job
     * @return false when the land changed hands
     */
    boolean owns(Job job);

    /**
     * Whether a block lies on the land a job gives access to, which may be larger than its area, such as the whole plot
     * whose members are trusted.
     *
     * @param job   the job
     * @param world name of the block's world
     * @param x     x of the block
     * @param y     y of the block
     * @param z     z of the block
     * @return true when the hook's access for the job reaches the block
     */
    default boolean reaches(Job job, String world, int x, int y, int z) {
        return false;
    }

    /**
     * The area of the land a player stands in and owns, so a whole plot can be offered as a job.
     *
     * @param player the player
     * @return the area, empty when the player stands on none of their own
     */
    default Optional<JobArea> ownArea(Player player) {
        return Optional.empty();
    }
}
