/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.components.ranking.TopOrder;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.companysettings.ProfileSettings;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.CompanyRank;
import de.eldoria.companies.data.wrapper.company.CompanyStats;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static de.eldoria.companies.components.company.CompanyPermission.INVITE;
import static de.eldoria.companies.components.company.CompanyPermission.MANAGE_PERMISSIONS;
import static de.eldoria.companies.components.company.CompanyPermission.OWNER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyViewsTest {
    private static final LocalDateTime FOUNDED = LocalDateTime.of(2026, 1, 2, 3, 4);

    private ServerMock server;
    private PlayerMock owner;
    private PlayerMock member;
    private final CompanySettings settings = new CompanySettings();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        owner = server.addPlayer("Owner");
        member = server.addPlayer("Zed");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private CompanyProfile profile(int level) {
        return new CompanyProfile(
                7,
                "Acme",
                FOUNDED,
                level,
                CompanyDetails.NONE,
                List.of(
                        CompanyMember.of(7, member.getUniqueId(), mask(INVITE)),
                        CompanyMember.of(7, owner.getUniqueId(), mask(OWNER))));
    }

    private static long mask(CompanyPermission... permissions) {
        return CompanyPermission.composePermissions(permissions);
    }

    @Test
    void theOwnProfileShowsTheProgressTowardsTheNextLevel() {
        var stats = new CompanyStats(7, "Acme", FOUNDED, 2, 5, 30_000, 500);

        var view = ProfileView.own(profile(1), settings, stats, 3, false);

        assertTrue(view.own());
        assertEquals("Owner", view.leader());
        assertEquals(2, view.memberCount());
        assertEquals(OptionalInt.of(3), view.orderCount());
        var next = view.next().orElseThrow();
        assertEquals("Business", next.level().levelName());
        var progress = next.progress();
        assertEquals(4, progress.size());
        assertEquals(1.0, progress.get(0).share());
        assertEquals(0.5, progress.get(1).share());
        assertEquals(0.5, progress.get(2).share());
        assertEquals(1.0, progress.get(3).share());
        assertTrue(progress.get(3).money());
    }

    @Test
    void withoutStatsTheNextLevelHasNoProgress() {
        var view = ProfileView.own(profile(1), settings, null, 0, false);

        assertTrue(view.next().orElseThrow().progress().isEmpty());
    }

    @Test
    void theHighestLevelHasNoNextLevel() {
        var view = ProfileView.own(profile(settings.level().size()), settings, null, 0, false);

        assertTrue(view.next().isEmpty());
    }

    @Test
    void theExternalProfileHidesProgressAndOrders() {
        var view = ProfileView.external(profile(1), settings);

        assertFalse(view.own());
        assertTrue(view.next().isEmpty());
        assertEquals("Startup", view.level().levelName());
    }

    @Test
    void theProfileShowsDescriptionTagColourAndJoinMode() {
        var details = new CompanyDetails("We dig.", "ACME", "gold", JoinMode.OPEN);

        var view = ProfileView.external(profile(1).withDetails(details), settings);

        assertEquals(Optional.of("We dig."), view.description());
        assertEquals(Optional.of("ACME"), view.tag());
        assertEquals(Optional.of(NamedTextColor.GOLD), view.color());
        assertEquals(JoinMode.OPEN, view.joinMode());
        assertFalse(view.editable());
    }

    @Test
    void anUnknownColourIsNotShown() {
        var view = ProfileView.external(profile(1).withDetails(CompanyDetails.NONE.withColor("rainbow")), settings);

        assertEquals(Optional.empty(), view.color());
    }

    @Test
    void theOwnProfileIsEditableWhenTheViewerMayEdit() {
        assertTrue(ProfileView.own(profile(1), settings, null, 0, true).editable());
        assertFalse(ProfileView.own(profile(1), settings, null, 0, false).editable());
    }

    @Test
    void theEditorOffersThePaletteAsItIsStored() {
        var profileSettings = new ProfileSettings();
        profileSettings.colors(List.of("Gold", "gold", "#00FF00", "rainbow"));
        var details = new CompanyDetails("We dig.", "ACME", "gold", JoinMode.APPLICATIONS);

        var view = ProfileEditView.of(profile(1).withDetails(details), profileSettings);

        assertEquals(List.of("gold", "#00ff00"), view.palette());
        assertTrue(view.selected("gold"));
        assertFalse(view.selected("#00ff00"));
        assertEquals(Optional.of(NamedTextColor.GOLD), view.textColor());
        assertEquals(JoinMode.APPLICATIONS, view.joinMode());
        assertEquals(ProfileSettings.MAX_TAG_LENGTH, view.maxTagLength());
        assertTrue(view.error().isEmpty());
    }

    @Test
    void theOwnerIsListedFirst() {
        var view = MemberView.of(profile(1), member);

        assertEquals(
                List.of("Owner", "Zed"),
                view.members().stream().map(MemberEntry::name).toList());
        assertTrue(view.members().getFirst().owner());
        assertTrue(view.members().getFirst().online());
    }

    @Test
    void membersSeePermissionsAndOnlyManagersMayEditThem() {
        assertTrue(MemberView.of(profile(1), member).showPermissions());
        assertFalse(MemberView.of(profile(1), member).managePermissions());
        assertTrue(MemberView.of(profile(1), owner).managePermissions());
    }

    @Test
    void outsidersSeeNoPermissions() {
        var outsider = server.addPlayer("Outsider");

        var view = MemberView.of(profile(1), outsider);

        assertFalse(view.showPermissions());
        assertFalse(view.managePermissions());
    }

    @Test
    void aPlayerWithoutNameIsNamedByItsId() {
        var unknown = UUID.randomUUID();
        var player = (OfflinePlayer) Proxy.newProxyInstance(
                OfflinePlayer.class.getClassLoader(),
                new Class<?>[] {OfflinePlayer.class},
                (proxy, method, args) -> method.getName().equals("getUniqueId") ? unknown : null);

        assertEquals(unknown.toString(), MemberEntry.nameOf(player));
    }

    @Test
    void anOfflineMemberShowsWhenItWasLastSeen() {
        var entry = MemberEntry.of(CompanyMember.of(7, UUID.randomUUID(), mask(MANAGE_PERMISSIONS)));

        assertFalse(entry.online());
        assertTrue(entry.lastSeen().isPresent());
        assertEquals(List.of(MANAGE_PERMISSIONS), entry.permissions());
    }

    @Test
    void aFullRankingPageMayHaveASuccessor() {
        var rank = new CompanyRank(1, 7, "Acme", FOUNDED, 1, 1, 1, 1, 1, null, null);

        assertTrue(TopView.of(TopOrder.ORDERS, TimeRange.ALL, 1, List.of(rank, rank), 2, Optional.empty())
                .hasNext());
        assertFalse(TopView.of(TopOrder.ORDERS, TimeRange.ALL, 1, List.of(rank), 2, Optional.empty())
                .hasNext());
        assertFalse(TopView.of(TopOrder.ORDERS, TimeRange.ALL, 1, List.of(rank), 2, Optional.empty())
                .paging()
                .hasPrevious());
        assertTrue(TopView.of(TopOrder.ORDERS, TimeRange.ALL, 2, List.of(rank), 2, Optional.empty())
                .paging()
                .hasPrevious());
    }
}
