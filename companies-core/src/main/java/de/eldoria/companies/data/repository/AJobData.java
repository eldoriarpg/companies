/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.components.job.JobState;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobMark;
import de.eldoria.companies.data.wrapper.job.JobReviewEntry;
import de.eldoria.companies.data.wrapper.job.StoredArea;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
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
     * @return the player's jobs, whatever their state: the drafts first, so none drops out of the list, then the
     *     newest first
     */
    public abstract List<Job> ofOwner(UUID owner, int limit);

    /**
     * @param company id of a company
     * @return the jobs the company holds: worked on, waiting for approval or disputed
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
     * Opens a job the company works on or waits for approval of again, when the company gave it back, its time ran out
     * or it was disbanded. The work counted for the company and its review are dropped, so the next company starts
     * fresh.
     *
     * @param job     id of the job
     * @param company id of the company that had it
     * @return true when the company had the job and it is open again
     */
    public abstract boolean release(int job, int company);

    /**
     * Sets the description of a job that waits for a company.
     *
     * @param job         id of the job
     * @param description the description, null for none
     * @return true when the job was open and its description changed
     */
    public abstract boolean describe(int job, @Nullable String description);

    /**
     * Notes that the company says a taken job is done; it then waits for the owner.
     *
     * @param job     id of the job
     * @param company id of the company that has it
     * @param by      the member saying so
     * @param at      when
     * @return true when the company had the job taken and it now waits for the owner
     */
    public abstract boolean markDone(int job, int company, UUID by, LocalDateTime at);

    /**
     * Sends a job waiting for the owner back to work, with the reason and a new due time.
     *
     * @param job    id of the job
     * @param reason why the owner rejected the work
     * @param due    when the company's time now runs out
     * @return true when the job waited for the owner and is taken again
     */
    public abstract boolean reject(int job, String reason, LocalDateTime due);

    /**
     * Notes that the owner asks the company holding a taken job to let it be cancelled.
     *
     * @param job     id of the job
     * @param company id of the company that holds it
     * @param at      when the owner asked
     * @return true when the company held the job taken and no request was open yet
     */
    public abstract boolean askCancel(int job, int company, LocalDateTime at);

    /**
     * Cancels a job the company holds taken, as the owner asked; only while the request is still open, so a refusal
     * that came first wins.
     *
     * @param job     id of the job
     * @param company id of the company that holds it
     * @param by      who agreed, as noted
     * @param at      when
     * @return true when the company held the job taken with a request open and it is now cancelled
     */
    public abstract boolean acceptCancel(int job, int company, @Nullable String by, LocalDateTime at);

    /**
     * Drops the owner's request to cancel a job, when the company refused it.
     *
     * @param job     id of the job
     * @param company id of the company that holds it
     * @return true when the company held the job taken with a request open
     */
    public abstract boolean refuseCancel(int job, int company);

    /**
     * Notes a step of the review of a job's work.
     *
     * @param entry the step
     */
    public abstract void review(JobReviewEntry entry);

    /**
     * @param job id of a job
     * @return the steps of the review of its work, oldest first
     */
    public abstract List<JobReviewEntry> reviews(int job);

    /**
     * Opens a dispute about a job a company holds.
     *
     * @param job     id of the job
     * @param company id of the company that must hold it
     * @param from    the state it must be in
     * @param by      who opens it
     * @param reason  why
     * @param at      when
     * @return true when the company held it in {@code from} and it is now disputed
     */
    public abstract boolean dispute(int job, int company, JobState from, UUID by, String reason, LocalDateTime at);

    /**
     * Ends a dispute without a settlement, when the side that opened it withdraws it. The job goes back to where it was:
     * waiting for the owner when the company had said it is done, taken otherwise. Either way it gets a new due time.
     *
     * @param job    id of the job
     * @param by     who opened the dispute
     * @param due    when the company's time now runs out
     * @param doneAt when the company said the work is done, from which approval without an answer counts; null when it
     *               did not, which makes the job taken again
     * @return true when the job was disputed by {@code by} and is back where it was
     */
    public abstract boolean withdraw(int job, UUID by, LocalDateTime due, @Nullable LocalDateTime doneAt);

    /**
     * Closes a disputed job with an admin's split.
     *
     * @param job     id of the job
     * @param percent the percent of the price the company gets
     * @param by      the admin
     * @param at      when
     * @return true when it was disputed and is now settled
     */
    public abstract boolean settle(int job, int percent, String by, LocalDateTime at);

    /**
     * @param before the latest time the company may have said the work is done
     * @return the jobs waiting for their owner since then or earlier
     */
    public abstract List<Job> waiting(LocalDateTime before);

    /**
     * @return the disputed jobs, the oldest dispute first
     */
    public abstract List<Job> disputed();

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
    public boolean closeTaken(int job, int company, JobState to, @Nullable String by, LocalDateTime at) {
        return closeHeld(job, company, JobState.TAKEN, to, by, at);
    }

    /**
     * Closes a job only while the given company still holds it in the given state, so a job given back and taken by
     * another company meanwhile is left alone.
     *
     * @param job     id of the job
     * @param company id of the company that holds it
     * @param from    the state it must be in
     * @param to      its final state
     * @param by      who closed it, null when nobody did
     * @param at      when it closed
     * @return true when the company held the job in {@code from} and it is now in {@code to}
     */
    public abstract boolean closeHeld(
            int job, int company, JobState from, JobState to, @Nullable String by, LocalDateTime at);

    /**
     * @param owner a player
     * @return how many of the player's jobs wait for a company
     */
    public abstract int openOf(UUID owner);

    /**
     * @param owner the player who posted or saved them
     * @param state a state
     * @return how many of the player's jobs are in that state
     */
    public abstract int countOf(UUID owner, JobState state);

    /**
     * Posts a draft: it becomes open as the job given, with the title, price, time, fee and land it was charged for,
     * whatever an edit stored meanwhile, and its time to be taken starts.
     *
     * @param job the posted job, with the id of the draft
     * @return true when it was still a draft and is open now
     */
    public abstract boolean publish(Job job);

    /**
     * Changes what a draft asks for.
     *
     * @param id          id of the draft
     * @param title       its title
     * @param description its description, null for none
     * @param price       its price
     * @param dueHours    how long a company has to finish it
     * @return true when it was still a draft
     */
    public abstract boolean editDraft(int id, String title, @Nullable String description, double price, int dueHours);

    /**
     * Deletes a draft with its blueprint, only once its stock is empty. The draft is locked meanwhile, so items handed
     * in after the stock was taken out keep it from being deleted rather than being deleted with it.
     *
     * @param id id of the draft
     * @return true when it was still a draft with an empty stock and is gone
     */
    public abstract boolean deleteDraft(int id);

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
     * The jobs of a server whose land and access must be checked: the waiting and taken ones with an area and every one
     * still noted as granted. Contracts have no land and are never among them.
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
     * @param job     id of the job
     * @param company id of the company working on it
     * @return the positions remembered for the job and company, by what is remembered about them
     */
    public abstract Map<JobMark.Kind, Set<Long>> marks(int job, int company);

    /**
     * Writes changes to the remembered positions: true remembers a position, false forgets it. A position is only
     * remembered while its job is taken or disputed by the company it was remembered for.
     *
     * @param changes the changes
     * @return true when they were written, false when nothing was
     */
    public abstract boolean marks(Map<JobMark, Boolean> changes);

    /**
     * Forgets every position remembered for a job.
     *
     * @param job id of the job
     */
    public abstract void clearMarks(int job);

    /**
     * Forgets the positions remembered for jobs that are neither taken nor disputed, or for a company that no longer
     * has the job.
     */
    public abstract void pruneMarks();

    /**
     * @param job id of the job
     * @return how many blocks of a dig job's area were already cleared when it was posted, empty when unknown
     */
    public abstract OptionalLong airAtPost(int job);

    /**
     * Notes how many blocks of a dig job's area were already cleared when it was posted, once.
     *
     * @param job    id of the job
     * @param blocks the cleared blocks
     * @return true when it was not noted before
     */
    public abstract boolean airAtPost(int job, long blocks);

    /**
     * @param area an area
     * @return the area jobs that wait or are held by a company whose areas share a block with it
     */
    public abstract List<Job> overlapping(JobArea area);

    /**
     * @param job id of the job
     * @return the blocks every worker removed, by worker
     */
    public abstract Map<UUID, Integer> workOf(int job);

    /**
     * Puts harvested items into a harvest job's stock and adds what they are paid to what the job paid, only while the
     * company still has the job taken and the budget covers the pay.
     *
     * @param job     id of the job
     * @param company id of the company that harvested them
     * @param items   the items, as amounts by material name
     * @param cents   what they are paid before tax, in cents
     * @return true when they were stored and paid
     */
    public abstract boolean harvested(int job, int company, Map<String, Integer> items, long cents);

    /**
     * Puts items into a job's stock without paying for them, when the budget is used up.
     *
     * @param job   id of the job
     * @param items the items, as amounts by material name
     */
    public abstract void stock(int job, Map<String, Integer> items);

    /**
     * Puts what a supplied draft still lacks of the offered items into its stock, while it is a supplied draft. The
     * draft is locked meanwhile, so hand-ins, posting and deleting it run one after the other and nothing is taken
     * beyond what is needed.
     *
     * @param job     id of the draft
     * @param needs   what its blueprint takes, as amounts by material name
     * @param offered the items offered, as amounts by material name
     * @return what was taken, by material name; empty when it is no supplied draft or the items could not be stored
     */
    public abstract Optional<Map<String, Integer>> supply(
            int job, Map<String, Integer> needs, Map<String, Integer> offered);

    /**
     * Stores the blueprint of a build job.
     *
     * @param job  id of the job
     * @param data the blueprint as compact text
     * @return true when it was stored
     */
    public abstract boolean blueprint(int job, String data);

    /**
     * @param job id of the job
     * @return the blueprint of a build job as compact text, empty for other jobs
     */
    public abstract Optional<String> blueprint(int job);

    /**
     * @param job id of the job
     * @return the items in the job's stock, as amounts by material name, without empty ones
     */
    public abstract Map<String, Integer> stock(int job);

    /**
     * Takes items out of a job's stock.
     *
     * @param job      id of the job
     * @param material the material name
     * @param amount   how many
     * @return true when the stock held that many and they are taken
     */
    public abstract boolean takeStock(int job, String material, int amount);

    /**
     * Gives the company holding a job more time and closes its open request. Only changes the job while the company
     * holds it, its time still ends at {@code from} and has not run out, so two extensions never add up wrongly and a
     * job the primary is about to open again is never extended.
     *
     * @param job     id of the job
     * @param company id of the company holding it
     * @param from    when its time ends now
     * @param until   when its time ends after the extension
     * @param hours   the hours added
     * @param asked   when the request it answers was made, null when the owner extends unasked
     * @param now     the time now
     * @return true when it was stored
     */
    public abstract boolean extend(
            int job,
            int company,
            LocalDateTime from,
            LocalDateTime until,
            int hours,
            @Nullable LocalDateTime asked,
            LocalDateTime now);

    /**
     * Notes that the company holding a job asks for more time, when it has no open request, asked less often than
     * allowed and its time has not run out.
     *
     * @param job         id of the job
     * @param company     id of the company holding it
     * @param hours       the hours it asks for
     * @param reason      why
     * @param at          when it asks
     * @param maxRequests how often a company may ask per job
     * @return true when it was stored
     */
    public abstract boolean askExtension(
            int job, int company, int hours, String reason, LocalDateTime at, int maxRequests);

    /**
     * Closes the open request of the company holding a job without more time.
     *
     * @param job     id of the job
     * @param company id of the company holding it
     * @return true when there was a request
     */
    public abstract boolean refuseExtension(int job, int company);

    /**
     * Stores a schematic a player uploaded, replacing one of the same name.
     *
     * @param owner the player
     * @param name  its name, unique per player
     * @param data  the cleaned schematic file as base64
     * @param at    when it was uploaded
     */
    public abstract void saveSchematic(UUID owner, String name, String data, LocalDateTime at);

    /**
     * @param owner a player
     * @return the names of the schematics the player uploaded, sorted
     */
    public abstract List<String> schematicNames(UUID owner);

    /**
     * @param owner a player
     * @param name  the name of one of the player's schematics
     * @return the schematic file as base64, empty when the player has none of that name
     */
    public abstract Optional<String> schematic(UUID owner, String name);

    /**
     * @param owner a player
     * @param name  the name of one of the player's schematics
     * @return true when it existed and is gone
     */
    public abstract boolean deleteSchematic(UUID owner, String name);

    /**
     * Stores an area a player named, replacing one of the same name.
     *
     * @param owner the player
     * @param area  the area and its name
     * @param at    when it was stored
     */
    public abstract void saveArea(UUID owner, StoredArea area, LocalDateTime at);

    /**
     * @param owner a player
     * @return the player's stored areas, sorted by name
     */
    public abstract List<StoredArea> areas(UUID owner);

    /**
     * @param owner a player
     * @param name  the name of one of the player's areas
     * @return the area, empty when the player has none of that name
     */
    public abstract Optional<StoredArea> area(UUID owner, String name);

    /**
     * @param owner a player
     * @param name  the name of one of the player's areas
     * @return true when it existed and is gone
     */
    public abstract boolean deleteArea(UUID owner, String name);
}
