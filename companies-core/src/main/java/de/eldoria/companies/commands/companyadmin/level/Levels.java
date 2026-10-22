/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.commands.support.Checks;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;

/**
 * Checks shared by the level editor commands.
 */
final class Levels {
    private Levels() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Levels are edited on the primary node only, which shares them with the others.
     *
     * @param configuration configuration of the plugin
     */
    static void requirePrimary(Configuration configuration) {
        Checks.isTrue(configuration.nodeSettings().nodeType() == NodeType.PRIMARY, "error.secondarynode");
    }

    /**
     * @param configuration configuration of the plugin
     * @param level         number of the level
     * @return the level
     */
    static CompanyLevel level(Configuration configuration, int level) {
        var optLevel = configuration.companySettings().level(level);
        Checks.isTrue(optLevel.isPresent(), "error.invalidLevel");
        return optLevel.get();
    }
}
