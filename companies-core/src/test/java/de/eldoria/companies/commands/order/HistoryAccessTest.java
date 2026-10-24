/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.companies.data.wrapper.history.HistorySnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryAccessTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 12, 0);
    private static final HistorySnapshot EMPTY = new HistorySnapshot(List.of(), 1, 0, 0, 0, 0, List.of());

    private final UUID owner = UUID.randomUUID();

    @Test
    void ownerSeesOnlyOwnOwnerEntries() {
        var own = HistoryEntry.ofOwner(1, owner, HistoryOutcome.RECEIVED, "o", NOW, NOW, EMPTY);
        var foreign = HistoryEntry.ofOwner(1, UUID.randomUUID(), HistoryOutcome.RECEIVED, "o", NOW, NOW, EMPTY);
        var company = HistoryEntry.ofCompany(1, 4, HistoryOutcome.DELIVERED, "o", NOW, NOW, EMPTY);

        assertTrue(HistoryCommand.ownedBy(Optional.of(own), owner).isPresent());
        assertTrue(HistoryCommand.ownedBy(Optional.of(foreign), owner).isEmpty());
        assertTrue(HistoryCommand.ownedBy(Optional.of(company), owner).isEmpty());
        assertTrue(HistoryCommand.ownedBy(Optional.empty(), owner).isEmpty());
    }
}
