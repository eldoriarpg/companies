/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands;

import de.eldoria.companies.commands.support.CommandFailure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandDependenciesTest {
    private final CommandDependencies deps = new CommandDependencies(null, null, null, null, null, null, null, null);
    private final CommandDependencies.Services services = new CommandDependencies.Services(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null, null);

    @Test
    void commandsRefuseUntilTheServicesArrive() {
        assertEquals(
                "error.notReady",
                assertThrows(CommandFailure.class, deps::services).localeKey());
        assertThrows(CommandFailure.class, deps::requireReady);
    }

    @Test
    void providedServicesLetTheCommandsRun() {
        deps.provide(services);

        assertSame(services, deps.services());
        assertDoesNotThrow(deps::requireReady);
    }

    @Test
    void withdrawnServicesRefuseAgain() {
        deps.provide(services);
        deps.withdraw();

        assertThrows(CommandFailure.class, deps::requireReady);
    }
}
