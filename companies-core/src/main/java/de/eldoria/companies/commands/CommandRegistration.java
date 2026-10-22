/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands;

import io.papermc.paper.command.brigadier.Commands;

/**
 * Registers the command trees of the plugin.
 */
public final class CommandRegistration {
    private CommandRegistration() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Registers {@code /company}, {@code /order} and {@code /companyadmin}. Called by the commands lifecycle event,
     * which fires again when the server reloads its commands.
     *
     * @param registrar registrar of the lifecycle event
     * @param deps      what the commands need
     */
    public static void register(Commands registrar, CommandDependencies deps) {
        CompanyCommandBrigadier.register(registrar, deps);
        OrderCommandBrigadier.register(registrar, deps);
        CompanyAdminCommandBrigadier.register(registrar, deps);
    }
}
