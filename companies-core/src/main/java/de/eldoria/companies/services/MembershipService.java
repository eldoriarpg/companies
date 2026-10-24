/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.ICompanyMember;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.components.join.JoinResult;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AMembershipData;
import de.eldoria.companies.data.wrapper.company.Application;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.DetailsResult;
import de.eldoria.companies.data.wrapper.company.Invite;
import de.eldoria.companies.events.company.CompanyApplicationAnswerEvent;
import de.eldoria.companies.events.company.CompanyApplicationEvent;
import de.eldoria.companies.events.company.CompanyApplicationWithdrawEvent;
import de.eldoria.companies.events.company.CompanyInviteAnswerEvent;
import de.eldoria.companies.events.company.CompanyInviteEvent;
import de.eldoria.companies.events.company.CompanyInviteRevokeEvent;
import de.eldoria.companies.events.company.CompanyJoinEvent;
import de.eldoria.companies.events.company.CompanyJoinModeChangeEvent;
import de.eldoria.companies.events.company.CompanyProfileEditEvent;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.util.AsyncEvents;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Invites, applications and joining a company.
 * <p>
 * Everything a player does here is stored in the database, so an invite sent on one server can be answered on another
 * one, also after the player was offline. Every read only returns invites and applications that have not expired;
 * {@link #expire()} merely removes the expired rows and tells the players about them, on the primary server.
 * <p>
 * A player becomes a member only through {@link AMembershipData#submitJoin(int, UUID, int)}, which keeps the member
 * limit and never moves a member of another company. A successful join fires the join event, which also recalculates
 * the company level and announces the new member to the company.
 * <p>
 * Every stored invite, application, answer and join mode change fires its event afterwards, off the main thread.
 * <p>
 * Each action runs on the executor and answers with an {@link Outcome}: the message the acting player sees.
 */
public final class MembershipService {
    /**
     * How many expired rows one cleanup step removes before it looks for more.
     */
    static final int CLEANUP_BATCH = 100;

    private static final long CLEANUP_MINUTES = 5;
    private static final String FAILED = "company.membership.error.failed";
    private static final String COMPANY_NAME = "company_name";
    private static final String PLAYER_NAME = "player_name";

    private final ACompanyData companyData;
    private final AMembershipData membershipData;
    private final Supplier<CompanySettings> settings;
    private final Notifier notifier;
    private final Events events;
    private final Function<UUID, OfflinePlayer> players;
    private final Supplier<LocalDateTime> clock;
    private final Executor executor;
    private final Logger logger;

    /**
     * @param companyData    company repository
     * @param membershipData invite and application repository
     * @param settings       current company settings
     * @param notifier       notifies players, online or later
     * @param events         fires the events of joins, profile changes, invites and applications
     * @param players        looks up a player by id
     * @param clock          the current time
     * @param executor       runs the actions
     * @param logger         logs failed cleanups
     */
    public MembershipService(
            ACompanyData companyData,
            AMembershipData membershipData,
            Supplier<CompanySettings> settings,
            Notifier notifier,
            Events events,
            Function<UUID, OfflinePlayer> players,
            Supplier<LocalDateTime> clock,
            Executor executor,
            Logger logger) {
        this.companyData = companyData;
        this.membershipData = membershipData;
        this.settings = settings;
        this.notifier = notifier;
        this.events = events;
        this.players = players;
        this.clock = clock;
        this.executor = executor;
        this.logger = logger;
    }

    /**
     * Creates the service of the plugin, notifying through the notification service and firing Bukkit events.
     *
     * @param plugin         the plugin
     * @param companyData    company repository
     * @param membershipData invite and application repository
     * @param settings       current company settings
     * @param notifications  notifies players, online or later
     * @param executor       runs the actions
     * @return the service
     */
    public static MembershipService create(
            Plugin plugin,
            ACompanyData companyData,
            AMembershipData membershipData,
            Supplier<CompanySettings> settings,
            NotificationService notifications,
            Executor executor) {
        return new MembershipService(
                companyData,
                membershipData,
                settings,
                notifications::send,
                Events.bukkit(plugin),
                plugin.getServer()::getOfflinePlayer,
                LocalDateTime::now,
                executor,
                plugin.getLogger());
    }

    /**
     * Removes expired invites and applications every five minutes. Only the primary server does this.
     *
     * @param pool pool running the cleanup
     */
    public void start(ScheduledExecutorService pool) {
        pool.scheduleAtFixedRate(this::expireLogged, 1, CLEANUP_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Invites a player into the company of the inviter. A renewed invite to the same player restarts its lifetime.
     *
     * @param inviter       member who invites
     * @param target        invited player
     * @param targetMayJoin whether the target may join companies; only known while they are online
     * @return the outcome
     */
    public CompletableFuture<Outcome> invite(OfflinePlayer inviter, OfflinePlayer target, boolean targetMayJoin) {
        return run(() -> inviteNow(inviter, target, targetMayJoin));
    }

    /**
     * Takes back an invite of the company of a member.
     *
     * @param member member who takes it back
     * @param target invited player
     * @return the outcome
     */
    public CompletableFuture<Outcome> revokeInvite(OfflinePlayer member, UUID target) {
        return run(() -> revokeNow(member, target));
    }

    /**
     * Joins the company that invited the player.
     *
     * @param player  invited player
     * @param company id of the inviting company
     * @return the outcome
     */
    public CompletableFuture<Outcome> acceptInvite(OfflinePlayer player, int company) {
        return run(() -> acceptInviteNow(player, company));
    }

    /**
     * Declines an invite.
     *
     * @param player  invited player
     * @param company id of the inviting company
     * @return the outcome
     */
    public CompletableFuture<Outcome> denyInvite(OfflinePlayer player, int company) {
        return run(() -> denyInviteNow(player, company));
    }

    /**
     * Joins a company that is open to everyone.
     *
     * @param player      joining player
     * @param companyName name of the company
     * @return the outcome
     */
    public CompletableFuture<Outcome> join(OfflinePlayer player, String companyName) {
        return run(() -> joinNow(player, companyName));
    }

    /**
     * Adds a player to a company regardless of its join mode, as plugins do through the API. The member limit applies;
     * the player gets no permissions.
     *
     * @param company id of the company
     * @param player  id of the player
     * @return the result of the join
     */
    public CompletableFuture<JoinResult> addMember(int company, UUID player) {
        return CompletableFuture.supplyAsync(
                () -> companyData
                        .retrieveCompanyById(company)
                        .asFuture()
                        .join()
                        .map(found -> join(players.apply(player), found))
                        .orElse(JoinResult.UNKNOWN_COMPANY),
                executor);
    }

    /**
     * Applies to a company that accepts applications.
     *
     * @param player      applying player
     * @param companyName name of the company
     * @param message     message to the members, on one line
     * @return the outcome
     */
    public CompletableFuture<Outcome> apply(OfflinePlayer player, String companyName, String message) {
        return run(() -> applyNow(player, companyName, message));
    }

    /**
     * Takes back an own application.
     *
     * @param player  applying player
     * @param company id of the company
     * @return the outcome
     */
    public CompletableFuture<Outcome> withdraw(OfflinePlayer player, int company) {
        return run(() -> withdrawNow(player, company));
    }

    /**
     * Accepts an application to the company of a member, which makes the applicant a member.
     *
     * @param member    member who accepts
     * @param applicant applying player
     * @return the outcome
     */
    public CompletableFuture<Outcome> acceptApplication(OfflinePlayer member, UUID applicant) {
        return run(() -> acceptApplicationNow(member, applicant));
    }

    /**
     * Denies an application to the company of a member.
     *
     * @param member    member who denies
     * @param applicant applying player
     * @return the outcome
     */
    public CompletableFuture<Outcome> denyApplication(OfflinePlayer member, UUID applicant) {
        return run(() -> denyApplicationNow(member, applicant));
    }

    /**
     * Changes how players join the company of a member. Open applications stay until they expire and can still be
     * accepted.
     *
     * @param member member who changes it
     * @param mode   new join mode
     * @return the outcome
     */
    public CompletableFuture<Outcome> setJoinMode(OfflinePlayer member, JoinMode mode) {
        return run(() -> setJoinModeNow(member, mode));
    }

    /**
     * The invites a player received, the own applications and, for members who may invite, the invites their company
     * sent.
     *
     * @param player viewing player
     * @return the overview
     */
    public CompletableFuture<InviteOverview> invites(OfflinePlayer player) {
        return CompletableFuture.supplyAsync(() -> invitesNow(player), executor);
    }

    /**
     * The open applications to the company of a member who may invite.
     *
     * @param member viewing member
     * @return the applications, or why they can not be shown
     */
    public CompletableFuture<ApplicationOverview> applications(OfflinePlayer member) {
        return CompletableFuture.supplyAsync(() -> applicationsNow(member), executor);
    }

    /**
     * Removes expired invites and applications and tells the players about them. Only the call that removed a row
     * notifies, so two servers cleaning up at once never notify twice.
     *
     * @return number of removed invites and applications
     */
    public CompletableFuture<Integer> expire() {
        return CompletableFuture.supplyAsync(this::expireNow, executor);
    }

    private Outcome inviteNow(OfflinePlayer inviter, OfflinePlayer target, boolean targetMayJoin) {
        var optProfile = profileOf(inviter);
        if (optProfile.isEmpty()) return Outcome.failure("error.noMember");
        var profile = optProfile.get();
        if (!may(profile, inviter.getUniqueId(), CompanyPermission.INVITE)) {
            return Outcome.failure("error.permission.invite");
        }
        if (inviter.getUniqueId().equals(target.getUniqueId())) return Outcome.failure("company.invite.error.self");
        if (!targetMayJoin) return Outcome.failure("error.canNotJoin");
        if (companyData.retrievePlayerCompany(target).asFuture().join().isPresent()) {
            return Outcome.failure("error.hasCompany");
        }
        if (profile.members().size() >= maxMembers(profile)) return Outcome.failure("error.companyFull");
        var now = clock.get();
        var hours = settings.get().inviteHours();
        var stored = membershipData
                .submitInvite(profile.id(), target.getUniqueId(), inviter.getUniqueId(), now, now.plusHours(hours))
                .join();
        if (!stored) return Outcome.failure(FAILED);
        events.fire(new CompanyInviteEvent(profile, target.getUniqueId(), inviter.getUniqueId(), now.plusHours(hours)));
        notifier.send(
                target.getUniqueId(),
                NotificationType.INVITE_RECEIVED,
                NotificationArgs.create()
                        .text(COMPANY_NAME, profile.name())
                        .text(PLAYER_NAME, name(inviter.getUniqueId())));
        return Outcome.success(
                "company.invite.sent",
                Values.text(PLAYER_NAME, name(target.getUniqueId())),
                Values.number("hours", hours));
    }

    private Outcome revokeNow(OfflinePlayer member, UUID target) {
        var optProfile = profileOf(member);
        if (optProfile.isEmpty()) return Outcome.failure("error.noMember");
        var profile = optProfile.get();
        if (!may(profile, member.getUniqueId(), CompanyPermission.INVITE)) {
            return Outcome.failure("error.permission.invite");
        }
        var notInvited = Outcome.failure("company.invite.error.notInvited", Values.text(PLAYER_NAME, name(target)));
        var invite =
                membershipData.retrieveInvite(profile.id(), target, clock.get()).join();
        if (invite.isEmpty()) return notInvited;
        if (!membershipData.submitInviteRemoval(invite.get().id()).join()) return notInvited;
        events.fire(new CompanyInviteRevokeEvent(profile, target, member.getUniqueId()));
        notifier.send(
                target,
                NotificationType.INVITE_REVOKED,
                NotificationArgs.create().text(COMPANY_NAME, profile.name()));
        return Outcome.success("company.invite.revoked", Values.text(PLAYER_NAME, name(target)));
    }

    private Outcome acceptInviteNow(OfflinePlayer player, int company) {
        var invite = membershipData
                .retrieveInvite(company, player.getUniqueId(), clock.get())
                .join();
        if (invite.isEmpty()) return Outcome.failure("company.invite.error.noPending");
        var outcome = joinCompany(player, company, "error.alreadyMember");
        if (outcome.success()) {
            companyProfile(company)
                    .ifPresent(profile -> events.fire(new CompanyInviteAnswerEvent(
                            profile, player.getUniqueId(), invite.get().inviter(), true)));
            notifier.send(
                    invite.get().inviter(),
                    NotificationType.INVITE_ACCEPTED,
                    NotificationArgs.create().text(PLAYER_NAME, name(player.getUniqueId())));
        }
        return outcome;
    }

    private Outcome denyInviteNow(OfflinePlayer player, int company) {
        var noPending = Outcome.failure("company.invite.error.noPending");
        var invite = membershipData
                .retrieveInvite(company, player.getUniqueId(), clock.get())
                .join();
        if (invite.isEmpty()) return noPending;
        if (!membershipData.submitInviteRemoval(invite.get().id()).join()) return noPending;
        companyProfile(company)
                .ifPresent(profile -> events.fire(new CompanyInviteAnswerEvent(
                        profile, player.getUniqueId(), invite.get().inviter(), false)));
        notifier.send(
                invite.get().inviter(),
                NotificationType.INVITE_DECLINED,
                NotificationArgs.create().text(PLAYER_NAME, name(player.getUniqueId())));
        return Outcome.success(
                "company.invite.declined",
                Values.text(COMPANY_NAME, invite.get().companyName()));
    }

    private Outcome joinNow(OfflinePlayer player, String companyName) {
        var company = companyData.retrieveCompanyByName(companyName).asFuture().join();
        if (company.isEmpty()) return Outcome.failure("error.unknownCompany");
        if (company.get().joinMode() != JoinMode.OPEN) {
            return Outcome.failure(
                    "company.join.error.notOpen",
                    Values.text(COMPANY_NAME, company.get().name()));
        }
        return joinCompany(player, company.get().id(), "error.alreadyMember");
    }

    private Outcome applyNow(OfflinePlayer player, String companyName, String message) {
        var text = singleLine(message);
        if (text.isEmpty()) return Outcome.failure("company.apply.error.empty");
        var joining = settings.get().joining();
        if (text.length() > joining.maxApplicationLength()) {
            return Outcome.failure(
                    "company.apply.error.tooLong", Values.number("max_length", joining.maxApplicationLength()));
        }
        var optCompany =
                companyData.retrieveCompanyByName(companyName).asFuture().join();
        if (optCompany.isEmpty()) return Outcome.failure("error.unknownCompany");
        var company = optCompany.get();
        var named = Values.text(COMPANY_NAME, company.name());
        if (company.joinMode() != JoinMode.APPLICATIONS) {
            return Outcome.failure("company.apply.error.notAccepting", named);
        }
        if (companyData.retrievePlayerCompany(player).asFuture().join().isPresent()) {
            return Outcome.failure("error.alreadyMember");
        }
        var now = clock.get();
        var result = membershipData
                .submitApplication(
                        company.id(),
                        player.getUniqueId(),
                        text,
                        now,
                        now.plusHours(settings.get().inviteHours()),
                        joining.maxPendingApplications())
                .join();
        return switch (result) {
            case CREATED -> {
                companyData.retrieveCompanyProfile(company).asFuture().join().ifPresent(profile -> {
                    events.fire(new CompanyApplicationEvent(profile, player.getUniqueId(), text));
                    notifyRecruiters(profile, player);
                });
                yield Outcome.success("company.apply.sent", named);
            }
            case ALREADY_PENDING -> Outcome.failure("company.apply.error.alreadyPending", named);
            case TOO_MANY ->
                Outcome.failure(
                        "company.apply.error.tooMany", Values.number("max_pending", joining.maxPendingApplications()));
            case FAILED -> Outcome.failure(FAILED);
        };
    }

    private void notifyRecruiters(CompanyProfile profile, OfflinePlayer applicant) {
        var args = NotificationArgs.create().text(PLAYER_NAME, name(applicant.getUniqueId()));
        profile.members().stream()
                .filter(member -> member.hasPermission(CompanyPermission.INVITE))
                .forEach(member -> notifier.send(member.uuid(), NotificationType.APPLICATION_RECEIVED, args));
    }

    private Outcome withdrawNow(OfflinePlayer player, int company) {
        var noPending = Outcome.failure("company.apply.error.noPending");
        var application = membershipData
                .retrieveApplication(company, player.getUniqueId(), clock.get())
                .join();
        if (application.isEmpty()) return noPending;
        if (!membershipData.submitApplicationRemoval(application.get().id()).join()) return noPending;
        companyProfile(company)
                .ifPresent(profile -> events.fire(new CompanyApplicationWithdrawEvent(profile, player.getUniqueId())));
        return Outcome.success(
                "company.apply.withdrawn",
                Values.text(COMPANY_NAME, application.get().companyName()));
    }

    private Outcome acceptApplicationNow(OfflinePlayer member, UUID applicant) {
        var optProfile = profileOf(member);
        if (optProfile.isEmpty()) return Outcome.failure("error.noMember");
        var profile = optProfile.get();
        if (!may(profile, member.getUniqueId(), CompanyPermission.INVITE)) {
            return Outcome.failure("error.permission.invite");
        }
        var application = membershipData
                .retrieveApplication(profile.id(), applicant, clock.get())
                .join();
        if (application.isEmpty()) return noApplication(applicant);
        var joined = joinCompany(players.apply(applicant), profile.id(), "error.hasCompany");
        if (!joined.success()) return joined;
        companyProfile(profile.id())
                .ifPresent(company ->
                        events.fire(new CompanyApplicationAnswerEvent(company, applicant, member.getUniqueId(), true)));
        notifier.send(
                applicant,
                NotificationType.APPLICATION_ACCEPTED,
                NotificationArgs.create().text(COMPANY_NAME, profile.name()));
        return Outcome.success("company.apply.accepted", Values.text(PLAYER_NAME, name(applicant)));
    }

    private Outcome denyApplicationNow(OfflinePlayer member, UUID applicant) {
        var optProfile = profileOf(member);
        if (optProfile.isEmpty()) return Outcome.failure("error.noMember");
        var profile = optProfile.get();
        if (!may(profile, member.getUniqueId(), CompanyPermission.INVITE)) {
            return Outcome.failure("error.permission.invite");
        }
        var application = membershipData
                .retrieveApplication(profile.id(), applicant, clock.get())
                .join();
        if (application.isEmpty()) return noApplication(applicant);
        if (!membershipData.submitApplicationRemoval(application.get().id()).join()) return noApplication(applicant);
        events.fire(new CompanyApplicationAnswerEvent(profile, applicant, member.getUniqueId(), false));
        notifier.send(
                applicant,
                NotificationType.APPLICATION_DENIED,
                NotificationArgs.create().text(COMPANY_NAME, profile.name()));
        return Outcome.success("company.apply.denied", Values.text(PLAYER_NAME, name(applicant)));
    }

    private Outcome noApplication(UUID applicant) {
        return Outcome.failure("company.applications.error.noPending", Values.text(PLAYER_NAME, name(applicant)));
    }

    private Outcome setJoinModeNow(OfflinePlayer member, JoinMode mode) {
        var optProfile = profileOf(member);
        if (optProfile.isEmpty()) return Outcome.failure("error.noMember");
        var profile = optProfile.get();
        if (!may(profile, member.getUniqueId(), CompanyPermission.EDIT_PROFILE)) {
            return Outcome.failure("company.joinMode.error.permission");
        }
        var details = profile.details().withJoinMode(mode);
        var result = companyData.submitDetails(profile, details).asFuture().join();
        if (result == DetailsResult.UNKNOWN_COMPANY) return Outcome.failure("error.unknownCompany");
        if (result != DetailsResult.SAVED) return Outcome.failure(FAILED);
        var changed = profile.withDetails(details);
        events.profileEdited(profile, changed, member);
        events.fire(new CompanyJoinModeChangeEvent(changed, profile.joinMode(), member.getUniqueId()));
        var args = NotificationArgs.create().text(PLAYER_NAME, name(member.getUniqueId()));
        profile.members().stream()
                .map(ICompanyMember::uuid)
                .filter(uuid -> !uuid.equals(member.getUniqueId()))
                .forEach(uuid -> notifier.send(uuid, NotificationType.PROFILE_CHANGED, args));
        var text = MenuText.create()
                .locale("company.joinMode.changed")
                .text(" <value><i18n:" + mode.translationKey() + ">");
        return Outcome.success("company.joinMode.changed", text);
    }

    private InviteOverview invitesNow(OfflinePlayer player) {
        var now = clock.get();
        var received =
                membershipData.retrieveInvitesOf(player.getUniqueId(), now).join();
        var applications =
                membershipData.retrieveApplicationsOf(player.getUniqueId(), now).join();
        var sent = profileOf(player)
                .filter(profile -> may(profile, player.getUniqueId(), CompanyPermission.INVITE))
                .map(profile ->
                        membershipData.retrieveInvitesBy(profile.id(), now).join())
                .orElse(List.of());
        Map<UUID, String> names = new HashMap<>();
        received.forEach(invite -> names.computeIfAbsent(invite.inviter(), this::name));
        sent.forEach(invite -> names.computeIfAbsent(invite.target(), this::name));
        return new InviteOverview(received, sent, applications, names, now);
    }

    private ApplicationOverview applicationsNow(OfflinePlayer member) {
        var now = clock.get();
        var optProfile = profileOf(member);
        if (optProfile.isEmpty()) return ApplicationOverview.refused(Outcome.failure("error.noMember"), now);
        var profile = optProfile.get();
        if (!may(profile, member.getUniqueId(), CompanyPermission.INVITE)) {
            return ApplicationOverview.refused(Outcome.failure("error.permission.invite"), now);
        }
        var applications =
                membershipData.retrieveApplicationsTo(profile.id(), now).join();
        Map<UUID, String> names = new HashMap<>();
        applications.forEach(application -> names.computeIfAbsent(application.applicant(), this::name));
        return new ApplicationOverview(Optional.empty(), profile.name(), applications, names, now);
    }

    private int expireNow() {
        var now = clock.get();
        return expireInvites(now) + expireApplications(now);
    }

    private int expireInvites(LocalDateTime now) {
        var removed = 0;
        while (true) {
            var batch =
                    membershipData.retrieveExpiredInvites(now, CLEANUP_BATCH).join();
            var step = 0;
            for (var invite : batch) {
                if (!membershipData.submitInviteRemoval(invite.id()).join()) continue;
                step++;
                notifier.send(
                        invite.target(),
                        NotificationType.INVITE_EXPIRED_TARGET,
                        NotificationArgs.create().text(COMPANY_NAME, invite.companyName()));
                notifier.send(
                        invite.inviter(),
                        NotificationType.INVITE_EXPIRED_INVITER,
                        NotificationArgs.create().text(PLAYER_NAME, name(invite.target())));
            }
            removed += step;
            if (batch.size() < CLEANUP_BATCH || step == 0) return removed;
        }
    }

    private int expireApplications(LocalDateTime now) {
        var removed = 0;
        while (true) {
            var batch = membershipData
                    .retrieveExpiredApplications(now, CLEANUP_BATCH)
                    .join();
            var step = 0;
            for (var application : batch) {
                if (!membershipData.submitApplicationRemoval(application.id()).join()) continue;
                step++;
                notifier.send(
                        application.applicant(),
                        NotificationType.APPLICATION_EXPIRED,
                        NotificationArgs.create().text(COMPANY_NAME, application.companyName()));
            }
            removed += step;
            if (batch.size() < CLEANUP_BATCH || step == 0) return removed;
        }
    }

    private void expireLogged() {
        try {
            expireNow();
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not remove expired invites and applications", e);
        }
    }

    private Outcome joinCompany(OfflinePlayer player, int companyId, String alreadyMemberKey) {
        var optCompany = companyData.retrieveCompanyById(companyId).asFuture().join();
        if (optCompany.isEmpty()) return Outcome.failure("error.unknownCompany");
        var company = optCompany.get();
        var result = join(player, company);
        if (result != JoinResult.JOINED) return refusedJoin(result, alreadyMemberKey);
        return Outcome.success("company.join.joined", Values.text(COMPANY_NAME, company.name()));
    }

    private JoinResult join(OfflinePlayer player, ISimpleCompany company) {
        var result = membershipData
                .submitJoin(company.id(), player.getUniqueId(), maxMembers(company))
                .join();
        if (result != JoinResult.JOINED) return result;
        companyData
                .retrieveCompanyProfile(company)
                .asFuture()
                .join()
                .ifPresent(profile -> events.joined(profile, player));
        return result;
    }

    private static Outcome refusedJoin(JoinResult result, String alreadyMemberKey) {
        return switch (result) {
            case ALREADY_MEMBER -> Outcome.failure(alreadyMemberKey);
            case COMPANY_FULL -> Outcome.failure("error.companyFull");
            case UNKNOWN_COMPANY -> Outcome.failure("error.unknownCompany");
            case JOINED, FAILED -> Outcome.failure(FAILED);
        };
    }

    private Optional<CompanyProfile> companyProfile(int company) {
        return companyData.retrieveCompanyById(company).asFuture().join().flatMap(found -> companyData
                .retrieveCompanyProfile(found)
                .asFuture()
                .join());
    }

    private Optional<CompanyProfile> profileOf(OfflinePlayer player) {
        return companyData.retrievePlayerCompanyProfile(player).asFuture().join();
    }

    private int maxMembers(ISimpleCompany company) {
        return settings.get()
                .level(company.level())
                .orElse(CompanyLevel.DEFAULT)
                .settings()
                .maxMembers();
    }

    private String name(UUID player) {
        return Optional.ofNullable(players.apply(player).getName()).orElse(player.toString());
    }

    private CompletableFuture<Outcome> run(Supplier<Outcome> action) {
        return CompletableFuture.supplyAsync(action, executor);
    }

    /**
     * Whether a member of a company holds a permission. The owner holds every permission.
     *
     * @param profile    the company
     * @param player     id of the player
     * @param permission permission to check
     * @return false when the player is no member or lacks the permission
     */
    static boolean may(CompanyProfile profile, UUID player, CompanyPermission permission) {
        return profile.members().stream()
                .filter(member -> member.uuid().equals(player))
                .anyMatch(member -> member.hasPermission(permission));
    }

    /**
     * Puts an application message on one line: line breaks and other control characters become spaces, runs of
     * spaces are collapsed and the ends are trimmed.
     *
     * @param message message as typed
     * @return the message on one line
     */
    static String singleLine(String message) {
        return message.replaceAll("\\p{Cntrl}", " ").replaceAll(" {2,}", " ").trim();
    }

    /**
     * Notifies a player, online now or on their next join.
     */
    @FunctionalInterface
    public interface Notifier {
        /**
         * @param recipient player to notify
         * @param type      kind of notification
         * @param args      values of the notification
         */
        void send(UUID recipient, NotificationType type, NotificationArgs args);
    }

    /**
     * Fires the events of joins, profile changes, invites, applications and join mode changes.
     */
    public interface Events {
        /**
         * Fires the events as Bukkit events of the plugin.
         *
         * @param plugin the plugin
         * @return the events
         */
        static Events bukkit(Plugin plugin) {
            var manager = plugin.getServer().getPluginManager();
            return new Events() {
                @Override
                public void joined(CompanyProfile company, OfflinePlayer player) {
                    manager.callEvent(new CompanyJoinEvent(company, player));
                }

                @Override
                public void profileEdited(CompanyProfile previous, CompanyProfile company, OfflinePlayer player) {
                    manager.callEvent(new CompanyProfileEditEvent(previous, company, player));
                }

                @Override
                public void fire(Event event) {
                    AsyncEvents.fire(plugin, event);
                }
            };
        }

        /**
         * A player joined a company.
         *
         * @param company the company, with the new member
         * @param player  the new member
         */
        void joined(CompanyProfile company, OfflinePlayer player);

        /**
         * A member changed the profile of the company.
         *
         * @param previous the company before the change
         * @param company  the company after the change
         * @param player   the member
         */
        void profileEdited(CompanyProfile previous, CompanyProfile company, OfflinePlayer player);

        /**
         * Fires an asynchronous event of an invite, an application or a join mode change, off the main thread.
         *
         * @param event the event
         */
        void fire(Event event);
    }

    /**
     * What the acting player is told about an action.
     *
     * @param success whether the action was done
     * @param key     locale key of the message, naming the outcome
     * @param text    the message
     */
    public record Outcome(boolean success, String key, MenuText text) {
        /**
         * @param key    locale key of the message
         * @param values values of the placeholders in the message
         * @return an outcome of a done action
         */
        public static Outcome success(String key, TagResolver.Single... values) {
            return new Outcome(true, key, MenuText.create().locale(key, values));
        }

        /**
         * @param key  locale key naming the outcome
         * @param text the message
         * @return an outcome of a done action
         */
        public static Outcome success(String key, MenuText text) {
            return new Outcome(true, key, text);
        }

        /**
         * @param key    locale key of the error
         * @param values values of the placeholders in the error
         * @return an outcome of a refused action
         */
        public static Outcome failure(String key, TagResolver.Single... values) {
            return new Outcome(false, key, MenuText.create().locale(key, values));
        }

        /**
         * Sends the message: in chat when the action was done, as an error in the action bar otherwise.
         *
         * @param player   acting player
         * @param messages message sender of the plugin
         */
        public void sendTo(Player player, MessageSender messages) {
            var resolvers = text.replacements().toArray(TagResolver[]::new);
            if (success) {
                messages.sendMessage(player, text.build(), resolvers);
                return;
            }
            messages.sendErrorActionBar(player, text.build(), resolvers);
        }
    }

    /**
     * The invites and applications of a player.
     *
     * @param received     open invites the player received
     * @param sent         open invites of the player's company, empty unless the player may invite
     * @param applications open applications of the player
     * @param names        names of the inviters and invited players, by id
     * @param now          when the overview was read
     */
    public record InviteOverview(
            List<Invite> received,
            List<Invite> sent,
            List<Application> applications,
            Map<UUID, String> names,
            LocalDateTime now) {
        /**
         * @param received     open invites the player received
         * @param sent         open invites of the player's company
         * @param applications open applications of the player
         * @param names        names of the inviters and invited players, by id
         * @param now          when the overview was read
         */
        public InviteOverview {
            received = List.copyOf(received);
            sent = List.copyOf(sent);
            applications = List.copyOf(applications);
            names = Map.copyOf(names);
        }

        /**
         * @param player id of a player in this overview
         * @return the name of the player
         */
        public String name(UUID player) {
            return names.getOrDefault(player, player.toString());
        }
    }

    /**
     * The open applications to a company.
     *
     * @param refusal      why the applications are not shown, empty when they are
     * @param companyName  name of the company
     * @param applications open applications, oldest first
     * @param names        names of the applicants, by id
     * @param now          when the overview was read
     */
    public record ApplicationOverview(
            Optional<Outcome> refusal,
            String companyName,
            List<Application> applications,
            Map<UUID, String> names,
            LocalDateTime now) {
        /**
         * @param refusal      why the applications are not shown
         * @param companyName  name of the company
         * @param applications open applications
         * @param names        names of the applicants, by id
         * @param now          when the overview was read
         */
        public ApplicationOverview {
            applications = List.copyOf(applications);
            names = Map.copyOf(names);
        }

        static ApplicationOverview refused(Outcome refusal, LocalDateTime now) {
            return new ApplicationOverview(Optional.of(refusal), "", List.of(), Map.of(), now);
        }

        /**
         * @param player id of an applicant
         * @return the name of the applicant
         */
        public String name(UUID player) {
            return names.getOrDefault(player, player.toString());
        }
    }
}
