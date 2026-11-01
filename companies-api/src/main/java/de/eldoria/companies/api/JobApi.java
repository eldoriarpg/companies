/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.api.write.JobRequest;
import de.eldoria.companies.api.write.PostJobResult;
import de.eldoria.companies.components.job.IJob;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Jobs: work in a block box of an owner's land, or described in words, that companies take and are paid for. Taking,
 * finishing and ending jobs happen in game and on the dashboard.
 */
public interface JobApi {
    /**
     * Reads one job.
     *
     * @param id id of the job
     * @return future of the job, empty when no job has this id
     */
    CompletableFuture<Optional<? extends IJob>> byId(int id);

    /**
     * Reads the jobs that wait for a company.
     *
     * @param limit how many jobs at most, at least 1
     * @return future of the jobs that wait for a company, newest first
     * @throws IllegalArgumentException when the limit is below 1
     */
    CompletableFuture<List<? extends IJob>> open(int limit);

    /**
     * Reads the jobs a player posted.
     *
     * @param owner id of the player
     * @param limit how many jobs at most, at least 1
     * @return future of the jobs the player posted, newest first, in any state
     * @throws IllegalArgumentException when the limit is below 1
     */
    CompletableFuture<List<? extends IJob>> ofOwner(UUID owner, int limit);

    /**
     * Reads the jobs a company holds.
     *
     * @param company id of the company
     * @return future of the jobs the company holds: worked on, waiting for approval or disputed
     */
    CompletableFuture<List<? extends IJob>> ofCompany(int company);

    /**
     * Posts a job with the same checks as {@code /order job}: the area lies on this server, within the world's
     * height, on land the owner owns (or on unowned land where the server allows jobs there), within the size and
     * count limits of {@code jobs.yml}, and, unless {@link JobRequest#charge()} is false, the owner pays price and fee.
     * The land plugins are asked on the main thread.
     * <p>
     * This is a write operation: before Companies is ready the future completes exceptionally with an
     * {@link ApiNotReadyException}.
     *
     * @param request the job to post
     * @return future of the posted job, or why it was not posted
     */
    CompletableFuture<PostJobResult> post(JobRequest request);
}
