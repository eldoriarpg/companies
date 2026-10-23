/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.components.join.JoinResult;
import de.eldoria.companies.data.wrapper.company.Application;
import de.eldoria.companies.data.wrapper.company.ApplyResult;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.Invite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

class SqLiteMembershipDataTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 12, 0, 0);
    private static final LocalDateTime LATER = NOW.plusHours(24);

    private final UUID owner = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();
    private final UUID inviter = UUID.randomUUID();

    @TempDir
    Path directory;

    private ExecutorService executor;
    private SqLiteCompanyData companyData;
    private SqLiteMembershipData membershipData;
    private int miners;
    private int farmers;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(8);
        companyData = new SqLiteCompanyData(executor);
        membershipData = new SqLiteMembershipData(executor);
        miners = createCompany("Miners", owner);
        farmers = createCompany("Farmers", UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private int createCompany(String name, UUID companyOwner) {
        var id = companyData.submitCompanyCreation(name, JoinMode.OPEN).join();
        companyData
                .submitMemberUpdate(CompanyMember.of(id, companyOwner, CompanyPermission.OWNER.mask()))
                .join();
        return id;
    }

    private Invite invite(int company, UUID target, LocalDateTime created, LocalDateTime expires) {
        Assertions.assertTrue(membershipData
                .submitInvite(company, target, inviter, created, expires)
                .join());
        return membershipData.retrieveInvite(company, target, created).join().orElseThrow();
    }

    private ApplyResult apply(int company, UUID applicant, int maxPending) {
        return membershipData
                .submitApplication(company, applicant, "Let me in", NOW, LATER, maxPending)
                .join();
    }

    private List<Integer> companies(List<Invite> invites) {
        return invites.stream().map(Invite::company).toList();
    }

    @Test
    void inviteCarriesItsCompanyAndTimes() {
        var invite = invite(miners, player, NOW, LATER);

        Assertions.assertEquals("Miners", invite.companyName());
        Assertions.assertEquals(player, invite.target());
        Assertions.assertEquals(inviter, invite.inviter());
        Assertions.assertEquals(NOW, invite.created());
        Assertions.assertEquals(LATER, invite.expires());
    }

    @Test
    void renewedInviteReplacesTheOldOne() {
        var first = invite(miners, player, NOW, LATER);
        var renewed = invite(miners, player, NOW.plusHours(1), LATER.plusHours(1));

        Assertions.assertEquals(first.id(), renewed.id());
        Assertions.assertEquals(LATER.plusHours(1), renewed.expires());
        Assertions.assertEquals(1, SqLiteTestDatabase.count("company_invites", "TRUE"));
    }

    @Test
    void invitesAreListedForTargetAndCompany() {
        invite(miners, player, NOW, LATER);
        invite(farmers, player, NOW.plusMinutes(1), LATER);
        var other = UUID.randomUUID();
        invite(miners, other, NOW, LATER);

        Assertions.assertEquals(
                List.of(miners, farmers),
                companies(membershipData.retrieveInvitesOf(player, NOW).join()));
        Assertions.assertEquals(
                2, membershipData.retrieveInvitesBy(miners, NOW).join().size());
    }

    @Test
    void expiredInvitesAreHiddenAndListedForCleanup() {
        invite(miners, player, NOW.minusHours(2), NOW.minusHours(1));
        invite(farmers, player, NOW, LATER);

        Assertions.assertEquals(
                List.of(farmers),
                companies(membershipData.retrieveInvitesOf(player, NOW).join()));
        Assertions.assertTrue(
                membershipData.retrieveInvite(miners, player, NOW).join().isEmpty());
        Assertions.assertTrue(
                membershipData.retrieveInvitesBy(miners, NOW).join().isEmpty());
        Assertions.assertEquals(
                List.of(miners),
                companies(membershipData.retrieveExpiredInvites(NOW, 10).join()));
    }

    @Test
    void inviteIsRemovedOnlyOnce() {
        var invite = invite(miners, player, NOW, LATER);

        Assertions.assertTrue(membershipData.submitInviteRemoval(invite.id()).join());
        Assertions.assertFalse(membershipData.submitInviteRemoval(invite.id()).join());
    }

    @Test
    void applicationIsStoredWithItsMessage() {
        Assertions.assertEquals(ApplyResult.CREATED, apply(miners, player, 3));

        var application =
                membershipData.retrieveApplication(miners, player, NOW).join().orElseThrow();
        Assertions.assertEquals("Let me in", application.message());
        Assertions.assertEquals("Miners", application.companyName());
        Assertions.assertEquals(LATER, application.expires());
        Assertions.assertEquals(
                List.of(application),
                membershipData.retrieveApplicationsTo(miners, NOW).join());
        Assertions.assertEquals(
                List.of(application),
                membershipData.retrieveApplicationsOf(player, NOW).join());
    }

    @Test
    void secondApplicationToTheSameCompanyIsRefused() {
        apply(miners, player, 3);

        Assertions.assertEquals(ApplyResult.ALREADY_PENDING, apply(miners, player, 3));
        Assertions.assertEquals(1, SqLiteTestDatabase.count("company_applications", "TRUE"));
    }

    @Test
    void applicationsBeyondTheLimitAreRefused() {
        var third = createCompany("Smiths", UUID.randomUUID());

        Assertions.assertEquals(ApplyResult.CREATED, apply(miners, player, 2));
        Assertions.assertEquals(ApplyResult.CREATED, apply(farmers, player, 2));
        Assertions.assertEquals(ApplyResult.TOO_MANY, apply(third, player, 2));
    }

    @Test
    void expiredApplicationIsReplacedAndDoesNotCount() {
        membershipData
                .submitApplication(miners, player, "old", NOW.minusHours(2), NOW.minusHours(1), 1)
                .join();

        Assertions.assertEquals(
                List.of(miners),
                membershipData.retrieveExpiredApplications(NOW, 10).join().stream()
                        .map(Application::company)
                        .toList());
        Assertions.assertEquals(ApplyResult.CREATED, apply(miners, player, 1));
        Assertions.assertEquals(
                "Let me in",
                membershipData
                        .retrieveApplication(miners, player, NOW)
                        .join()
                        .orElseThrow()
                        .message());
        Assertions.assertTrue(
                membershipData.retrieveExpiredApplications(NOW, 10).join().isEmpty());
    }

    @Test
    void applicationIsRemovedOnlyOnce() {
        apply(miners, player, 3);
        var application =
                membershipData.retrieveApplication(miners, player, NOW).join().orElseThrow();

        Assertions.assertTrue(
                membershipData.submitApplicationRemoval(application.id()).join());
        Assertions.assertFalse(
                membershipData.submitApplicationRemoval(application.id()).join());
    }

    @Test
    void joinAddsAPlainMemberAndRemovesEveryInviteAndApplication() {
        invite(miners, player, NOW, LATER);
        invite(farmers, player, NOW, LATER);
        apply(farmers, player, 3);
        var other = UUID.randomUUID();
        invite(miners, other, NOW, LATER);

        Assertions.assertEquals(
                JoinResult.JOINED, membershipData.submitJoin(miners, player, 10).join());

        var profile = companyData
                .retrievePlayerCompanyProfile(SqLiteTestDatabase.player(player))
                .join()
                .orElseThrow();
        Assertions.assertEquals(miners, profile.id());
        Assertions.assertTrue(profile.member(SqLiteTestDatabase.player(player))
                .orElseThrow()
                .permissions()
                .isEmpty());
        Assertions.assertTrue(
                membershipData.retrieveInvitesOf(player, NOW).join().isEmpty());
        Assertions.assertTrue(
                membershipData.retrieveApplicationsOf(player, NOW).join().isEmpty());
        Assertions.assertEquals(
                1, membershipData.retrieveInvitesOf(other, NOW).join().size());
    }

    @Test
    void memberOfAnotherCompanyIsNotMoved() {
        Assertions.assertEquals(
                JoinResult.ALREADY_MEMBER,
                membershipData.submitJoin(farmers, owner, 10).join());
        Assertions.assertEquals(
                miners,
                companyData
                        .retrievePlayerCompany(SqLiteTestDatabase.player(owner))
                        .join()
                        .orElseThrow()
                        .id());
    }

    @Test
    void refusedJoinKeepsInvites() {
        invite(miners, player, NOW, LATER);

        Assertions.assertEquals(
                JoinResult.COMPANY_FULL,
                membershipData.submitJoin(miners, player, 1).join());
        Assertions.assertEquals(
                1, membershipData.retrieveInvitesOf(player, NOW).join().size());
    }

    @Test
    void joinIntoAMissingCompanyIsRefused() {
        Assertions.assertEquals(
                JoinResult.UNKNOWN_COMPANY,
                membershipData.submitJoin(999, player, 10).join());
    }

    private static Map<JoinResult, Long> results(List<CompletableFuture<JoinResult>> joins) {
        return joins.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    @Test
    void concurrentJoinsDoNotExceedTheMemberLimit() {
        var joins = new ArrayList<CompletableFuture<JoinResult>>();
        for (var i = 0; i < 8; i++) {
            joins.add(membershipData.submitJoin(miners, UUID.randomUUID(), 3));
        }

        Assertions.assertEquals(Map.of(JoinResult.JOINED, 2L, JoinResult.COMPANY_FULL, 6L), results(joins));
        Assertions.assertEquals(3, SqLiteTestDatabase.count("company_member", "id = " + miners));
    }

    @Test
    void concurrentJoinsOfOnePlayerJoinOnlyOneCompany() {
        var joins = new ArrayList<CompletableFuture<JoinResult>>();
        for (var i = 0; i < 4; i++) {
            joins.add(membershipData.submitJoin(createCompany("Company " + i, UUID.randomUUID()), player, 10));
        }

        Assertions.assertEquals(Map.of(JoinResult.JOINED, 1L, JoinResult.ALREADY_MEMBER, 3L), results(joins));
        Assertions.assertEquals(1, SqLiteTestDatabase.count("company_member", "permission = 0"));
    }
}
