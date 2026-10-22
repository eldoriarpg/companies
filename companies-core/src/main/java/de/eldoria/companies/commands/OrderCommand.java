/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands;

import de.eldoria.companies.commands.support.HandleFailures;
import net.strokkur.commands.Command;
import net.strokkur.commands.paper.Description;

/**
 * {@code /order}: creating and managing the sender's own orders.
 */
@Command("order")
@Description("Manage your orders")
@HandleFailures
final class OrderCommand {
    // TODO list, the default executor: the sender's orders
    // TODO info <id>
    // TODO cancel <id>
    // TODO receive <id>
    // TODO collect
    // TODO extend <id>
    // TODO raise <id>
    // TODO create <name…>, starting a draft
    // TODO draft name|add|remove|amount|price|done|cancel …

    OrderCommand(CommandDependencies deps) {}
}
