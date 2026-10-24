/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.configuration.elements.companysettings.ProfileSettings;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.DetailsResult;
import de.eldoria.companies.events.company.CompanyProfileEditEvent;
import de.eldoria.companies.services.ProfileService.Outcome;
import de.eldoria.companies.services.notifications.NotificationType;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileServiceTest {
    private ServerMock server;
    private PlayerMock owner;
    private PlayerMock editor;
    private PlayerMock member;
    private PlayerMock outsider;
    private FakeStore store;
    private final List<Event> events = new ArrayList<>();
    private final List<UUID> notified = new ArrayList<>();
    private ProfileService service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        owner = server.addPlayer("Owner");
        editor = server.addPlayer("Editor");
        member = server.addPlayer("Member");
        outsider = server.addPlayer("Outsider");
        var profile = new CompanyProfile(
                7,
                "Acme",
                LocalDateTime.of(2026, 1, 2, 3, 4),
                1,
                CompanyDetails.NONE,
                List.of(
                        CompanyMember.of(7, owner.getUniqueId(), CompanyPermission.OWNER.mask()),
                        CompanyMember.of(7, editor.getUniqueId(), CompanyPermission.EDIT_PROFILE.mask()),
                        CompanyMember.of(7, member.getUniqueId(), CompanyPermission.INVITE.mask())));
        store = new FakeStore(profile);
        service = new ProfileService(
                store,
                ProfileSettings::new,
                events::add,
                (recipient, type, args) -> {
                    assertEquals(NotificationType.PROFILE_CHANGED, type);
                    notified.add(recipient);
                },
                Runnable::run);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Outcome edit(OfflinePlayer player, ProfileChange change) {
        return service.edit(player, change).join();
    }

    private static String refusal(Outcome outcome) {
        return assertInstanceOf(Outcome.Refused.class, outcome).failure().localeKey();
    }

    @Test
    void theOwnerMayAlwaysEdit() {
        var outcome = edit(owner, ProfileChange.tag("acme"));

        var saved = assertInstanceOf(Outcome.Saved.class, outcome);
        assertEquals(Optional.of("ACME"), saved.current().tag());
        assertEquals("ACME", store.saved.tag());
    }

    @Test
    void membersWithThePermissionMayEdit() {
        assertInstanceOf(Outcome.Saved.class, edit(editor, ProfileChange.description("We dig.")));
    }

    @Test
    void membersWithoutThePermissionAreRefused() {
        assertEquals("error.permission.editProfile", refusal(edit(member, ProfileChange.description("We dig."))));
        assertNull(store.saved);
        assertTrue(events.isEmpty());
    }

    @Test
    void playersWithoutCompanyAreRefused() {
        assertEquals("error.noMember", refusal(edit(outsider, ProfileChange.description("We dig."))));
    }

    @Test
    void invalidValuesAreNotSaved() {
        assertEquals("company.profile.edit.error.colorUnknown", refusal(edit(owner, ProfileChange.color("pink"))));
        assertNull(store.saved);
    }

    @Test
    void aTagUsedByAnotherCompanyIsRefused() {
        store.result = DetailsResult.TAG_TAKEN;

        assertEquals("company.profile.edit.error.tagTaken", refusal(edit(owner, ProfileChange.tag("MINE"))));
        assertTrue(events.isEmpty());
        assertTrue(notified.isEmpty());
    }

    @Test
    void aFailedSaveIsReported() {
        store.result = DetailsResult.FAILED;

        assertEquals("company.profile.edit.error.failed", refusal(edit(owner, ProfileChange.tag("MINE"))));
    }

    @Test
    void aSavedEditFiresTheEventWithBothStates() {
        edit(editor, ProfileChange.color("gold"));

        var event = assertInstanceOf(CompanyProfileEditEvent.class, events.getFirst());
        assertEquals(Optional.empty(), event.previous().color());
        assertEquals(Optional.of("gold"), event.company().color());
        assertSame(editor, event.player());
    }

    @Test
    void theOtherMembersAreNotified() {
        edit(editor, ProfileChange.color("gold"));

        assertEquals(List.of(owner.getUniqueId(), member.getUniqueId()), notified);
    }

    @Test
    void anEditChangingNothingIsNotSaved() {
        var outcome = edit(owner, ProfileChange.tag(""));

        assertInstanceOf(Outcome.Saved.class, outcome);
        assertNull(store.saved);
        assertTrue(events.isEmpty());
        assertFalse(notified.contains(owner.getUniqueId()));
    }

    private static final class FakeStore implements ProfileService.Store {
        private final CompanyProfile profile;
        private DetailsResult result = DetailsResult.SAVED;
        private CompanyDetails saved;

        private FakeStore(CompanyProfile profile) {
            this.profile = profile;
        }

        @Override
        public CompletableFuture<Optional<CompanyProfile>> profileOf(OfflinePlayer player) {
            return CompletableFuture.completedFuture(
                    profile.member(player).isPresent() ? Optional.of(profile) : Optional.empty());
        }

        @Override
        public CompletableFuture<DetailsResult> save(CompanyProfile company, CompanyDetails details) {
            if (result == DetailsResult.SAVED) saved = details;
            return CompletableFuture.completedFuture(result);
        }
    }
}
