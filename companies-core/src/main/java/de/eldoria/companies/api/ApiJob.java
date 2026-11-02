/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.components.job.IJob;
import de.eldoria.companies.components.job.IJobArea;
import de.eldoria.companies.components.job.IJobExtension;
import de.eldoria.companies.components.job.IJobHarvest;
import de.eldoria.companies.components.job.IJobReview;
import de.eldoria.companies.components.job.IJobTarget;
import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.components.job.JobState;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.jobs.TerraformCheck;
import org.bukkit.Material;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * A stored job as the API shows it.
 *
 * @param job the stored job
 */
public record ApiJob(Job job) implements IJob {
    @Override
    public int id() {
        return job.id();
    }

    @Override
    public UUID owner() {
        return job.owner();
    }

    @Override
    public JobKind kind() {
        return job.kind();
    }

    @Override
    public String title() {
        return job.title();
    }

    @Override
    public Optional<String> description() {
        return Optional.ofNullable(job.description());
    }

    @Override
    public double price() {
        return job.price();
    }

    @Override
    public double fee() {
        return job.fee();
    }

    @Override
    public JobState state() {
        return job.state();
    }

    @Override
    public OptionalInt company() {
        var company = job.company();
        return company == null ? OptionalInt.empty() : OptionalInt.of(company);
    }

    @Override
    public UUID node() {
        return job.node();
    }

    @Override
    public IJobArea area() {
        return job.area();
    }

    @Override
    public int dueHours() {
        return job.dueHours();
    }

    @Override
    public LocalDateTime created() {
        return job.created();
    }

    @Override
    public LocalDateTime expires() {
        return job.expires();
    }

    @Override
    public Optional<LocalDateTime> taken() {
        return Optional.ofNullable(job.taken());
    }

    @Override
    public Optional<LocalDateTime> due() {
        return Optional.ofNullable(job.due());
    }

    @Override
    public Optional<LocalDateTime> closed() {
        return Optional.ofNullable(job.closed());
    }

    @Override
    public boolean charged() {
        return job.charged();
    }

    @Override
    public IJobExtension extension() {
        var extension = job.extension();
        var asked = extension.askedHours();
        return new Extension(
                extension.extendedHours(),
                extension.requests(),
                asked == null ? OptionalInt.empty() : OptionalInt.of(asked),
                Optional.ofNullable(extension.reason()),
                Optional.ofNullable(extension.askedAt()));
    }

    @Override
    public Optional<IJobTarget> target() {
        var target = job.target();
        if (target == null) return Optional.empty();
        var surface = TerraformCheck.surface(target);
        return Optional.of(new Target(target.y(), Optional.ofNullable(surface)));
    }

    @Override
    public Optional<IJobHarvest> harvest() {
        var harvest = job.harvest();
        if (harvest == null) return Optional.empty();
        return Optional.of(new Harvest(harvest.unitPrice(), harvest.paid()));
    }

    @Override
    public IJobReview review() {
        var review = job.review();
        var settled = review.settled();
        return new Review(
                Optional.ofNullable(review.doneAt()),
                Optional.ofNullable(review.doneBy()),
                Optional.ofNullable(review.rejected()),
                Optional.ofNullable(review.disputeBy()),
                Optional.ofNullable(review.dispute()),
                Optional.ofNullable(review.disputedAt()),
                settled == null ? OptionalInt.empty() : OptionalInt.of(settled));
    }

    /**
     * The extra time of a job as the API shows it.
     *
     * @param extendedHours the hours the owner added
     * @param requests      how often the company asked
     * @param askedHours    the hours asked for now
     * @param reason        why the company asks
     * @param askedAt       when it asked
     */
    private record Extension(
            int extendedHours,
            int requests,
            OptionalInt askedHours,
            Optional<String> reason,
            Optional<LocalDateTime> askedAt)
            implements IJobExtension {}

    /**
     * The target of a terraform job as the API shows it.
     *
     * @param y       height of the top layer
     * @param surface block of the top layer
     */
    private record Target(int y, Optional<Material> surface) implements IJobTarget {}

    /**
     * The terms of a harvest job as the API shows them.
     *
     * @param unitPrice price per item
     * @param paid      paid so far
     */
    private record Harvest(double unitPrice, double paid) implements IJobHarvest {}

    /**
     * The review of a job as the API shows it.
     *
     * @param doneAt         when the company said it is done
     * @param doneBy         who said it
     * @param rejection      why the owner rejected it last
     * @param disputedBy     who opened the dispute
     * @param dispute        why
     * @param disputedAt     when
     * @param settledPercent the share an admin gave the company
     */
    private record Review(
            Optional<LocalDateTime> doneAt,
            Optional<UUID> doneBy,
            Optional<String> rejection,
            Optional<UUID> disputedBy,
            Optional<String> dispute,
            Optional<LocalDateTime> disputedAt,
            OptionalInt settledPercent)
            implements IJobReview {}
}
