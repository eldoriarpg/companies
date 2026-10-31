/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.components.job.IJob;
import de.eldoria.companies.components.job.IJobArea;
import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.components.job.JobState;
import de.eldoria.companies.data.wrapper.job.Job;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * A stored job as the API shows it.
 *
 * @param job the stored job
 */
record ApiJob(Job job) implements IJob {
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
}
