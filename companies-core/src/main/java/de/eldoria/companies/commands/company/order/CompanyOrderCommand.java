/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.commands.CommandDependencies;
import net.strokkur.commands.Subcommand;

/**
 * {@code /company order}: the orders of the sender's company.
 */
@Subcommand("order")
public final class CompanyOrderCommand {
    // TODO list, the default executor: the orders claimed by the company
    // TODO info <id>
    // TODO accept <id>
    // TODO abort <id> and abort confirm
    // TODO deliver <id> <line> <amount|max> and deliver <id> all
    // TODO search with page, query and its fields

    /**
     * @param deps what the commands need
     */
    public CompanyOrderCommand(CommandDependencies deps) {}
}
