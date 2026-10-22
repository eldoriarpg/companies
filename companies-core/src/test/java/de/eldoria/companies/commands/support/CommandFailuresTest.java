/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.support;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandFailuresTest {
    private final List<CommandFailure> reported = new ArrayList<>();
    private final List<CommandSender> receivers = new ArrayList<>();
    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        CommandFailures.use((sender, failure) -> {
            receivers.add(sender);
            reported.add(failure);
        });
    }

    @AfterEach
    void tearDown() {
        CommandFailures.clear();
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    private static CommandContext<CommandSourceStack> context(CommandSender sender) {
        return new CommandContext<>(
                new SourceStack(sender),
                "company",
                Map.of(),
                null,
                null,
                List.of(),
                StringRange.at(0),
                null,
                null,
                false);
    }

    @Test
    void passesTheResultOfASucceedingExecutor() throws CommandSyntaxException {
        var player = server.addPlayer();
        var result = CommandFailures.wrap(ctx -> Command.SINGLE_SUCCESS).run(context(player));

        assertEquals(Command.SINGLE_SUCCESS, result);
        assertTrue(reported.isEmpty());
    }

    @Test
    void reportsAFailureToTheSenderAndReturnsZero() throws CommandSyntaxException {
        var player = server.addPlayer();
        var failure = CommandFailure.of("error.noMember");

        var result = CommandFailures.wrap(ctx -> {
                    throw failure;
                })
                .run(context(player));

        assertEquals(0, result);
        assertSame(failure, reported.getFirst());
        assertSame(player, receivers.getFirst());
    }

    @Test
    void clearedFailuresAreNotReported() throws CommandSyntaxException {
        CommandFailures.clear();

        var result = CommandFailures.wrap(ctx -> {
                    throw CommandFailure.of("error.noMember");
                })
                .run(context(server.addPlayer()));

        assertEquals(0, result);
        assertTrue(reported.isEmpty());
    }

    @Test
    void failureIsSentAsErrorMessage() {
        var messages = StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        var player = server.addPlayer();

        CommandFailure.of("Not a member").sendTo(player, messages);

        var message = player.nextComponentMessage();
        assertNotNull(message);
        assertTrue(PlainTextComponentSerializer.plainText().serialize(message).endsWith("Not a member"));
    }

    @Test
    void actionBarFailureGoesToTheChatOfTheConsole() {
        var messages = StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        var console = server.getConsoleSender();

        CommandFailure.actionBar("Only players").sendTo(console, messages);

        var message = console.nextComponentMessage();
        assertNotNull(message);
        assertTrue(PlainTextComponentSerializer.plainText().serialize(message).endsWith("Only players"));
    }
}
