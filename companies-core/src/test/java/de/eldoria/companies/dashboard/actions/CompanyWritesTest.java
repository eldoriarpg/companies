/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.dashboard.DashboardWorld;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.services.TreasuryService;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dashboard's actions on the player's company, against the real services and a SQLite database.
 */
class CompanyWritesTest {
    @TempDir
    Path directory;

    private DashboardWorld world;
    private UUID owner;
    private UUID manager;
    private UUID worker;
    private UUID outsider;
    private int company;

    @BeforeEach
    void setUp() throws Exception {
        world = DashboardWorld.create(directory);
        owner = world.player("Orla");
        manager = world.player("Wren");
        worker = world.player("Moss");
        outsider = world.player("Ivy");
        company = world.company(
                "Miners",
                Map.of(
                        owner,
                        List.of(CompanyPermission.OWNER),
                        manager,
                        List.of(
                                CompanyPermission.KICK,
                                CompanyPermission.INVITE,
                                CompanyPermission.MANAGE_PERMISSIONS,
                                CompanyPermission.EDIT_PROFILE,
                                CompanyPermission.TREASURY),
                        worker,
                        List.of()));
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    private ActionResult perform(UUID player, String action, String args) {
        var session = new StoredSession(
                "session",
                player,
                "player",
                UUID.randomUUID(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                Set.of());
        return world.writes.perform(
                new ActionRequest("act", Protocol.VERSION, session.id(), player, action, json(args)), session);
    }

    private static tools.jackson.databind.JsonNode json(String args) {
        return Protocol.mapper().readTree(args);
    }

    private CompanyProfile profile() {
        return world.companies
                .retrieveCompanyProfile(world.companies
                        .retrieveCompanyById(company)
                        .asFuture()
                        .join()
                        .orElseThrow())
                .asFuture()
                .join()
                .orElseThrow();
    }

    private static String player(UUID player) {
        return "{\"player\":\"" + player + "\"}";
    }

    @Test
    void aMemberWhoMayKickRemovesAMemberWithoutThePermission() {
        var result = perform(manager, Actions.MEMBERS_KICK, player(worker));

        assertTrue(result.ok(), result.reason());
        assertEquals(worker.toString(), result.data().path("player").asString());
        assertTrue(profile().member(SqLiteTestDatabase.player(worker)).isEmpty());
    }

    @Test
    void aMemberWithoutThePermissionCannotKick() {
        var result = perform(worker, Actions.MEMBERS_KICK, player(manager));

        assertFalse(result.ok());
        assertEquals("text of " + result.reason(), result.message());
        assertTrue(profile().member(SqLiteTestDatabase.player(manager)).isPresent());
    }

    @Test
    void aMemberLeavesButTheOwnerMayNot() {
        assertTrue(perform(worker, Actions.MEMBERS_LEAVE, "{}").ok());
        assertTrue(profile().member(SqLiteTestDatabase.player(worker)).isEmpty());

        var owners = perform(owner, Actions.MEMBERS_LEAVE, "{}");
        assertFalse(owners.ok());
        assertTrue(profile().member(SqLiteTestDatabase.player(owner)).isPresent());
    }

    @Test
    void permissionsAreGrantedOnlyFromThoseTheEditorHolds() {
        var granted = perform(
                manager,
                Actions.MEMBERS_PERMISSIONS,
                "{\"player\":\"" + worker + "\",\"permissions\":{\"KICK\":true}}");
        assertTrue(granted.ok(), granted.reason());
        assertEquals("KICK", granted.data().path("permissions").get(0).asString());

        var refused = perform(
                manager,
                Actions.MEMBERS_PERMISSIONS,
                "{\"player\":\"" + worker + "\",\"permissions\":{\"MANAGE_ORDERS\":true}}");
        assertFalse(refused.ok());

        var owner = perform(
                manager,
                Actions.MEMBERS_PERMISSIONS,
                "{\"player\":\"" + worker + "\",\"permissions\":{\"OWNER\":true}}");
        assertEquals("error.ownerPermission", owner.reason());
    }

    @Test
    void permissionsOfTheWrongShapeAreInvalid() {
        for (var args : List.of(
                "{\"player\":\"" + worker + "\"}",
                "{\"player\":\"" + worker + "\",\"permissions\":{}}",
                "{\"player\":\"" + worker + "\",\"permissions\":{\"FLY\":true}}",
                "{\"player\":\"" + worker + "\",\"permissions\":{\"KICK\":\"yes\"}}",
                "{\"player\":\"nobody\",\"permissions\":{\"KICK\":true}}")) {
            assertEquals(
                    "dashboard.invalid",
                    perform(manager, Actions.MEMBERS_PERMISSIONS, args).reason(),
                    args);
        }
    }

    @Test
    void anInviteIsTakenBackAndAnApplicationAcceptedOrDenied() {
        var now = LocalDateTime.now();
        world.memberships
                .submitInvite(company, outsider, manager, now, now.plusDays(1))
                .join();
        var revoked = perform(manager, Actions.MEMBERS_REVOKE, player(outsider));
        assertTrue(revoked.ok(), revoked.reason());
        assertTrue(world.memberships.retrieveInvitesBy(company, now).join().isEmpty());

        perform(owner, Actions.APPLICATIONS_JOIN_MODE, "{\"mode\":\"APPLICATIONS\"}");
        var applicant = world.player("Fern");
        world.memberships
                .submitApplication(company, outsider, "Hi", now, now.plusDays(1), 5)
                .join();
        world.memberships
                .submitApplication(company, applicant, "Me too", now, now.plusDays(1), 5)
                .join();

        var accepted = perform(manager, Actions.APPLICATIONS_ACCEPT, player(outsider));
        assertTrue(accepted.ok(), accepted.reason());
        assertTrue(profile().member(SqLiteTestDatabase.player(outsider)).isPresent());

        var denied = perform(manager, Actions.APPLICATIONS_DENY, player(applicant));
        assertTrue(denied.ok(), denied.reason());
        assertTrue(profile().member(SqLiteTestDatabase.player(applicant)).isEmpty());

        var refused = perform(worker, Actions.APPLICATIONS_DENY, player(applicant));
        assertFalse(refused.ok());
        assertEquals("text of " + "<i18n:" + refused.reason() + ">", refused.message());
    }

    @Test
    void theJoinModeIsSetByName() {
        var result = perform(owner, Actions.APPLICATIONS_JOIN_MODE, "{\"mode\":\"OPEN\"}");

        assertTrue(result.ok(), result.reason());
        assertEquals(JoinMode.OPEN, profile().joinMode());
        assertEquals(
                "dashboard.invalid",
                perform(owner, Actions.APPLICATIONS_JOIN_MODE, "{\"mode\":\"x\"}")
                        .reason());
    }

    @Test
    void theProfileIsEditedFieldByField() {
        var description = perform(manager, Actions.PROFILE_DESCRIPTION, "{\"description\":\"We dig.\"}");
        assertTrue(description.ok(), description.reason());
        assertEquals("We dig.", description.data().path("description").asString());

        var tag = perform(manager, Actions.PROFILE_TAG, "{\"tag\":\"MIN\"}");
        assertTrue(tag.ok(), tag.reason());
        assertEquals("MIN", profile().details().tag());

        assertFalse(perform(worker, Actions.PROFILE_TAG, "{\"tag\":\"NO\"}").ok());
        assertEquals(
                "dashboard.invalid",
                perform(manager, Actions.PROFILE_COLOR, "{}").reason());
    }

    @Test
    void aRenameIsQuotedThenPaidByTheOwner() {
        world.companySettings.renamePrice(100);
        world.wallets.put(owner, 150.0);

        var quote = perform(owner, Actions.PROFILE_RENAME, "{\"name\":\"Diggers\",\"quote\":true}");
        assertTrue(quote.ok(), quote.reason());
        assertEquals(100.0, quote.data().path("price").asDouble());
        assertEquals("Miners", profile().name());

        var renamed = perform(owner, Actions.PROFILE_RENAME, "{\"name\":\"Diggers\",\"payment\":\"PLAYER\"}");
        assertTrue(renamed.ok(), renamed.reason());
        assertEquals("Diggers", profile().name());
        assertEquals(50.0, world.wallets.get(owner));

        assertFalse(perform(manager, Actions.PROFILE_RENAME, "{\"name\":\"Mine\",\"payment\":\"PLAYER\"}")
                .ok());
        assertEquals(
                "dashboard.invalid",
                perform(owner, Actions.PROFILE_RENAME, "{\"name\":\"Mine\",\"payment\":\"BANK\"}")
                        .reason());
    }

    @Test
    void moneyGoesInAndOutOfTheTreasury() {
        world.wallets.put(worker, 50.0);

        var deposit = perform(worker, Actions.TREASURY_DEPOSIT, "{\"amount\":20.5}");
        assertTrue(deposit.ok(), deposit.reason());
        assertEquals(20.5, deposit.data().path("balance").asDouble());
        assertEquals(29.5, world.wallets.get(worker));

        var unpermitted = perform(worker, Actions.TREASURY_WITHDRAW, "{\"amount\":5}");
        assertEquals("error.permission.treasury", unpermitted.reason());

        var withdraw = perform(manager, Actions.TREASURY_WITHDRAW, "{\"amount\":5}");
        assertTrue(withdraw.ok(), withdraw.reason());
        assertEquals(15.5, withdraw.data().path("balance").asDouble());
        assertEquals(5.0, world.wallets.get(manager));

        var tooMuch = perform(manager, Actions.TREASURY_WITHDRAW, "{\"amount\":500}");
        assertEquals("company.bank.error.insufficient", tooMuch.reason());
    }

    @Test
    void anAmountMustBeAPositiveNumberAboveTheMinimum() {
        for (var amount : List.of("\"5\"", "0", "-3", "0.5", "1.234")) {
            var result = perform(manager, Actions.TREASURY_DEPOSIT, "{\"amount\":" + amount + "}");
            assertEquals("company.bank.error.amount", result.reason(), amount);
        }
        assertEquals(
                "company.bank.error.playerFunds",
                perform(manager, Actions.TREASURY_DEPOSIT, "{\"amount\":5}").reason());
    }

    @Test
    void theShareIsSetUpToTheCap() {
        var share = perform(manager, Actions.TREASURY_SHARE, "{\"percent\":20}");
        assertTrue(share.ok(), share.reason());
        assertEquals(20, share.data().path("share").asInt());
        assertEquals(20, world.treasuryService.effectiveShare(company).join());

        assertEquals(
                "company.bank.error.shareCap",
                perform(manager, Actions.TREASURY_SHARE, "{\"percent\":30}").reason());
        assertEquals(
                "dashboard.invalid",
                perform(manager, Actions.TREASURY_SHARE, "{\"percent\":2.5}").reason());
    }

    @Test
    void aPlayerWithoutACompanyIsRefused() {
        assertEquals(
                "error.noMember",
                perform(outsider, Actions.TREASURY_DEPOSIT, "{\"amount\":5}").reason());
        assertEquals(
                "error.noMember", perform(outsider, Actions.MEMBERS_LEAVE, "{}").reason());
    }

    @Test
    void theTreasuryRefusalsMatchTheBankCommand() {
        var refusal = world.treasuryService.explain(
                TreasuryService.Result.of(TreasuryService.Outcome.INVALID_AMOUNT), "coins");
        assertEquals("company.bank.error.amount", refusal.key());
        assertEquals(1, refusal.values().size());
    }

    @Test
    void theJoinModeNeedsThePermissionToEditTheProfile() {
        var recruiter = world.player("Rook");
        var editor = world.player("Quill");
        world.member(company, recruiter, CompanyPermission.INVITE);
        world.member(company, editor, CompanyPermission.EDIT_PROFILE);

        assertFalse(perform(recruiter, Actions.APPLICATIONS_JOIN_MODE, "{\"mode\":\"OPEN\"}")
                .ok());
        assertEquals(JoinMode.INVITE_ONLY, profile().joinMode());
        assertTrue(perform(editor, Actions.APPLICATIONS_JOIN_MODE, "{\"mode\":\"OPEN\"}")
                .ok());
        assertEquals(JoinMode.OPEN, profile().joinMode());
    }

    @Test
    void theTreasuryPaysARenameWhenItHoldsEnoughAndTheServerAllowsIt() {
        world.companySettings.renamePrice(100);
        world.wallets.put(manager, 80.0);
        perform(manager, Actions.TREASURY_DEPOSIT, "{\"amount\":80}");

        var quote = perform(owner, Actions.PROFILE_RENAME, "{\"name\":\"Diggers\",\"quote\":true}");
        assertEquals(80.0, quote.data().path("treasury").asDouble());
        assertEquals(
                "company.bank.error.insufficient",
                perform(owner, Actions.PROFILE_RENAME, "{\"name\":\"Diggers\",\"payment\":\"TREASURY\"}")
                        .reason());

        world.wallets.put(manager, 20.0);
        perform(manager, Actions.TREASURY_DEPOSIT, "{\"amount\":20}");
        var renamed = perform(owner, Actions.PROFILE_RENAME, "{\"name\":\"Diggers\",\"payment\":\"TREASURY\"}");
        assertTrue(renamed.ok(), renamed.reason());
        assertEquals("Diggers", profile().name());

        world.companySettings.treasury().payRename(false);
        assertEquals(
                "company.bank.error.disabled",
                perform(owner, Actions.PROFILE_RENAME, "{\"name\":\"Miners\",\"payment\":\"TREASURY\"}")
                        .reason());
    }

    @Test
    void aBlankNameIsRefused() {
        world.wallets.put(owner, 50000.0);
        for (var name : List.of("\"\"", "\"   \"")) {
            var result = perform(owner, Actions.PROFILE_RENAME, "{\"name\":" + name + ",\"payment\":\"PLAYER\"}");
            assertFalse(result.ok(), name);
        }
        assertEquals("Miners", profile().name());
    }

    @Test
    void membersAndPlayersOfAnotherCompanyAreOutOfReach() {
        var rival = world.player("Rival");
        var rivalWorker = world.player("Rook");
        var other = world.company("Rivals", Map.of(rival, List.of(CompanyPermission.OWNER), rivalWorker, List.of()));
        var now = LocalDateTime.now();
        world.memberships
                .submitInvite(other, outsider, rival, now, now.plusDays(1))
                .join();
        world.memberships
                .submitApplication(other, world.player("Fern"), "Hi", now, now.plusDays(1), 5)
                .join();

        assertFalse(perform(manager, Actions.MEMBERS_KICK, player(rivalWorker)).ok());
        assertFalse(perform(manager, Actions.MEMBERS_REVOKE, player(outsider)).ok());
        assertEquals(1, world.memberships.retrieveInvitesBy(other, now).join().size());
        var applicant = world.memberships
                .retrieveApplicationsTo(other, now)
                .join()
                .getFirst()
                .applicant();
        assertFalse(
                perform(manager, Actions.APPLICATIONS_ACCEPT, player(applicant)).ok());
        assertEquals(
                1, world.memberships.retrieveApplicationsTo(other, now).join().size());
    }
}
