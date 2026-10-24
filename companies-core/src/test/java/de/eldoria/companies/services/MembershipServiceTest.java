/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.chojo.sadu.core.updater.SqlVersion;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.chojo.sadu.sqlite.databases.SqLite;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.components.join.JoinResult;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteMembershipData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.EventRecorder;
import de.eldoria.companies.events.company.CompanyApplicationAnswerEvent;
import de.eldoria.companies.events.company.CompanyApplicationEvent;
import de.eldoria.companies.events.company.CompanyApplicationWithdrawEvent;
import de.eldoria.companies.events.company.CompanyInviteAnswerEvent;
import de.eldoria.companies.events.company.CompanyInviteEvent;
import de.eldoria.companies.events.company.CompanyInviteRevokeEvent;
import de.eldoria.companies.events.company.CompanyJoinModeChangeEvent;
import de.eldoria.companies.services.MembershipService.Outcome;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.util.TestExecutors;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembershipServiceTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 3, 1, 12, 0, 0);

    @TempDir
    Path directory;

    private final Map<UUID, OfflinePlayer> players = new HashMap<>();
    private final List<Sent> notifications = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private final EventRecorder fired = new EventRecorder();
    private final CompanySettings settings = new CompanySettings();
    private LocalDateTime now = START;

    private ExecutorService executor;
    private SqLiteCompanyData companyData;
    private SqLiteMembershipData membershipData;
    private MembershipService service;

    private OfflinePlayer owner;
    private OfflinePlayer recruiter;
    private OfflinePlayer worker;
    private OfflinePlayer outsider;
    private int miners;

    @BeforeEach
    void setUp() throws Exception {
        var dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + directory.resolve("data.db"));
        QueryConfiguration.setDefault(QueryConfiguration.builder(dataSource).build());
        SqlUpdater.builder(dataSource, SqLite.get())
                .setVersionTable("companies_db_version")
                .setVersion(SqlVersion.load(getClass().getClassLoader()))
                .withClassLoader(getClass().getClassLoader())
                .setReplacements(new QueryReplacement("companies_schema", "companies_schema"))
                .execute();
        executor = Executors.newFixedThreadPool(4);
        companyData = new SqLiteCompanyData(executor);
        membershipData = new SqLiteMembershipData(executor);
        service = new MembershipService(
                companyData,
                membershipData,
                () -> settings,
                (recipient, type, args) -> notifications.add(new Sent(recipient, type, args)),
                new RecordedEvents(),
                this::player,
                () -> now,
                Runnable::run,
                Logger.getLogger("test"));

        owner = player("Owner");
        recruiter = player("Recruiter");
        worker = player("Worker");
        outsider = player("Outsider");
        miners = companyOwnedBy("Miners", JoinMode.INVITE_ONLY, owner);
        member(miners, recruiter, CompanyPermission.INVITE.mask());
        member(miners, worker, 0);
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

    private void member(int company, OfflinePlayer player, long permission) {
        var mask = permission < 0 ? CompanyPermission.OWNER.mask() : permission;
        companyData
                .submitMemberUpdate(CompanyMember.of(company, player.getUniqueId(), mask))
                .join();
    }

    private int companyOwnedBy(String name, JoinMode mode, OfflinePlayer companyOwner) {
        var id = companyData.submitCompanyCreation(name, mode).join();
        member(id, companyOwner, -1);
        return id;
    }

    private CompanyProfile profile(int company) {
        return companyData
                .retrieveCompanyProfile(
                        companyData.retrieveCompanyById(company).join().orElseThrow())
                .join()
                .orElseThrow();
    }

    private boolean isMemberOf(int company, OfflinePlayer player) {
        return profile(company).isMember(player.getUniqueId());
    }

    private List<NotificationType> notificationsOf(OfflinePlayer player) {
        return notifications.stream()
                .filter(sent -> sent.recipient().equals(player.getUniqueId()))
                .map(Sent::type)
                .toList();
    }

    private static void assertOutcome(String key, boolean success, Outcome outcome) {
        assertEquals(key, outcome.key());
        assertEquals(success, outcome.success());
    }

    private Outcome invite(OfflinePlayer inviter, OfflinePlayer target) {
        return service.invite(inviter, target, true).join();
    }

    @Test
    void inviteIsStoredAndTellsTheTarget() {
        assertOutcome("company.invite.sent", true, invite(recruiter, outsider));

        var received = service.invites(outsider).join().received();
        assertEquals(1, received.size());
        assertEquals("Miners", received.getFirst().companyName());
        assertEquals(
                START.plusHours(settings.inviteHours()), received.getFirst().expires());
        assertEquals(List.of(NotificationType.INVITE_RECEIVED), notificationsOf(outsider));
        var args = notifications.getFirst().args().values();
        assertEquals("Miners", args.get("company_name").value());
        assertEquals("Recruiter", args.get("player_name").value());
    }

    @Test
    void invitingNeedsTheInvitePermission() {
        assertOutcome("error.permission.invite", false, invite(worker, outsider));
        assertOutcome("error.noMember", false, invite(outsider, worker));
        assertTrue(notifications.isEmpty());
    }

    @Test
    void inviteIsRefusedForPlayersWhoCanNotJoin() {
        assertOutcome("error.hasCompany", false, invite(owner, worker));
        assertOutcome("company.invite.error.self", false, invite(owner, owner));
        assertOutcome(
                "error.canNotJoin",
                false,
                service.invite(owner, outsider, false).join());
    }

    @Test
    void inviteIsRefusedWhenTheCompanyIsFull() {
        settings.createLevel(1).settings().maxMembers(3);

        assertOutcome("error.companyFull", false, invite(owner, outsider));
    }

    @Test
    void acceptedInviteMakesAMemberAndFiresTheJoin() {
        invite(recruiter, outsider);

        assertOutcome(
                "company.join.joined",
                true,
                service.acceptInvite(outsider, miners).join());

        assertTrue(isMemberOf(miners, outsider));
        assertEquals(List.of("joined Miners Outsider 4"), events);
        assertEquals(List.of(NotificationType.INVITE_ACCEPTED), notificationsOf(recruiter));
        assertTrue(service.invites(outsider).join().received().isEmpty());
    }

    @Test
    void addedMemberJoinsWithoutInviteAndFiresTheJoin() {
        assertEquals(
                JoinResult.JOINED,
                service.addMember(miners, outsider.getUniqueId()).join());

        assertTrue(isMemberOf(miners, outsider));
        assertEquals(List.of("joined Miners Outsider 4"), events);
    }

    @Test
    void addMemberReportsWhyThePlayerWasNotAdded() {
        assertEquals(
                JoinResult.ALREADY_MEMBER,
                service.addMember(miners, worker.getUniqueId()).join());
        assertEquals(
                JoinResult.UNKNOWN_COMPANY,
                service.addMember(999, outsider.getUniqueId()).join());
        assertTrue(events.isEmpty());
    }

    @Test
    void inviteCanOnlyBeAcceptedWhileItIsOpen() {
        assertOutcome(
                "company.invite.error.noPending",
                false,
                service.acceptInvite(outsider, miners).join());

        invite(recruiter, outsider);
        now = START.plusHours(settings.inviteHours()).plusMinutes(1);

        assertOutcome(
                "company.invite.error.noPending",
                false,
                service.acceptInvite(outsider, miners).join());
        assertFalse(isMemberOf(miners, outsider));
    }

    @Test
    void acceptingRespectsTheMemberLimit() {
        invite(recruiter, outsider);
        settings.createLevel(1).settings().maxMembers(3);

        assertOutcome(
                "error.companyFull",
                false,
                service.acceptInvite(outsider, miners).join());
        assertTrue(events.isEmpty());
    }

    @Test
    void declinedInviteTellsTheInviter() {
        invite(recruiter, outsider);

        assertOutcome(
                "company.invite.declined",
                true,
                service.denyInvite(outsider, miners).join());

        assertEquals(List.of(NotificationType.INVITE_DECLINED), notificationsOf(recruiter));
        assertTrue(service.invites(outsider).join().received().isEmpty());
    }

    @Test
    void revokedInviteTellsTheTargetOnce() {
        invite(recruiter, outsider);

        assertOutcome(
                "company.invite.revoked",
                true,
                service.revokeInvite(owner, outsider.getUniqueId()).join());
        assertOutcome(
                "company.invite.error.notInvited",
                false,
                service.revokeInvite(owner, outsider.getUniqueId()).join());
        assertOutcome(
                "error.permission.invite",
                false,
                service.revokeInvite(worker, outsider.getUniqueId()).join());

        assertEquals(
                List.of(NotificationType.INVITE_RECEIVED, NotificationType.INVITE_REVOKED), notificationsOf(outsider));
    }

    @Test
    void sentInvitesAreShownToMembersWhoMayInvite() {
        invite(recruiter, outsider);

        var overview = service.invites(owner).join();
        assertEquals(1, overview.sent().size());
        assertEquals("Outsider", overview.name(overview.sent().getFirst().target()));
        assertTrue(service.invites(worker).join().sent().isEmpty());
    }

    @Test
    void openCompaniesCanBeJoined() {
        var farmers = companyOwnedBy("Farmers", JoinMode.OPEN, player("Farmer"));

        assertOutcome(
                "company.join.joined", true, service.join(outsider, "Farmers").join());

        assertTrue(isMemberOf(farmers, outsider));
        assertEquals(1, events.size());
        assertOutcome(
                "error.alreadyMember", false, service.join(worker, "Farmers").join());
    }

    @Test
    void onlyOpenCompaniesCanBeJoinedWithoutInvite() {
        assertOutcome(
                "company.join.error.notOpen",
                false,
                service.join(outsider, "Miners").join());
        assertOutcome(
                "error.unknownCompany", false, service.join(outsider, "Nobody").join());
        assertFalse(isMemberOf(miners, outsider));
    }

    @Test
    void applicationReachesTheMembersWhoMayInvite() {
        var bakers = companyOwnedBy("Bakers", JoinMode.APPLICATIONS, player("Baker"));
        var invite = player("Helper");
        var plain = player("Plain");
        member(bakers, invite, CompanyPermission.INVITE.mask());
        member(bakers, plain, 0);

        assertOutcome(
                "company.apply.sent",
                true,
                service.apply(outsider, "Bakers", "Let\nme  in ").join());

        var applications = service.invites(outsider).join().applications();
        assertEquals(1, applications.size());
        assertEquals("Let me in", applications.getFirst().message());
        assertEquals(List.of(NotificationType.APPLICATION_RECEIVED), notificationsOf(invite));
        assertTrue(notificationsOf(plain).isEmpty());
        assertEquals(2, notifications.size());
    }

    @Test
    void applicationsFollowTheRules() {
        companyOwnedBy("Bakers", JoinMode.APPLICATIONS, player("Baker"));
        companyOwnedBy("Smiths", JoinMode.APPLICATIONS, player("Smith"));
        settings.joining().maxApplicationLength(10);
        settings.joining().maxPendingApplications(1);

        assertOutcome(
                "company.apply.error.notAccepting",
                false,
                service.apply(outsider, "Miners", "Hi").join());
        assertOutcome(
                "company.apply.error.empty",
                false,
                service.apply(outsider, "Bakers", " \n ").join());
        assertOutcome(
                "company.apply.error.tooLong",
                false,
                service.apply(outsider, "Bakers", "Hello there!").join());
        assertOutcome(
                "error.alreadyMember",
                false,
                service.apply(worker, "Bakers", "Hi").join());
        assertOutcome(
                "company.apply.sent",
                true,
                service.apply(outsider, "Bakers", "Hi").join());
        assertOutcome(
                "company.apply.error.alreadyPending",
                false,
                service.apply(outsider, "Bakers", "Hi").join());
        assertOutcome(
                "company.apply.error.tooMany",
                false,
                service.apply(outsider, "Smiths", "Hi").join());
    }

    @Test
    void acceptedApplicationMakesAMember() {
        var baker = player("Baker");
        var bakers = companyOwnedBy("Bakers", JoinMode.APPLICATIONS, baker);
        service.apply(outsider, "Bakers", "Hi").join();

        assertOutcome(
                "company.applications.error.noPending",
                false,
                service.acceptApplication(owner, outsider.getUniqueId()).join());
        assertOutcome(
                "company.apply.accepted",
                true,
                service.acceptApplication(baker, outsider.getUniqueId()).join());

        assertTrue(isMemberOf(bakers, outsider));
        assertTrue(notificationsOf(outsider).contains(NotificationType.APPLICATION_ACCEPTED));
        assertTrue(service.invites(outsider).join().applications().isEmpty());
    }

    @Test
    void deniedApplicationTellsTheApplicant() {
        var baker = player("Baker");
        companyOwnedBy("Bakers", JoinMode.APPLICATIONS, baker);
        service.apply(outsider, "Bakers", "Hi").join();

        assertOutcome(
                "company.apply.denied",
                true,
                service.denyApplication(baker, outsider.getUniqueId()).join());

        assertEquals(List.of(NotificationType.APPLICATION_DENIED), notificationsOf(outsider));
        assertTrue(service.invites(outsider).join().applications().isEmpty());
    }

    @Test
    void applicationsAreAnsweredByMembersWhoMayInvite() {
        service.setJoinMode(owner, JoinMode.APPLICATIONS).join();
        service.apply(outsider, "Miners", "Hi").join();

        assertOutcome(
                "error.permission.invite",
                false,
                service.acceptApplication(worker, outsider.getUniqueId()).join());
        assertTrue(service.applications(worker).join().refusal().isPresent());
        var overview = service.applications(recruiter).join();
        assertTrue(overview.refusal().isEmpty());
        assertEquals("Miners", overview.companyName());
        assertEquals(
                "Outsider", overview.name(overview.applications().getFirst().applicant()));
    }

    @Test
    void ownApplicationCanBeWithdrawn() {
        var bakers = companyOwnedBy("Bakers", JoinMode.APPLICATIONS, player("Baker"));
        service.apply(outsider, "Bakers", "Hi").join();

        assertOutcome(
                "company.apply.withdrawn",
                true,
                service.withdraw(outsider, bakers).join());
        assertOutcome(
                "company.apply.error.noPending",
                false,
                service.withdraw(outsider, bakers).join());
    }

    @Test
    void joinModeIsChangedByMembersWhoMayEditTheProfile() {
        assertOutcome(
                "company.joinMode.error.permission",
                false,
                service.setJoinMode(recruiter, JoinMode.OPEN).join());

        assertOutcome(
                "company.joinMode.changed",
                true,
                service.setJoinMode(owner, JoinMode.OPEN).join());

        assertEquals(JoinMode.OPEN, profile(miners).joinMode());
        assertEquals(List.of("edited Miners INVITE_ONLY OPEN Owner"), events);
        assertEquals(List.of(NotificationType.PROFILE_CHANGED), notificationsOf(worker));
        assertTrue(notificationsOf(owner).isEmpty());
    }

    @Test
    void expiryRemovesAndNotifiesOnce() {
        companyOwnedBy("Bakers", JoinMode.APPLICATIONS, player("Baker"));
        invite(recruiter, outsider);
        var applicant = player("Applicant");
        service.apply(applicant, "Bakers", "Hi").join();
        notifications.clear();

        assertEquals(0, service.expire().join());
        now = START.plusHours(settings.inviteHours()).plusMinutes(1);

        assertEquals(2, service.expire().join());
        assertEquals(0, service.expire().join());
        assertEquals(List.of(NotificationType.INVITE_EXPIRED_TARGET), notificationsOf(outsider));
        assertEquals(List.of(NotificationType.INVITE_EXPIRED_INVITER), notificationsOf(recruiter));
        assertEquals(List.of(NotificationType.APPLICATION_EXPIRED), notificationsOf(applicant));
        assertEquals(
                "Outsider",
                notifications.stream()
                        .filter(sent -> sent.type() == NotificationType.INVITE_EXPIRED_INVITER)
                        .findFirst()
                        .orElseThrow()
                        .args()
                        .values()
                        .get("player_name")
                        .value());
    }

    @Test
    void storedInviteFiresTheInviteEvent() {
        assertOutcome("error.permission.invite", false, invite(worker, outsider));
        assertTrue(fired.of(CompanyInviteEvent.class).isEmpty());

        invite(recruiter, outsider);

        var event = fired.single(CompanyInviteEvent.class);
        assertEquals(miners, event.company().id());
        assertEquals(outsider.getUniqueId(), event.target());
        assertEquals(recruiter.getUniqueId(), event.inviter());
        assertEquals(START.plusHours(settings.inviteHours()), event.expires());
        assertTrue(event.isAsynchronous());
    }

    @Test
    void revokedInviteFiresTheRevokeEventOnce() {
        invite(recruiter, outsider);
        service.revokeInvite(worker, outsider.getUniqueId()).join();
        assertTrue(fired.of(CompanyInviteRevokeEvent.class).isEmpty());

        service.revokeInvite(owner, outsider.getUniqueId()).join();
        service.revokeInvite(owner, outsider.getUniqueId()).join();

        var event = fired.single(CompanyInviteRevokeEvent.class);
        assertEquals(miners, event.company().id());
        assertEquals(outsider.getUniqueId(), event.target());
        assertEquals(owner.getUniqueId(), event.actor());
    }

    @Test
    void acceptedInviteFiresTheAnswerWithTheNewMember() {
        service.acceptInvite(outsider, miners).join();
        assertTrue(fired.of(CompanyInviteAnswerEvent.class).isEmpty());
        invite(recruiter, outsider);

        service.acceptInvite(outsider, miners).join();

        var event = fired.single(CompanyInviteAnswerEvent.class);
        assertTrue(event.accepted());
        assertEquals(outsider.getUniqueId(), event.target());
        assertEquals(recruiter.getUniqueId(), event.inviter());
        assertTrue(event.company().members().stream()
                .anyMatch(member -> member.uuid().equals(outsider.getUniqueId())));
    }

    @Test
    void refusedJoinOfAnInviteFiresNoAnswer() {
        invite(recruiter, outsider);
        settings.createLevel(1).settings().maxMembers(3);

        service.acceptInvite(outsider, miners).join();

        assertTrue(fired.of(CompanyInviteAnswerEvent.class).isEmpty());
    }

    @Test
    void declinedInviteFiresTheAnswerOnce() {
        invite(recruiter, outsider);

        service.denyInvite(outsider, miners).join();
        service.denyInvite(outsider, miners).join();

        var event = fired.single(CompanyInviteAnswerEvent.class);
        assertFalse(event.accepted());
        assertEquals(miners, event.company().id());
        assertEquals(outsider.getUniqueId(), event.target());
        assertEquals(recruiter.getUniqueId(), event.inviter());
    }

    @Test
    void storedApplicationFiresTheApplicationEvent() {
        var bakers = companyOwnedBy("Bakers", JoinMode.APPLICATIONS, player("Baker"));
        service.apply(outsider, "Miners", "Hi").join();
        assertTrue(fired.of(CompanyApplicationEvent.class).isEmpty());

        service.apply(outsider, "Bakers", "Let\nme in").join();
        service.apply(outsider, "Bakers", "Again").join();

        var event = fired.single(CompanyApplicationEvent.class);
        assertEquals(bakers, event.company().id());
        assertEquals(outsider.getUniqueId(), event.applicant());
        assertEquals("Let me in", event.message());
    }

    @Test
    void withdrawnApplicationFiresTheWithdrawEventOnce() {
        var bakers = companyOwnedBy("Bakers", JoinMode.APPLICATIONS, player("Baker"));
        service.apply(outsider, "Bakers", "Hi").join();

        service.withdraw(outsider, bakers).join();
        service.withdraw(outsider, bakers).join();

        var event = fired.single(CompanyApplicationWithdrawEvent.class);
        assertEquals(bakers, event.company().id());
        assertEquals(outsider.getUniqueId(), event.applicant());
    }

    @Test
    void acceptedApplicationFiresTheAnswerWithTheNewMember() {
        var baker = player("Baker");
        companyOwnedBy("Bakers", JoinMode.APPLICATIONS, baker);
        service.apply(outsider, "Bakers", "Hi").join();
        service.acceptApplication(owner, outsider.getUniqueId()).join();
        assertTrue(fired.of(CompanyApplicationAnswerEvent.class).isEmpty());

        service.acceptApplication(baker, outsider.getUniqueId()).join();

        var event = fired.single(CompanyApplicationAnswerEvent.class);
        assertTrue(event.accepted());
        assertEquals(outsider.getUniqueId(), event.applicant());
        assertEquals(baker.getUniqueId(), event.actor());
        assertTrue(event.company().members().stream()
                .anyMatch(member -> member.uuid().equals(outsider.getUniqueId())));
    }

    @Test
    void deniedApplicationFiresTheAnswerOnce() {
        var baker = player("Baker");
        var bakers = companyOwnedBy("Bakers", JoinMode.APPLICATIONS, baker);
        service.apply(outsider, "Bakers", "Hi").join();
        service.denyApplication(owner, outsider.getUniqueId()).join();

        service.denyApplication(baker, outsider.getUniqueId()).join();
        service.denyApplication(baker, outsider.getUniqueId()).join();

        var event = fired.single(CompanyApplicationAnswerEvent.class);
        assertFalse(event.accepted());
        assertEquals(bakers, event.company().id());
        assertEquals(outsider.getUniqueId(), event.applicant());
        assertEquals(baker.getUniqueId(), event.actor());
    }

    @Test
    void changedJoinModeFiresTheJoinModeEvent() {
        service.setJoinMode(recruiter, JoinMode.OPEN).join();
        assertTrue(fired.of(CompanyJoinModeChangeEvent.class).isEmpty());

        service.setJoinMode(owner, JoinMode.OPEN).join();

        var event = fired.single(CompanyJoinModeChangeEvent.class);
        assertEquals(miners, event.company().id());
        assertEquals(JoinMode.INVITE_ONLY, event.previous());
        assertEquals(JoinMode.OPEN, event.joinMode());
        assertEquals(owner.getUniqueId(), event.actor());
    }

    @Test
    void applicationMessagesAreKeptOnOneLine() {
        assertEquals("a b c", MembershipService.singleLine(" a\r\nb\t c "));
        assertEquals("", MembershipService.singleLine("\n"));
    }

    private record Sent(UUID recipient, NotificationType type, NotificationArgs args) {}

    private final class RecordedEvents implements MembershipService.Events {
        @Override
        public void joined(CompanyProfile company, OfflinePlayer player) {
            events.add("joined " + company.name() + " " + player.getName() + " "
                    + company.members().size());
        }

        @Override
        public void profileEdited(CompanyProfile previous, CompanyProfile company, OfflinePlayer player) {
            events.add("edited " + company.name() + " " + previous.joinMode() + " " + company.joinMode() + " "
                    + player.getName());
        }

        @Override
        public void fire(Event event) {
            fired.accept(event);
        }
    }
}
