/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.api.write.JobChange;
import de.eldoria.companies.api.write.JobProblem;
import de.eldoria.companies.api.write.JobRequest;
import de.eldoria.companies.api.write.PostJobResult;
import de.eldoria.companies.components.job.BuildBlock;
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
     * The land plugins are asked on the main thread. {@link de.eldoria.companies.events.job.JobPostEvent} is fired
     * before any money is taken; a listener that cancels it gives {@link JobProblem#REFUSED}.
     * <p>
     * This is a write operation: before Companies is ready the future completes exceptionally with an
     * {@link ApiNotReadyException}.
     *
     * @param request the job to post
     * @return future of the posted job, or why it was not posted
     */
    CompletableFuture<PostJobResult> post(JobRequest request);

    /**
     * Reads the blocks a build job asks for.
     *
     * @param id id of the job
     * @return future of the blocks, at offsets from the lowest corner of the job's area, empty for a job that is not a
     *     build job or does not exist
     */
    CompletableFuture<Optional<List<BuildBlock>>> blueprint(int id);

    /**
     * Takes an open job for the company of a member, as {@code /company job take} does: the member needs the
     * {@code manage_orders} permission, the company a free work slot, and the job may not be the company's own.
     * {@link de.eldoria.companies.events.job.JobTakeEvent} is fired first.
     * <p>
     * This is a write operation: before Companies is ready the future completes exceptionally with an
     * {@link ApiNotReadyException}.
     *
     * @param id     id of the job
     * @param member the member taking it
     * @return future of what came of it
     */
    CompletableFuture<JobChange> take(int id, UUID member);

    /**
     * Gives a job back for the company of a member, as {@code /company job giveback} does; the company gets the penalty
     * of an aborted order and the job is open again.
     * <p>
     * This is a write operation: before Companies is ready the future completes exceptionally with an
     * {@link ApiNotReadyException}.
     *
     * @param id     id of the job
     * @param member the member giving it back
     * @return future of what came of it
     */
    CompletableFuture<JobChange> giveBack(int id, UUID member);

    /**
     * Cancels an open job of its owner, as {@code /order job cancel} does; its price goes back unless it was posted
     * without charge, the fee is kept.
     * <p>
     * This is a write operation: before Companies is ready the future completes exceptionally with an
     * {@link ApiNotReadyException}.
     *
     * @param id    id of the job
     * @param owner the owner
     * @return future of what came of it
     */
    CompletableFuture<JobChange> cancel(int id, UUID owner);

    /**
     * Finishes a job for the company of a member, as {@code /company job done} does: a dig, terraform or build job has
     * its area checked on this server and is paid when it is done; a harvest job ends; a contract is said to be done
     * and waits for its owner. The check of an area runs on the main thread, so do not wait for the future there.
     * <p>
     * This is a write operation: before Companies is ready the future completes exceptionally with an
     * {@link ApiNotReadyException}.
     *
     * @param id     id of the job
     * @param member the member finishing it
     * @return future of what came of it; {@link JobChange#NOT_DONE} when blocks are left
     */
    CompletableFuture<JobChange> finish(int id, UUID member);

    /**
     * Approves a job its company holds, as {@code /order job approve} does, and pays the company in full as it stands:
     * a contract its company said is done, or a dig, terraform or build job even when its area is not finished or it
     * is disputed. A harvest job is refused; it ends instead.
     * <p>
     * This is a write operation: before Companies is ready the future completes exceptionally with an
     * {@link ApiNotReadyException}.
     *
     * @param id    id of the contract
     * @param owner its owner
     * @return future of what came of it
     */
    CompletableFuture<JobChange> approve(int id, UUID owner);

    /**
     * Rejects the work on a contract its company said is done, as {@code /order job reject} does; the company works on
     * it again.
     * <p>
     * This is a write operation: before Companies is ready the future completes exceptionally with an
     * {@link ApiNotReadyException}.
     *
     * @param id     id of the contract
     * @param owner  its owner
     * @param reason why, at most 256 characters
     * @return future of what came of it
     */
    CompletableFuture<JobChange> reject(int id, UUID owner, String reason);
}
