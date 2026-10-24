/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.ICompanyMember;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.company.CompanyKickEvent;
import de.eldoria.companies.events.company.CompanyLeaveEvent;
import de.eldoria.companies.events.company.CompanyMemberPermissionEvent;
import de.eldoria.companies.menus.company.PermissionEdit;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.util.AsyncEvents;
import de.eldoria.companies.util.Threading;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Kicking members, leaving a company, editing the permissions of members and handing the company to another member.
 * <p>
 * The actor is given by id and does not need to be online. Every action reads the company of the actor again, so a
 * confirmation older than the company's state can not bypass the checks, and runs on the executor, off the main
 * thread. The answer is an {@link ActionResult}; the error of a refusal names its reason by its locale key.
 * <p>
 * Kicks and leaves fire {@link CompanyKickEvent} and {@link CompanyLeaveEvent} once the member was removed, a permission
 * edit that changed something fires {@link CompanyMemberPermissionEvent} once it was saved. A transfer fires its event
 * through {@link CompanyChanges} and notifies the new owner.
 */
public final class MemberService {
    /**
     * Refusal of an owner leaving a company that has other members: they have to hand it on first.
     */
    public static final String LEAVE_TRANSFER_FIRST = "company.leave.transferFirst";

    /**
     * Refusal of an owner leaving a company they are alone in: they have to disband it instead.
     */
    public static final String LEAVE_OWNER_ALONE = "company.leave.ownerAlone";

    /**
     * Refusal of a permission edit naming a member who is not in the company.
     */
    public static final String INVALID_MEMBER = "error.invalidMember";

    private static final String NO_MEMBER = "error.noMember";

    private final ACompanyData companyData;
    private final CompanyChanges changes;
    private final Notifier notifier;
    private final Consumer<Event> events;
    private final Function<UUID, OfflinePlayer> players;
    private final Executor executor;

    /**
     * @param companyData company repository
     * @param changes     fires the event of a transfer
     * @param notifier    notifies the new owner of a transfer
     * @param events      fires the events of kicks, leaves and permission edits, off the main thread
     * @param players     looks up a player by id
     * @param executor    runs the actions
     */
    public MemberService(
            ACompanyData companyData,
            CompanyChanges changes,
            Notifier notifier,
            Consumer<Event> events,
            Function<UUID, OfflinePlayer> players,
            Executor executor) {
        this.companyData = companyData;
        this.changes = changes;
        this.notifier = notifier;
        this.events = events;
        this.players = players;
        this.executor = executor;
    }

    /**
     * The service of a running plugin, notifying through the notification service and firing Bukkit events.
     *
     * @param plugin        the plugin
     * @param companyData   company repository
     * @param notifications notifies players, online or later
     * @return the service
     */
    public static MemberService create(Plugin plugin, ACompanyData companyData, NotificationService notifications) {
        return new MemberService(
                companyData,
                CompanyChanges.create(plugin, companyData),
                notifications::send,
                event -> AsyncEvents.fire(plugin, event),
                plugin.getServer()::getOfflinePlayer,
                Threading.VIRTUAL);
    }

    /**
     * Decides whether one member may kick another. The owner kicks anyone, a member with
     * {@link CompanyPermission#KICK} kicks members without it, and nobody kicks the owner.
     *
     * @param kicker member who wants to kick
     * @param target member who would be kicked
     * @return true when the kick is allowed
     */
    public static boolean mayKick(ICompanyMember kicker, ICompanyMember target) {
        if (target.isOwner()) return false;
        if (kicker.isOwner()) return true;
        return kicker.hasPermission(CompanyPermission.KICK) && !target.hasPermission(CompanyPermission.KICK);
    }

    /**
     * Checks whether the actor may kick a member, without kicking.
     *
     * @param actor  member who wants to kick
     * @param target member who would be kicked
     * @return the member who would be kicked, or why the kick is refused
     */
    public CompletableFuture<ActionResult<CompanyMember>> kickable(UUID actor, MemberRef target) {
        return run(() -> profileOf(actor)
                .map(profile -> checkKick(profile, actor, target))
                .orElseGet(MemberService::noMember));
    }

    /**
     * Removes a member from the company of the actor and fires {@link CompanyKickEvent}.
     *
     * @param actor  member who kicks
     * @param target id of the member to kick
     * @return the company before the kick and the kicked member, or why the kick is refused
     */
    public CompletableFuture<ActionResult<Affected>> kick(UUID actor, UUID target) {
        return run(() -> {
            var optProfile = profileOf(actor);
            if (optProfile.isEmpty()) return noMember();
            var profile = optProfile.get();
            return switch (checkKick(profile, actor, MemberRef.byId(target))) {
                case ActionResult.Refused<CompanyMember> refused -> ActionResult.refused(refused.failure());
                case ActionResult.Done<CompanyMember> done -> {
                    var member = done.value();
                    companyData.submitMemberUpdate(member.kick()).join();
                    events.accept(new CompanyKickEvent(profile, players.apply(member.uuid())));
                    yield ActionResult.done(new Affected(profile, member));
                }
            };
        });
    }

