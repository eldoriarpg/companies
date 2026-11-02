/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.api.ApiJob;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.events.job.CancellableJobEvent;
import de.eldoria.companies.events.job.JobDoneEvent;
import de.eldoria.companies.events.job.JobEndEvent;
import de.eldoria.companies.events.job.JobExtendEvent;
import de.eldoria.companies.events.job.JobFinishEvent;
import de.eldoria.companies.events.job.JobPostEvent;
import de.eldoria.companies.events.job.JobTakeEvent;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Fires the API events of jobs where their change is won, as {@link JobService.Events}: {@link JobPostEvent} and
 * {@link JobTakeEvent} before a job is posted or taken, so a listener can stop it, and {@link JobExtendEvent},
 * {@link JobDoneEvent}, {@link JobFinishEvent} and {@link JobEndEvent} after the change.
 */
public final class JobApiEvents implements JobService.Events {
    private final Consumer<Event> call;
    private final Function<UUID, OfflinePlayer> players;

    /**
     * @param call    fires an event on the calling thread
     * @param players finds a player by id
     */
    public JobApiEvents(Consumer<Event> call, Function<UUID, OfflinePlayer> players) {
        this.call = call;
        this.players = players;
    }

    @Override
    public boolean allowsPost(Job draft) {
        return allowed(new JobPostEvent(new ApiJob(draft), players.apply(draft.owner())));
    }

    @Override
    public boolean allowsTake(Job job, CompanyProfile company, UUID member) {
        return allowed(new JobTakeEvent(new ApiJob(job), company, players.apply(member)));
    }

    @Override
    public void extended(Job job, int hours) {
        call.accept(new JobExtendEvent(new ApiJob(job), hours));
    }

    @Override
    public void markedDone(Job job) {
        call.accept(new JobDoneEvent(new ApiJob(job)));
    }

    @Override
    public void finished(Job job, double paid) {
        call.accept(new JobFinishEvent(new ApiJob(job), paid));
    }

    @Override
    public void ended(Job job, JobEndEvent.Reason reason) {
        call.accept(new JobEndEvent(new ApiJob(job), reason));
    }

    private boolean allowed(CancellableJobEvent event) {
        call.accept(event);
        return !event.isCancelled();
    }
}
