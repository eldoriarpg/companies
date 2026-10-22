/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.data.wrapper.company.Application;
import de.eldoria.companies.data.wrapper.company.ApplyResult;
import de.eldoria.companies.data.wrapper.company.Invite;
import de.eldoria.companies.data.wrapper.company.JoinResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Stores invites and applications and is the only way a player becomes a member of a company.
 * <p>
 * Every read takes the current time and returns only rows that have not expired yet, so the result never depends on
 * when expired rows are cleaned up. Times are bound from Java, never taken from the database clock. All futures
 * complete on the executor given to the constructor.
 */
public abstract class AMembershipData {
    private final ExecutorService executor;

    protected AMembershipData(ExecutorService executor) {
        this.executor = executor;
    }

    /**
     * Stores an invite. An invite of the same company to the same player is replaced, which renews it.
     *
     * @param company id of the inviting company
     * @param target  invited player
     * @param inviter member who sends the invite
     * @param created current time
     * @param expires when the invite expires
     * @return true when the invite was stored
     */
    public CompletableFuture<Boolean> submitInvite(
            int company, UUID target, UUID inviter, LocalDateTime created, LocalDateTime expires) {
        return CompletableFuture.supplyAsync(() -> saveInvite(company, target, inviter, created, expires), executor);
    }

    protected abstract boolean saveInvite(
            int company, UUID target, UUID inviter, LocalDateTime created, LocalDateTime expires);

    /**
     * The open invites a player received, oldest first.
     *
     * @param target invited player
     * @param now    current time
     * @return invites that have not expired
     */
    public CompletableFuture<List<Invite>> retrieveInvitesOf(UUID target, LocalDateTime now) {
        return CompletableFuture.supplyAsync(() -> getInvitesOf(target, now), executor);
    }

    protected abstract List<Invite> getInvitesOf(UUID target, LocalDateTime now);

    /**
     * The open invites a company sent, oldest first.
     *
     * @param company id of the company
     * @param now     current time
     * @return invites that have not expired
     */
    public CompletableFuture<List<Invite>> retrieveInvitesBy(int company, LocalDateTime now) {
        return CompletableFuture.supplyAsync(() -> getInvitesBy(company, now), executor);
    }

    protected abstract List<Invite> getInvitesBy(int company, LocalDateTime now);

    /**
     * The open invite of a company to a player.
     *
     * @param company id of the company
     * @param target  invited player
     * @param now     current time
     * @return the invite, or empty when there is none or it expired
     */
    public CompletableFuture<Optional<Invite>> retrieveInvite(int company, UUID target, LocalDateTime now) {
        return CompletableFuture.supplyAsync(() -> getInvite(company, target, now), executor);
    }

    protected abstract Optional<Invite> getInvite(int company, UUID target, LocalDateTime now);

    /**
     * Removes an invite.
     *
     * @param invite id of the invite
     * @return true when this call removed it, false when it was already gone
     */
    public CompletableFuture<Boolean> submitInviteRemoval(int invite) {
        return CompletableFuture.supplyAsync(() -> removeInvite(invite), executor);
    }

    protected abstract boolean removeInvite(int invite);

    /**
     * Stores an application unless the player already has one open to this company or has reached the limit of open
     * applications. An expired application of the player to this company is replaced.
     *
     * @param company    id of the company
     * @param applicant  applying player
     * @param message    message of the applicant
     * @param created    current time
     * @param expires    when the application expires
     * @param maxPending maximum number of open applications per player
     * @return the outcome
     */
    public CompletableFuture<ApplyResult> submitApplication(
            int company, UUID applicant, String message, LocalDateTime created, LocalDateTime expires, int maxPending) {
        return CompletableFuture.supplyAsync(
                () -> saveApplication(company, applicant, message, created, expires, maxPending), executor);
    }

    protected abstract ApplyResult saveApplication(
            int company, UUID applicant, String message, LocalDateTime created, LocalDateTime expires, int maxPending);

    /**
     * The open applications to a company, oldest first.
     *
     * @param company id of the company
     * @param now     current time
     * @return applications that have not expired
     */
    public CompletableFuture<List<Application>> retrieveApplicationsTo(int company, LocalDateTime now) {
        return CompletableFuture.supplyAsync(() -> getApplicationsTo(company, now), executor);
    }

    protected abstract List<Application> getApplicationsTo(int company, LocalDateTime now);

    /**
     * The open applications of a player, oldest first.
     *
     * @param applicant applying player
     * @param now       current time
     * @return applications that have not expired
     */
    public CompletableFuture<List<Application>> retrieveApplicationsOf(UUID applicant, LocalDateTime now) {
        return CompletableFuture.supplyAsync(() -> getApplicationsOf(applicant, now), executor);
    }

    protected abstract List<Application> getApplicationsOf(UUID applicant, LocalDateTime now);

    /**
     * The open application of a player to a company.
     *
     * @param company   id of the company
     * @param applicant applying player
     * @param now       current time
     * @return the application, or empty when there is none or it expired
     */
    public CompletableFuture<Optional<Application>> retrieveApplication(
            int company, UUID applicant, LocalDateTime now) {
        return CompletableFuture.supplyAsync(() -> getApplication(company, applicant, now), executor);
    }

    protected abstract Optional<Application> getApplication(int company, UUID applicant, LocalDateTime now);

    /**
     * Removes an application.
     *
     * @param application id of the application
     * @return true when this call removed it, false when it was already gone
     */
    public CompletableFuture<Boolean> submitApplicationRemoval(int application) {
        return CompletableFuture.supplyAsync(() -> removeApplication(application), executor);
    }

    protected abstract boolean removeApplication(int application);

    /**
     * Adds a player to a company in one transaction, safe against concurrent joins on other servers.
     * <p>
     * The company row is locked first, so two joins into the same company run one after the other and cannot exceed
     * the member limit. The player is inserted as a new member, never moved: a player who is a member anywhere stays
     * where they are. On success every invite and application of the player is removed.
     *
     * @param company    id of the company
     * @param player     joining player
     * @param maxMembers member limit of the company
     * @return the outcome
     */
    public CompletableFuture<JoinResult> submitJoin(int company, UUID player, int maxMembers) {
        return CompletableFuture.supplyAsync(() -> join(company, player, maxMembers), executor);
    }

    protected abstract JoinResult join(int company, UUID player, int maxMembers);

    /**
     * Invites that expired and were not removed yet, oldest first.
     *
     * @param now   current time
     * @param limit maximum number of invites
     * @return expired invites
     */
    public CompletableFuture<List<Invite>> retrieveExpiredInvites(LocalDateTime now, int limit) {
        return CompletableFuture.supplyAsync(() -> getExpiredInvites(now, limit), executor);
    }

    protected abstract List<Invite> getExpiredInvites(LocalDateTime now, int limit);

    /**
     * Applications that expired and were not removed yet, oldest first.
     *
     * @param now   current time
     * @param limit maximum number of applications
     * @return expired applications
     */
    public CompletableFuture<List<Application>> retrieveExpiredApplications(LocalDateTime now, int limit) {
        return CompletableFuture.supplyAsync(() -> getExpiredApplications(now, limit), executor);
    }

    protected abstract List<Application> getExpiredApplications(LocalDateTime now, int limit);
}
