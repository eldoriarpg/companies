/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.data.repository.AJobData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.jobs.hooks.JobHooks;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * What the commands of jobs work with.
 *
 * @param data       the jobs
 * @param service    posts, takes, gives back, cancels and finishes jobs
 * @param hooks      the land and selection plugins
 * @param access     keeps the access to the areas of this server right
 * @param progress   reads how far the work on a job of this server is
 * @param node       this server, which holds the areas of the jobs posted here
 * @param blueprints the blueprints of build jobs
 * @param ghosts     shows the missing blocks of build jobs
 * @param costs      what the blocks of build jobs cost
 * @param bars       shows members in an area how far its job is
 * @param uploads    the schematics players uploaded
 * @param areas      the areas players marked and stored
 * @param tool       the item that marks the corners of an area
 * @param checks     checks whether an area job may be posted on an area of this server
 */
public record JobServices(
        AJobData data,
        JobService service,
        JobHooks hooks,
        JobAccess access,
        JobProgress progress,
        UUID node,
        Blueprints blueprints,
        BuildGhosts ghosts,
        MaterialCosts costs,
        ProgressBars bars,
        SchematicUploads uploads,
        StoredAreas areas,
        AreaTool tool,
        AreaChecks checks) {
    /**
     * Finishes a job for a member's company: a harvest job ends, a contract is said to be done and waits for its owner,
     * and a dig, terraform or build job has its area checked on this server and is paid when it is done. Blocks; call
     * it off the main thread.
     *
     * @param company the company
     * @param member  the member finishing it
     * @param id      id of the job
     * @return what came of it; {@link JobService.Change#OTHER_SERVER} for an area on another server and
     *     {@link JobService.Change#WORLD} for one whose world is not loaded or whose check took too long
     */
    public JobService.Finished finish(CompanyProfile company, UUID member, int id) {
        var may = service.mayFinish(company, member, id);
        if (may == JobService.Change.HARVEST) {
            access.flush(id);
            var ended = service.endAsCompany(company, member, id);
            return ended == JobService.Change.DONE
                    ? new JobService.Finished.Ended()
                    : new JobService.Finished.Refused(ended);
        }
        if (may == JobService.Change.CONTRACT) {
            var marked = service.markDone(company, member, id);
            if (marked != JobService.Change.DONE) return new JobService.Finished.Refused(marked);
            return new JobService.Finished.Waiting(service.approveAt());
        }
        if (may != JobService.Change.DONE) return new JobService.Finished.Refused(may);
        var job = data.job(id).orElse(null);
        if (job == null) return new JobService.Finished.Refused(JobService.Change.UNKNOWN);
        if (!job.node().equals(node)) return new JobService.Finished.Refused(JobService.Change.OTHER_SERVER);
        access.flush(id);
        Optional<DigCheck.Progress> scanned;
        try {
            scanned = progress.of(job, true)
                    .copy()
                    .orTimeout(JobAccess.MAIN_THREAD_SECONDS * 3L, TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException e) {
            scanned = Optional.empty();
        }
        if (scanned.isEmpty()) return new JobService.Finished.Refused(JobService.Change.WORLD);
        return service.finish(company, member, id, scanned.get());
    }
}
