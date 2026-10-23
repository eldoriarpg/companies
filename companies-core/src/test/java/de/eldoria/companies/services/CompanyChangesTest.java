/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.events.EventRecorder;
import de.eldoria.companies.events.company.CompanyCreateEvent;
import de.eldoria.companies.events.company.CompanyOwnerTransferEvent;
import de.eldoria.companies.events.company.CompanyRenameEvent;
import de.eldoria.companies.events.company.PreCompanyCreateEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyChangesTest {
    @TempDir
    Path directory;

    private ServerMock server;
    private ExecutorService executor;
    private SqLiteCompanyData companyData;
    private final EventRecorder events = new EventRecorder();
    private Consumer<PreCompanyCreateEvent> listener = event -> {};
    private CompanyChanges changes;
    private Player founder;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
        companyData = new SqLiteCompanyData(executor);
        changes = new CompanyChanges(
                companyData,
                server::getOfflinePlayer,
                event -> {
                    events.accept(event);
                    if (event instanceof PreCompanyCreateEvent pre) listener.accept(pre);
                },
                Logger.getAnonymousLogger(),
                executor);
        founder = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        MockBukkit.unmock();
    }

    private int company(String name) {
        var id = companyData.submitCompanyCreation(name, JoinMode.INVITE_ONLY).join();
        companyData
                .submitMemberUpdate(CompanyMember.forCompanyId(id, founder).addPermission(CompanyPermission.OWNER))
                .join();
        return id;
    }

    @Test
    void foundingGoesOnWithoutObjection() {
        assertTrue(changes.mayFound(founder, "Acme", 250.0).isEmpty());

        var event = events.single(PreCompanyCreateEvent.class);
        assertSame(founder, event.player());
        assertEquals("Acme", event.name());
        assertEquals(250.0, event.price());
        assertFalse(event.isAsynchronous());
    }

    @Test
    void foundingCancelledWithAReasonShowsTheReason() {
        var reason = Component.text("Founding is closed");
        listener = event -> event.cancel(reason);

        var failure = changes.mayFound(founder, "Acme", 250.0).orElseThrow();

        assertEquals("error.cancelledByIntegration", failure.localeKey());
        assertEquals(reason, failure.message().orElseThrow());
    }

    @Test
    void foundingCancelledWithoutAReasonShowsTheDefaultError() {
        listener = event -> event.setCancelled(true);

        var failure = changes.mayFound(founder, "Acme", 250.0).orElseThrow();

        assertEquals("error.cancelledByIntegration", failure.localeKey());
        assertTrue(failure.message().isEmpty());
    }

    @Test
    void foundedCompanyFiresWithItsProfile() {
        var id = company("Acme");

        changes.founded(id, founder).join();

        var event = events.single(CompanyCreateEvent.class);
        assertEquals(id, event.company().id());
        assertEquals("Acme", event.company().name());
        assertSame(founder, event.player());
        assertTrue(event.company().members().stream()
                .anyMatch(member -> member.uuid().equals(founder.getUniqueId())));
        assertTrue(event.isAsynchronous());
    }

    @Test
    void renameCarriesBothNames() {
        var id = company("Acme");
        var admin = UUID.randomUUID();
        companyData
                .updateCompanyName(companyData.retrieveCompanyById(id).join().orElseThrow(), "Bolt")
                .join();

        changes.renamed(id, "Acme", admin, true).join();

        var event = events.single(CompanyRenameEvent.class);
        assertEquals("Acme", event.oldName());
        assertEquals("Bolt", event.newName());
        assertEquals(admin, event.actor().orElseThrow());
        assertTrue(event.byAdmin());
        assertTrue(event.isAsynchronous());
    }

    @Test
    void ownerTransferCarriesBothOwners() {
        var id = company("Acme");
        var heir = server.addPlayer();

        changes.ownerTransferred(id, founder.getUniqueId(), heir.getUniqueId(), false)
                .join();

        var event = events.single(CompanyOwnerTransferEvent.class);
        assertEquals(founder.getUniqueId(), event.previousOwner().getUniqueId());
        assertEquals(heir.getUniqueId(), event.newOwner().getUniqueId());
        assertFalse(event.byAdmin());
    }

    @Test
    void unknownCompaniesFireNothing() {
        changes.renamed(999, "Ghost", null, false).join();
        changes.founded(999, founder).join();

        assertTrue(events.of(CompanyRenameEvent.class).isEmpty());
        assertTrue(events.of(CompanyCreateEvent.class).isEmpty());
    }
}
