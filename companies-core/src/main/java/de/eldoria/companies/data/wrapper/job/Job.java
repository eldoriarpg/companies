/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.job;

import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A job stored in {@code jobs}: work that a company takes like an order, paid from the price the owner paid when
 * creating it. Area kinds work in an area of the owner's land; a contract is work described in words, its area only a
 * place to look at.
 * <p>
 * The owner pays the price and the fee at once. While it is {@link JobState#OPEN} any company may take it until it
 * expires. A company that takes it has {@code dueHours} to finish and gets access to the area on the server whose world
 * it lies in, the {@code node}; when the time runs out the job is open again. When the work checks out the company's
 * members are paid by their share of the work, with tax and treasury share as for orders.
 *
 * @param id          id of the job, 0 before it is stored
 * @param owner       the player who pays for it
 * @param kind        what work it asks for
 * @param title       title shown to companies
 * @param description what the owner wrote about it, null for none
 * @param price       what the company is paid in all
 * @param fee         the fee the owner paid on top
 * @param state       where it is in its life
 * @param company     the company working on it, null while nobody does
 * @param node        the server whose world holds the area
 * @param area        the block box of the work
 * @param land        whose land the area lies on
 * @param landRef     the plot or region the land check found, null for unowned land
 * @param dueHours    how long a company has to finish once it took the job
 * @param created     when it was created
 * @param expires     when it expires while nobody took it
 * @param taken       when the company took it, null while open
 * @param due         when the company's time runs out, null while open
 * @param closed      when it reached a final state, null while it runs
 * @param closedBy    who closed it, null while it runs
 * @param granted     whether the company's members have access to the area right now
 * @param review      how the end of the work is reviewed
 * @param target      the shape a terraform job asks for, null for other kinds
 * @param harvest     the item price and what was paid of a harvest job, null for other kinds
 * @param extension   the extra time the company holding the job got, and its open request for more
 */
public record Job(
        int id,
        UUID owner,
        JobKind kind,
        String title,
        @Nullable String description,
        double price,
        double fee,
        JobState state,
        @Nullable Integer company,
        UUID node,
        JobArea area,
        JobLand land,
        @Nullable String landRef,
        int dueHours,
        LocalDateTime created,
        LocalDateTime expires,
        @Nullable LocalDateTime taken,
        @Nullable LocalDateTime due,
        @Nullable LocalDateTime closed,
        @Nullable String closedBy,
        boolean granted,
        JobReview review,
        @Nullable JobTarget target,
        @Nullable JobHarvest harvest,
        JobExtension extension) {
    /**
     * The longest title.
     */
    public static final int MAX_TITLE = 64;

    /**
     * The longest description.
     */
    public static final int MAX_DESCRIPTION = 512;

    /**
     * A new open job, not stored yet.
     *
     * @param owner    the player who pays for it
     * @param kind     what work it asks for
     * @param title    title shown to companies
     * @param price    what the company is paid
     * @param fee      the fee the owner pays on top
     * @param node     the server whose world holds the area
     * @param area     the block box of the work
     * @param land     whose land the area lies on
     * @param landRef  the plot or region of the land, null for unowned land
     * @param dueHours how long a company has to finish
     * @param created  when it is created
     * @param expires  when it expires while nobody takes it
     * @return the job
     */
    public static Job draft(
            UUID owner,
            JobKind kind,
            String title,
            double price,
            double fee,
            UUID node,
            JobArea area,
            JobLand land,
            @Nullable String landRef,
            int dueHours,
            LocalDateTime created,
            LocalDateTime expires) {
        return new Job(
                0,
                owner,
                kind,
                title,
                null,
                price,
                fee,
                JobState.OPEN,
                null,
                node,
                area,
                land,
                landRef,
                dueHours,
                created,
                expires,
                null,
                null,
                null,
                null,
                false,
                JobReview.NONE,
                null,
                null,
                JobExtension.NONE);
    }

    /**
     * @param id the id it was stored with
     * @return the job with that id
     */
    public Job withId(int id) {
        return new Job(
                id,
                owner,
                kind,
                title,
                description,
                price,
                fee,
                state,
                company,
                node,
                area,
                land,
                landRef,
                dueHours,
                created,
                expires,
                taken,
                due,
                closed,
                closedBy,
                granted,
                review,
                target,
                harvest,
                extension);
    }

    /**
     * @param target the shape a terraform job asks for
     * @return the job with that target
     */
    public Job withTarget(@Nullable JobTarget target) {
        return new Job(
                id,
                owner,
                kind,
                title,
                description,
                price,
                fee,
                state,
                company,
                node,
                area,
                land,
                landRef,
                dueHours,
                created,
                expires,
                taken,
                due,
                closed,
                closedBy,
                granted,
                review,
                target,
                harvest,
                extension);
    }

    /**
     * @param description what the owner writes about the job, null for nothing
     * @return the job with that description
     */
    public Job withDescription(@Nullable String description) {
        return new Job(
                id,
                owner,
                kind,
                title,
                description,
                price,
                fee,
                state,
                company,
                node,
                area,
                land,
                landRef,
                dueHours,
                created,
                expires,
                taken,
                due,
                closed,
                closedBy,
                granted,
                review,
                target,
                harvest,
                extension);
    }

    /**
     * @param harvest the item price of a harvest job
     * @return the job with that price
     */
    public Job withHarvest(@Nullable JobHarvest harvest) {
        return new Job(
                id,
                owner,
                kind,
                title,
                description,
                price,
                fee,
                state,
                company,
                node,
                area,
                land,
                landRef,
                dueHours,
                created,
                expires,
                taken,
                due,
                closed,
                closedBy,
                granted,
                review,
                target,
                harvest,
                extension);
    }

    /**
     * @return what is left of the price to give back: the whole price, less what a harvest job already paid out
     */
    public double unpaid() {
        var spent = harvest;
        return spent == null ? price : Math.max(0, price - spent.paid());
    }

    /**
     * @return the region name a job region on the area carries
     */
    public String regionName() {
        return "companies-job-" + id;
    }
}
