/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.eldoria.companies.commands.suggest.MembershipSources.PlayerMembership;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.util.Times;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembershipProvidersTest {
    private static final Duration LIFETIME = Duration.ofMinutes(1);
    private static final List<SimpleCompany> COMPANIES = List.of(
            company(1, "Miners", JoinMode.OPEN),
            company(2, "Iron Works", JoinMode.APPLICATIONS),
            company(3, "Ironclad", JoinMode.APPLICATIONS),
            company(4, "Secret", JoinMode.INVITE_ONLY),
            company(5, "Millers", JoinMode.OPEN));

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        var membership =
                new PlayerMembership(List.of("Miners", "Farmers"), List.of("Bakers"), List.of("Alex"), List.of("Bob"));
        MembershipSources.use(new MembershipSources(
                new SuggestionCache<>(LIFETIME, Runnable::run, player -> membership),
                new SuggestionCache<>(
                        LIFETIME, Runnable::run, mode -> MembershipSources.companyNames(COMPANIES, mode))));
    }

    @AfterEach
    void tearDown() {
        MembershipSources.clear();
        MockBukkit.unmock();
    }

    private static SimpleCompany company(int id, String name, JoinMode mode) {
        return new SimpleCompany(id, name, Times.now(), 1, CompanyDetails.NONE.withJoinMode(mode));
    }

    private static List<String> texts(CompletableFuture<Suggestions> suggestions) {
        return suggestions.join().getList().stream().map(Suggestion::getText).toList();
    }

    private static SuggestionsBuilder typed(String input) {
        return new SuggestionsBuilder(input, 0);
    }

    private static CommandContext<CommandSourceStack> context(CommandSender sender) {
        return new CommandContext<>(
                new Source(sender), "company", Map.of(), null, null, List.of(), StringRange.at(0), null, null, false);
    }

    @Test
    void companyNamesAreFilteredByJoinMode() {
        assertEquals(List.of("Secret"), MembershipSources.companyNames(COMPANIES, JoinMode.INVITE_ONLY));
        assertEquals(List.of("Miners", "Millers"), MembershipSources.companyNames(COMPANIES, JoinMode.OPEN));
    }

    @Test
    void openCompaniesAreSuggestedForJoining() {
        var player = context(server.addPlayer());

        assertEquals(List.of("Millers", "Miners"), texts(MembershipProviders.openCompanies(player, typed("mi"))));
        assertTrue(
                texts(MembershipProviders.openCompanies(player, typed("Iron"))).isEmpty());
    }

    @Test
    void companiesAcceptingApplicationsAreQuotedWhereNeeded() {
        var player = context(server.addPlayer());

        assertEquals(
                List.of("\"Iron Works\"", "Ironclad"), texts(MembershipProviders.applyingCompanies(player, typed(""))));
    }

    @Test
    void theDataOfThePlayerIsSuggested() {
        var player = context(server.addPlayer());

        assertEquals(List.of("Miners"), texts(MembershipProviders.invitingCompanies(player, typed("Mi"))));
        assertEquals(List.of("Bakers"), texts(MembershipProviders.appliedCompanies(player, typed(""))));
        assertEquals(List.of("Alex"), texts(MembershipProviders.invitedPlayers(player, typed("a"))));
        assertEquals(List.of("Bob"), texts(MembershipProviders.applicants(player, typed(""))));
    }

    @Test
    void theConsoleGetsNoPlayerData() {
        var console = context(server.getConsoleSender());

        assertTrue(
                texts(MembershipProviders.invitingCompanies(console, typed(""))).isEmpty());
    }

    @Test
    void nothingIsSuggestedWhileThePluginIsDisabled() {
        MembershipSources.clear();
        var player = context(server.addPlayer());

        assertTrue(texts(MembershipProviders.openCompanies(player, typed(""))).isEmpty());
        assertTrue(texts(MembershipProviders.applicants(player, typed(""))).isEmpty());
    }

    /**
     * A command source of a sender, without location or executor.
     *
     * @param sender the sender
     */
    private record Source(CommandSender sender) implements CommandSourceStack {
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
}