    private ActionResult<CompanyMember> checkKick(CompanyProfile profile, UUID actor, MemberRef target) {
        var kicker = profile.member(actor);
        if (kicker.isEmpty()) return noMember();
        if (!kicker.get().hasPermission(CompanyPermission.KICK)) {
            return ActionResult.refusedActionBar("error.permission.kick");
        }
        var optTarget = find(profile, target);
        if (optTarget.isEmpty()) return ActionResult.refusedActionBar("error.noCompanyMember");
        if (!mayKick(kicker.get(), optTarget.get())) return ActionResult.refusedActionBar("error.cantKick");
        return ActionResult.done(optTarget.get());
    }

    /**
     * Checks whether the actor may leave their company, without leaving. An owner is refused with
     * {@link #LEAVE_TRANSFER_FIRST} or {@link #LEAVE_OWNER_ALONE}.
     *
     * @param actor member who wants to leave
     * @return the company of the actor, or why they may not leave
     */
    public CompletableFuture<ActionResult<CompanyProfile>> leavable(UUID actor) {
        return run(() ->
                profileOf(actor).map(profile -> checkLeave(profile, actor)).orElseGet(MemberService::noMember));
    }

    /**
     * Removes the actor from their company and fires {@link CompanyLeaveEvent}.
     *
     * @param actor   member who leaves
     * @param company id of the company the actor wants to leave; another company of the actor is refused
     * @return the company before the actor left, or why they may not leave
     */
    public CompletableFuture<ActionResult<CompanyProfile>> leave(UUID actor, int company) {
        return run(() -> {
            var optProfile = profileOf(actor);
            if (optProfile.isEmpty() || optProfile.get().id() != company) return noMember();
            var profile = optProfile.get();
            var checked = checkLeave(profile, actor);
            if (checked instanceof ActionResult.Refused<CompanyProfile>) return checked;
            companyData
                    .submitMemberUpdate(profile.member(actor).orElseThrow().kick())
                    .join();
            events.accept(new CompanyLeaveEvent(profile, players.apply(actor)));
            return checked;
        });
    }

    private static ActionResult<CompanyProfile> checkLeave(CompanyProfile profile, UUID actor) {
        var member = profile.member(actor);
        if (member.isEmpty()) return noMember();
        return switch (Ownership.leaveCheck(member.get(), profile.members().size())) {
            case ALLOWED -> ActionResult.done(profile);
            case TRANSFER_FIRST -> ActionResult.refusedActionBar(LEAVE_TRANSFER_FIRST);
            case DISBAND_INSTEAD -> ActionResult.refusedActionBar(LEAVE_OWNER_ALONE);
        };
    }

    /**
     * The permissions of a member, for an actor with {@link CompanyPermission#MANAGE_PERMISSIONS}.
     *
     * @param actor  member who views the permissions
     * @param target member whose permissions are shown
     * @return the permissions and which of them the actor may change, or why they are not shown
     */
    public CompletableFuture<ActionResult<PermissionEdit>> permissions(UUID actor, MemberRef target) {
        return run(() -> profileOf(actor)
                .map(profile -> permissionEdit(profile, actor, target, "error.permission.managePermissions"))
                .orElseGet(MemberService::noMember));
    }

    /**
     * Grants and revokes permissions of a member. The actor needs {@link CompanyPermission#MANAGE_PERMISSIONS} and may
     * only change permissions they hold themselves; {@link CompanyPermission#OWNER} is never changed here. Nothing is
     * changed when one of the wanted changes is not allowed. A saved change fires {@link CompanyMemberPermissionEvent}.
     *
     * @param actor  member who changes the permissions
     * @param target member whose permissions change
     * @param wanted whether each permission should be held
     * @return the permissions after the change, or why nothing was changed
     */
    public CompletableFuture<ActionResult<PermissionEdit>> setPermissions(
            UUID actor, MemberRef target, Map<CompanyPermission, Boolean> wanted) {
        if (wanted.containsKey(CompanyPermission.OWNER)) {
            return CompletableFuture.completedFuture(ActionResult.refusedActionBar("error.ownerPermission"));
        }
        return run(() -> {
            var optProfile = profileOf(actor);
            if (optProfile.isEmpty()) return noMember();
            var profile = optProfile.get();
            var checked = permissionEdit(profile, actor, target, "error.permission.givePermissions");
            if (!(checked instanceof ActionResult.Done<PermissionEdit>(var edit))) return checked;
            if (!wanted.keySet().stream().allMatch(edit::editable)) {
                return ActionResult.refusedActionBar("error.permission.givePermissions");
            }
            var previous = Set.copyOf(edit.target().permissions());
            if (!edit.apply(wanted).isEmpty()) {
                companyData.submitMemberUpdate(edit.target()).join();
                events.accept(new CompanyMemberPermissionEvent(profile, edit.target(), actor, previous));
            }
            return checked;
        });
    }

