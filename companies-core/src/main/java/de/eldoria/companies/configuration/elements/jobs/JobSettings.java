/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The jobs, the file {@code jobs.yml}: whether players may post them, where they may lie, how large they may be and
 * how long they run and wait for approval. The fee of a job is the order fee of {@code order.yml}. The jobs themselves are stored in the
 * database.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class JobSettings {
    private boolean enabled = true;
    private List<String> unownedWorlds = new ArrayList<>();
    private int digMaxVolume = 32768;
    private int maxChunks = 64;
    private int maxOpenPerOwner = 5;
    private int maxDueHours = 168;
    private int openDays = 7;
    private int approveHours = 72;

    public JobSettings() {}

    /**
     * @return whether players may post new jobs; running jobs go on either way
     */
    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * The worlds where a job may lie on land that belongs to nobody, no plot and no region. Off everywhere unless a
     * world is listed.
     *
     * @return the names of the worlds, as written
     */
    public List<String> unownedWorlds() {
        return unownedWorlds == null ? List.of() : List.copyOf(unownedWorlds);
    }

    public void unownedWorlds(List<String> unownedWorlds) {
        this.unownedWorlds = new ArrayList<>(unownedWorlds);
    }

    /**
     * @param world name of a world
     * @return whether jobs may lie on unowned land in the world, ignoring case
     */
    public boolean unownedAllowed(String world) {
        var wanted = world.toLowerCase(Locale.ROOT);
        return unownedWorlds().stream()
                .anyMatch(name -> name.toLowerCase(Locale.ROOT).equals(wanted));
    }

    /**
     * @return the most blocks the area of a dig job may hold, at least 1
     */
    public int digMaxVolume() {
        return Math.max(1, digMaxVolume);
    }

    public void digMaxVolume(int digMaxVolume) {
        this.digMaxVolume = digMaxVolume;
    }

    /**
     * @return the most chunks the area of a job may touch, at least 1, so a long thin area can not load half a world
     */
    public int maxChunks() {
        return Math.max(1, maxChunks);
    }

    public void maxChunks(int maxChunks) {
        this.maxChunks = maxChunks;
    }

    /**
     * @return the most jobs a player may have waiting for a company at once, at least 1
     */
    public int maxOpenPerOwner() {
        return Math.max(1, maxOpenPerOwner);
    }

    public void maxOpenPerOwner(int maxOpenPerOwner) {
        this.maxOpenPerOwner = maxOpenPerOwner;
    }

    /**
     * @return the most hours an owner may give a company to finish a job, at least 1
     */
    public int maxDueHours() {
        return Math.max(1, maxDueHours);
    }

    public void maxDueHours(int maxDueHours) {
        this.maxDueHours = maxDueHours;
    }

    /**
     * @return how many days a job waits for a company before it expires and its price goes back, at least 1
     */
    public int openDays() {
        return Math.max(1, openDays);
    }

    public void openDays(int openDays) {
        this.openDays = openDays;
    }

    /**
     * @return how many hours a job the company says is done waits for its owner before it is approved and paid, at
     *     least 1
     */
    public int approveHours() {
        return Math.max(1, approveHours);
    }

    public void approveHours(int approveHours) {
        this.approveHours = approveHours;
    }
}
