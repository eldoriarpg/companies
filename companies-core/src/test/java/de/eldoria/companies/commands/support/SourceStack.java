/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.support;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * A command source of a sender, without location or executor.
 */
record SourceStack(CommandSender sender) implements CommandSourceStack {
    @Override
    public Location getLocation() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CommandSender getSender() {
        return sender;
    }

    @Override
    public @Nullable Entity getExecutor() {
        return null;
    }

    @Override
    public CommandSourceStack withLocation(Location location) {
        return this;
    }

    @Override
    public CommandSourceStack withExecutor(Entity executor) {
        return this;
    }
}