    private ActionResult<PermissionEdit> permissionEdit(
            CompanyProfile profile, UUID actor, MemberRef target, String deniedKey) {
        var editor = profile.member(actor);
        if (editor.isEmpty() || !editor.get().hasPermission(CompanyPermission.MANAGE_PERMISSIONS)) {
            return ActionResult.refusedActionBar(deniedKey);
        }
        return find(profile, target)
                .map(member -> ActionResult.done(new PermissionEdit(editor.get(), member)))
                .orElseGet(() -> ActionResult.refusedActionBar(INVALID_MEMBER));
    }

    /**
     * Checks whether the actor may hand their company to a member, without handing it over.
     *
     * @param actor  owner who wants to hand the company over
     * @param target member who would become the owner
     * @return the company and the member who would become the owner, or why the transfer is refused
     */
    public CompletableFuture<ActionResult<Affected>> transferable(UUID actor, MemberRef target) {
        return run(() -> profileOf(actor)
                .map(profile -> checkTransfer(profile, actor, target))
                .orElseGet(MemberService::noMember));
    }

    /**
     * Hands the company of the actor to another member. The new owner is notified.
     *
     * @param actor   owner who hands the company over
     * @param company id of the company the actor wants to hand over; another company of the actor is refused
     * @param target  id of the member who becomes the owner
     * @return the company before the transfer and the new owner, or why the transfer is refused
     */
    public CompletableFuture<ActionResult<Affected>> transfer(UUID actor, int company, UUID target) {
        return run(() -> {
            var optProfile = profileOf(actor);
            if (optProfile.isEmpty() || optProfile.get().id() != company) return noMember();
            var profile = optProfile.get();
            var checked = checkTransfer(profile, actor, MemberRef.byId(target));
            if (checked instanceof ActionResult.Refused<Affected>) return checked;
            var transferred =
                    companyData.submitOwnerTransfer(profile, actor, target).join();
            if (!Boolean.TRUE.equals(transferred)) return ActionResult.refusedChat("company.transfer.failed");
            changes.ownerTransferred(profile.id(), actor, target, false);
            notifier.send(
                    target,
                    NotificationType.OWNERSHIP_TRANSFERRED,
                    NotificationArgs.create().text("company", profile.name()));
            return checked;
        });
    }

    private ActionResult<Affected> checkTransfer(CompanyProfile profile, UUID actor, MemberRef target) {
        var member = profile.member(actor);
        if (member.isEmpty()) return noMember();
        var optTarget = find(profile, target);
        return switch (Ownership.transferCheck(member.get(), optTarget)) {
            case NOT_OWNER -> ActionResult.refusedActionBar("error.noOwner");
            case NOT_MEMBER -> ActionResult.refusedActionBar("error.noCompanyMember");
            case SELF -> ActionResult.refusedActionBar("company.transfer.self");
            case ALLOWED -> ActionResult.done(new Affected(profile, optTarget.orElseThrow()));
        };
    }

    private Optional<CompanyMember> find(CompanyProfile profile, MemberRef target) {
        var id = target.id();
        if (id != null) return profile.member(id);
        var name = target.name();
        if (name == null) return Optional.empty();
        return profile.members().stream()
                .filter(member ->
                        name.equalsIgnoreCase(players.apply(member.uuid()).getName()))
                .findFirst()
                .flatMap(member -> profile.member(member.uuid()));
    }

    private Optional<CompanyProfile> profileOf(UUID player) {
        return companyData
                .retrievePlayerCompanyProfile(players.apply(player))
                .asFuture()
                .join();
    }

    private static <T> ActionResult<T> noMember() {
        return ActionResult.refusedActionBar(NO_MEMBER);
    }

    private <T> CompletableFuture<ActionResult<T>> run(Supplier<ActionResult<T>> action) {
        return CompletableFuture.supplyAsync(action, executor);
    }

    /**
     * A member of the company of the actor, named by id or by name.
     *
     * @param id   id of the member, null when named by name
     * @param name name of the member, null when named by id
     */
    public record MemberRef(@Nullable UUID id, @Nullable String name) {
        /**
         * @param id id of the member
         * @return the member with the id
         */
        public static MemberRef byId(UUID id) {
            return new MemberRef(id, null);
        }

        /**
         * @param name name of the member, ignoring case
         * @return the member with the name
         */
        public static MemberRef byName(String name) {
            return new MemberRef(null, name);
        }
    }

    /**
     * A member an action is about, with the company as it was before the action.
     *
     * @param company the company before the action
     * @param member  the member: the kicked member, or the new owner of a transfer
     */
    public record Affected(CompanyProfile company, CompanyMember member) {}

    /**
     * Sends a notification to one player.
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
}
