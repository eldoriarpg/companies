/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobState;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The jobs and the work done on them. Every change of a job is conditional on the state it is read in, so two servers,
 * or the job check and a command, never move a job twice. The methods block and are called off the main thread.
 */
public abstract class AJobData {
    /**
     * Stores a new job.
     *
     * @param job the job; its id, company and times of work are ignored
     * @return the id of the stored job, empty when it could not be stored
     */
    public abstract Optional<Integer> create(Job job);

    /**
     * @param id id of the job
     * @return the job, empty when there is none
     */
    public abstract Optional<Job> job(int id);

    /**
     * The jobs companies may take: open and not expired.
     *
     * @param now   the current time
     * @param limit how many to return at most
     * @return the jobs, the newest first
     */
    public abstract List<Job> open(LocalDateTime now, int limit);

    /**
     * @param owner a player
     * @param limit how many to return at most
     * @return the player's jobs, the newest first, whatever their state
     */
    public abstract List<Job> ofOwner(UUID owner, int limit);

    /**
     * @param company id of a company
     * @return the jobs the company works on
     */
    public abstract List<Job> ofCompany(int company);

    /**
     * Lets a company take an open job.
     *
     * @param job     id of the job
     * @param company id of the company
     * @param at      when it is taken
     * @param due     when the company's time runs out
     * @return true when the job was open and not expired, and is now the company's
     */
    public abstract boolean take(int job, int company, LocalDateTime at, LocalDateTime due);

    /**
     * Opens a taken job again, when the company gave it back or its time ran out. The work counted for the company is
     * dropped, so the next company starts with none.
     *
     * @param job     id of the job
     * @param company id of the company that had it
     * @return true when the company had the job and it is open again
     */
    public abstract boolean release(int job, int company);

    /**
     * Closes a job.
     *
     * @param job  id of the job
     * @param from the state it must be in
     * @param to   its final state
     * @param by   who closed it, null when nobody did
     * @param at   when it closed
     * @return true when it was in {@code from} and is now in {@code to}
     */
    public abstract boolean close(int job, JobState from, JobState to, @Nullable String by, LocalDateTime at);

    /**
     * Closes a taken job only while the given company still has it, so a job given back and taken by another company
     * meanwhile is left alone.
     *
     * @param job     id of the job
     * @param company id of the company that has it
     * @param to      its final state
     * @param by      who closed it, null when nobody did
     * @param at      when it closed
     * @return true when the company had the job and it is now in {@code to}
     */
    public abstract boolean closeTaken(int job, int company, JobState to, @Nullable String by, LocalDateTime at);

    /**
     * @param owner a player
     * @return how many of the player's jobs wait for a company
     */
    public abstract int openOf(UUID owner);

    /**
     * @param now the current time
     * @return the open jobs that expired
     */
    public abstract List<Job> expired(LocalDateTime now);

    /**
     * @param now the current time
     * @return the taken jobs whose company's time ran out
     */
    public abstract List<Job> overdue(LocalDateTime now);

    /**
     * The jobs of a server whose land and access must be checked: the waiting and taken ones and every one still noted
     * as granted.
     *
     * @param node the server
     * @return the jobs
     */
    public abstract List<Job> access(UUID node);

    /**
     * Notes whether the company's members have access to a job's area.
     *
     * @param job     id of the job
     * @param granted whether access is granted
     */
    public abstract void granted(int job, boolean granted);

    /**
     * @param job id of the job
     * @return the players a land hook gave access to the job's area, which it takes back later
     */
    public abstract Set<UUID> grants(int job);

    /**
     * Replaces the players a land hook gave access to a job's area.
     *
     * @param job     id of the job
     * @param players the players, empty when nobody has access anymore
     * @return true when they were stored
     */
    public abstract boolean grants(int job, Set<UUID> players);

    /**
     * Counts blocks a worker removed from a job's area, only while the company the work was done for still has the
     * job, so work done for a company that gave the job back is never counted for the next.
     *
     * @param job     id of the job
     * @param company id of the company the worker worked for
     * @param worker  the worker
     * @param blocks  how many
     */
    public abstract void work(int job, int company, UUID worker, int blocks);

    /**
     * @param area an area
     * @return the jobs that wait or are worked on whose areas share a block with it
     */
    public abstract List<Job> overlapping(JobArea area);

    /**
     * @param job id of the job
     * @return the blocks every worker removed, by worker
     */
    public abstract Map<UUID, Integer> workOf(int job);
}
