/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

import de.eldoria.companies.components.job.BuildBlock;
import de.eldoria.companies.components.job.JobBox;
import de.eldoria.companies.components.job.JobKind;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A job to post through {@link de.eldoria.companies.api.JobApi#post(JobRequest)}, with the same checks as
 * {@code /order job}. Start from the factory of its kind; each kind needs its own details.
 *
 * @param owner       the player who posts and pays for it; they need not be online
 * @param kind        what work it asks for
 * @param title       the title shown to companies
 * @param price       what the company is paid in all
 * @param dueHours    how long a company has to finish once it took the job
 * @param area        the block box of the work, on this server; null for a build job (its blueprint places it) and
 *                    optional for a contract
 * @param height      the height a terraform job shapes its area to, null for other kinds
 * @param surface     the block the top layer of a terraform job must be made of, null for any solid block
 * @param unitPrice   what a harvest job pays for each item, null for other kinds
 * @param build       where a build job places its blueprint and what it asks for, null for other kinds
 * @param description what the owner writes about a contract, null for nothing
 * @param charge      whether the owner pays price and fee; an uncharged job takes no fee, refunds nothing when it ends
 *                    unfinished, and is logged
 */
public record JobRequest(
        UUID owner,
        JobKind kind,
        String title,
        double price,
        int dueHours,
        @Nullable JobBox area,
        @Nullable Integer height,
        @Nullable Material surface,
        @Nullable Double unitPrice,
        @Nullable Build build,
        @Nullable String description,
        boolean charge) {
    /**
     * Checks that the request carries what its kind needs.
     *
     * @throws IllegalArgumentException when a detail of its kind is missing or one of another kind is set
     */
    public JobRequest {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(title, "title");
        if (area == null && kind != JobKind.BUILD && kind != JobKind.CONTRACT) {
            throw new IllegalArgumentException("A " + kind + " job needs an area");
        }
        if (area != null && kind == JobKind.BUILD) throw new IllegalArgumentException("A build job has no area");
        only(kind == JobKind.TERRAFORM, height != null, "a height");
        if (surface != null && kind != JobKind.TERRAFORM) {
            throw new IllegalArgumentException("Only a terraform job has a surface");
        }
        only(kind == JobKind.HARVEST, unitPrice != null, "an item price");
        only(kind == JobKind.BUILD, build != null, "a blueprint");
        if (description != null && kind != JobKind.CONTRACT) {
            throw new IllegalArgumentException("Only a contract has a description");
        }
        if (surface != null && !surface.isSolid()) throw new IllegalArgumentException(surface + " is not solid");
    }

    private static void only(boolean kindHasIt, boolean present, String detail) {
        if (kindHasIt != present) {
            throw new IllegalArgumentException(
                    (kindHasIt ? "This kind of job needs " : "This kind of job has no ") + detail);
        }
    }

    /**
     * A dig job: every block of the area must be cleared.
     *
     * @param owner    the player who posts and pays for it
     * @param title    the title
     * @param price    what the company is paid
     * @param dueHours how long a company has to finish
     * @param area     the area to clear
     * @return the request, charging the owner
     */
    public static JobRequest dig(UUID owner, String title, double price, int dueHours, JobBox area) {
        return new JobRequest(owner, JobKind.DIG, title, price, dueHours, area, null, null, null, null, null, true);
    }

    /**
     * A terraform job: every column of the area solid up to a height and empty above it.
     *
     * @param owner    the player who posts and pays for it
     * @param title    the title
     * @param price    what the company is paid
     * @param dueHours how long a company has to finish
     * @param area     the area to shape
     * @param height   the height of the top solid block of every column, inside the area
     * @param surface  the block the top layer must be made of, null for any solid block
     * @return the request, charging the owner
     */
    public static JobRequest terraform(
            UUID owner, String title, double price, int dueHours, JobBox area, int height, @Nullable Material surface) {
        return new JobRequest(
                owner, JobKind.TERRAFORM, title, price, dueHours, area, height, surface, null, null, null, true);
    }

    /**
     * A harvest job: the field in the area kept harvested and replanted, paid per item until the price is used up.
     *
     * @param owner     the player who posts and pays for it
     * @param title     the title
     * @param price     the budget of the job
     * @param dueHours  how long a company has to finish
     * @param area      the field
     * @param unitPrice what each harvested item pays
     * @return the request, charging the owner
     */
    public static JobRequest harvest(
            UUID owner, String title, double price, int dueHours, JobBox area, double unitPrice) {
        return new JobRequest(
                owner, JobKind.HARVEST, title, price, dueHours, area, null, null, unitPrice, null, null, true);
    }

    /**
     * A build job: the blueprint built where it is placed.
     *
     * @param owner    the player who posts and pays for it
     * @param title    the title
     * @param price    what the company is paid
     * @param dueHours how long a company has to finish
     * @param build    where the blueprint goes and its blocks
     * @return the request, charging the owner
     */
    public static JobRequest build(UUID owner, String title, double price, int dueHours, Build build) {
        return new JobRequest(owner, JobKind.BUILD, title, price, dueHours, null, null, null, null, build, null, true);
    }

    /**
     * A contract: work described in words that the owner approves.
     *
     * @param owner       the player who posts and pays for it
     * @param title       the title
     * @param price       what the company is paid
     * @param dueHours    how long a company has to finish
     * @param description what the work is, null or blank for nothing
     * @param place       a place to look at, null for none
     * @return the request, charging the owner
     */
    public static JobRequest contract(
            UUID owner,
            String title,
            double price,
            int dueHours,
            @Nullable String description,
            @Nullable JobBox place) {
        var text = description == null ? "" : description.strip();
        return new JobRequest(
                owner,
                JobKind.CONTRACT,
                title,
                price,
                dueHours,
                place,
                null,
                null,
                null,
                null,
                text.isEmpty() ? null : text,
                true);
    }

    /**
     * Copies this request, choosing whether the owner is charged.
     *
     * @param charge whether the owner pays price and fee
     * @return this request with that choice
     */
    public JobRequest charge(boolean charge) {
        return new JobRequest(
                owner, kind, title, price, dueHours, area, height, surface, unitPrice, build, description, charge);
    }

    /**
     * Where a build job places its blueprint and what it asks for.
     *
     * @param world  name of the world, on this server
     * @param x      x of the point the blocks are relative to
     * @param y      y of that point
     * @param z      z of that point
     * @param blocks the blocks to build, air left out; at least one
     */
    public record Build(String world, int x, int y, int z, List<BuildBlock> blocks) {
        /**
         * Copies the blocks.
         *
         * @throws IllegalArgumentException when there are no blocks
         */
        public Build {
            Objects.requireNonNull(world, "world");
            blocks = List.copyOf(blocks);
            if (blocks.isEmpty()) throw new IllegalArgumentException("A blueprint needs at least one block");
        }
    }
}
