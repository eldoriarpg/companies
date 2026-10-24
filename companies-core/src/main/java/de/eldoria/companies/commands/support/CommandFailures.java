/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.support;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

import java.util.function.BiConsumer;

/**
 * Turns a {@link CommandFailure} thrown by an executor into an error message for the sender.
 * <p>
 * StrokkCommands calls executor wrappers statically, so the plugin hands in how failures are reported when it
 * enables and takes it back when it disables.
 */
public final class CommandFailures {
    private static final BiConsumer<CommandSender, CommandFailure> SILENT = (sender, failure) -> {};
    private static volatile BiConsumer<CommandSender, CommandFailure> reporter = SILENT;

    private CommandFailures() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @param failureReporter reports a failure to the sender of the failed command
     */
    public static void use(BiConsumer<CommandSender, CommandFailure> failureReporter) {
        reporter = failureReporter;
    }

    /**
     * Stops reporting failures.
     */
    public static void clear() {
        reporter = SILENT;
    }

    /**
     * Wraps an executor, so a {@link CommandFailure} it throws is reported instead of reaching Brigadier.
     *
     * @param executor executor to wrap
     * @return the wrapped executor, returning 0 when the command failed
     */
    @HandleFailures
    public static Command<CommandSourceStack> wrap(Command<CommandSourceStack> executor) {
        return context -> {
            try {
                return executor.run(context);
            } catch (CommandFailure failure) {
                reporter.accept(context.getSource().getSender(), failure);
                return 0;
            }
        };
    }
}
