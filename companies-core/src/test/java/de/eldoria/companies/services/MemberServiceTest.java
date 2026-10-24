/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.EventRecorder;
import de.eldoria.companies.events.company.CompanyKickEvent;
import de.eldoria.companies.events.company.CompanyLeaveEvent;
import de.eldoria.companies.events.company.CompanyMemberPermissionEvent;
import de.eldoria.companies.services.MemberService.MemberRef;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.util.TestExecutors;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MemberServiceTest {
    @TempDir
    Path directory;

    private final Map<UUID, OfflinePlayer> players = new HashMap<>();
    private final List<NotificationType> notifications = new CopyOnWriteArrayList<>();
    private final List<UUID> notified = new CopyOnWriteArrayList<>();
    private final EventRecorder events = new EventRecorder();

    private ExecutorService executor;
    private SqLiteCompanyData companyData;
    private MemberService service;

    private OfflinePlayer owner;
    private OfflinePlayer kicker;
    private OfflinePlayer worker;
    private OfflinePlayer outsider;
    private int miners;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
        companyData = new SqLiteCompanyData(executor);
        service = new MemberService(
                companyData,
                new CompanyChanges(companyData, this::player, events, Logger.getLogger("test"), executor),
                (recipient, type, args) -> {
                    notified.add(recipient);
                    notifications.add(type);
                },
                events,
                this::player,
                executor);

        owner = player("Owner");
        kicker = player("Kicker");
        worker = player("Worker");
        outsider = player("Outsider");
        miners = companyData
                .submitCompanyCreation("Miners", JoinMode.INVITE_ONLY)
                .join();
        member(owner, CompanyPermission.OWNER);
        member(kicker, CompanyPermission.KICK, CompanyPermission.MANAGE_PERMISSIONS, CompanyPermission.INVITE);
        member(worker);
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
    }

    private OfflinePlayer player(String name) {
        var uuid = UUID.randomUUID();
        var player = (OfflinePlayer) Proxy.newProxyInstance(
                OfflinePlayer.class.getClassLoader(),
                new Class[] {OfflinePlayer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getName" -> name;
                    case "hashCode" -> uuid.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> name;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        players.put(uuid, player);
        return player;
    }

    private OfflinePlayer player(UUID uuid) {
        return players.get(uuid);
    }

    private void member(OfflinePlayer player, CompanyPermission... permissions) {
        companyData
                .submitMemberUpdate(CompanyMember.of(
                        miners, player.getUniqueId(), CompanyPermission.composePermissions(permissions)))
                .join();
    }

    private CompanyProfile profile() {
        return companyData
                .retrieveCompanyProfile(
                        companyData.retrieveCompanyById(miners).join().orElseThrow())
                .join()
                .orElseThrow();
    }

    private static UUID id(OfflinePlayer player) {
        return player.getUniqueId();
    }

    private static <T> T done(ActionResult<T> result) {
        if (result instanceof ActionResult.Done<T>(var value)) return value;
        return fail("refused with " + refusalKey(result));
    }

    private static String refusalKey(ActionResult<?> result) {
        return result instanceof ActionResult.Refused<?> refused
                ? refused.failure().localeKey()
                : "done";
    }

    private static void assertRefused(String key, ActionResult<?> result) {
        assertEquals(key, refusalKey(result));
    }

    @Test
    void ownerKicksAMemberAndTheKickIsAnnounced() {
        var kicked = done(service.kick(id(owner), id(worker)).join());

        assertEquals(worker.getUniqueId(), kicked.member().uuid());
        assertFalse(profile().isMember(id(worker)));
        var event = events.single(CompanyKickEvent.class);
        assertSame(worker, event.player());
        assertEquals(miners, event.company().id());
    }

    @Test
    void kickingNeedsTheKickPermission() {
        assertRefused(
                "error.permission.kick", service.kick(id(worker), id(kicker)).join());
        assertRefused("error.noMember", service.kick(id(outsider), id(worker)).join());
        assertTrue(profile().isMember(id(kicker)));
        assertTrue(events.of(CompanyKickEvent.class).isEmpty());
    }

    @Test
    void nobodyKicksTheOwnerOrAnEqual() {
        assertRefused("error.cantKick", service.kick(id(kicker), id(owner)).join());
        member(worker, CompanyPermission.KICK);
        assertRefused("error.cantKick", service.kick(id(kicker), id(worker)).join());
        assertRefused(
                "error.noCompanyMember", service.kick(id(kicker), id(outsider)).join());
        assertEquals(3, profile().members().size());
    }

    @Test
    void kickableFindsTheMemberByNameWithoutKicking() {
        var target =
                done(service.kickable(id(kicker), MemberRef.byName("worker")).join());

        assertEquals(id(worker), target.uuid());
        assertTrue(profile().isMember(id(worker)));
    }

    @Test
    void memberLeavesAndTheLeaveIsAnnounced() {
        var left = done(service.leave(id(worker), miners).join());

        assertEquals(miners, left.id());
        assertFalse(profile().isMember(id(worker)));
        assertSame(worker, events.single(CompanyLeaveEvent.class).player());
    }

    @Test
    void ownerMayNotLeave() {
        assertRefused(
                MemberService.LEAVE_TRANSFER_FIRST,
                service.leave(id(owner), miners).join());

        var alone =
                companyData.submitCompanyCreation("Solo", JoinMode.INVITE_ONLY).join();
        companyData
                .submitMemberUpdate(CompanyMember.of(alone, id(outsider), CompanyPermission.OWNER.mask()))
                .join();
        assertRefused(
                MemberService.LEAVE_OWNER_ALONE, service.leavable(id(outsider)).join());
        assertTrue(events.of(CompanyLeaveEvent.class).isEmpty());
    }

    @Test
    void leavingAnotherCompanyIsRefused() {
        assertRefused("error.noMember", service.leave(id(worker), miners + 1).join());
        assertRefused("error.noMember", service.leave(id(outsider), miners).join());
        assertTrue(profile().isMember(id(worker)));
    }

    @Test
    void permissionsAreGrantedAndRevoked() {
        var edit = done(
                service.setPermissions(id(kicker), MemberRef.byId(id(worker)), Map.of(CompanyPermission.INVITE, true))
                        .join());

        assertTrue(edit.granted(CompanyPermission.INVITE));
        assertTrue(profile().member(id(worker)).orElseThrow().hasPermission(CompanyPermission.INVITE));

        done(service.setPermissions(id(kicker), MemberRef.byName("Worker"), Map.of(CompanyPermission.INVITE, false))
                .join());
        assertFalse(profile().member(id(worker)).orElseThrow().hasPermission(CompanyPermission.INVITE));
    }

    @Test
    void permissionEditsNeedManagePermissions() {
        assertRefused(
                "error.permission.givePermissions",
                service.setPermissions(id(worker), MemberRef.byId(id(kicker)), Map.of(CompanyPermission.KICK, false))
                        .join());
        assertRefused(
                "error.permission.managePermissions",
                service.permissions(id(worker), MemberRef.byId(id(kicker))).join());
        assertTrue(profile().member(id(kicker)).orElseThrow().hasPermission(CompanyPermission.KICK));
    }

    @Test
    void editorsOnlyChangePermissionsTheyHold() {
        assertRefused(
                "error.permission.givePermissions",
                service.setPermissions(
                                id(kicker), MemberRef.byId(id(worker)), Map.of(CompanyPermission.MANAGE_ORDERS, true))
                        .join());
        assertRefused(
                "error.ownerPermission",
                service.setPermissions(id(owner), MemberRef.byId(id(worker)), Map.of(CompanyPermission.OWNER, true))
                        .join());
        assertRefused(
                MemberService.INVALID_MEMBER,
                service.permissions(id(kicker), MemberRef.byName("Nobody")).join());
        assertFalse(profile().member(id(worker)).orElseThrow().hasPermission(CompanyPermission.MANAGE_ORDERS));
    }

    @Test
    void savedPermissionEditFiresThePermissionEventOnce() {
        service.setPermissions(id(worker), MemberRef.byId(id(kicker)), Map.of(CompanyPermission.KICK, false))
                .join();
        assertTrue(events.of(CompanyMemberPermissionEvent.class).isEmpty());

        service.setPermissions(id(kicker), MemberRef.byId(id(worker)), Map.of(CompanyPermission.INVITE, true))
                .join();
        service.setPermissions(id(kicker), MemberRef.byId(id(worker)), Map.of(CompanyPermission.INVITE, true))
                .join();

        var event = events.single(CompanyMemberPermissionEvent.class);
        assertEquals(miners, event.company().id());
        assertEquals(id(worker), event.member().uuid());
        assertEquals(id(kicker), event.actor());
        assertEquals(Set.of(), event.previous());
        assertEquals(Set.of(CompanyPermission.INVITE), event.current());
        assertTrue(event.isAsynchronous());
    }

    @Test
    void ownerTransfersTheCompanyAndTheNewOwnerIsNotified() {
        var transfer = done(service.transfer(id(owner), miners, id(worker)).join());

        assertEquals(id(worker), transfer.member().uuid());
        var after = profile();
        assertTrue(after.member(id(worker)).orElseThrow().isOwner());
        assertFalse(after.member(id(owner)).orElseThrow().isOwner());
        assertEquals(List.of(NotificationType.OWNERSHIP_TRANSFERRED), notifications);
        assertEquals(List.of(id(worker)), notified);
    }

    @Test
    void onlyTheOwnerTransfersToAnotherMember() {
        assertRefused(
                "error.noOwner",
                service.transfer(id(kicker), miners, id(worker)).join());
        assertRefused(
                "company.transfer.self",
                service.transfer(id(owner), miners, id(owner)).join());
        assertRefused(
                "error.noCompanyMember",
                service.transfer(id(owner), miners, id(outsider)).join());
        assertRefused(
                "error.noMember",
                service.transfer(id(owner), miners + 1, id(worker)).join());
        assertTrue(profile().member(id(owner)).orElseThrow().isOwner());
        assertTrue(notifications.isEmpty());
    }

    @Test
    void transferableNamesTheMemberWithoutTransferring() {
        var transfer =
                done(service.transferable(id(owner), MemberRef.byName("Kicker")).join());

        assertEquals(id(kicker), transfer.member().uuid());
        assertEquals(miners, transfer.company().id());
        assertTrue(profile().member(id(owner)).orElseThrow().isOwner());
    }
}
