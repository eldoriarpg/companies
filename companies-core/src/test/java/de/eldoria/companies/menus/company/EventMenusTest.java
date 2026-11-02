/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.event.EventKind;
import de.eldoria.companies.components.event.EventState;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource.SourceType;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.data.wrapper.offer.ContestEntry;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The menus of {@code /company events} and {@code /company contest}: the offers grouped under their server event with
 * an open button each, the contests with an info button and a join button where the company may join, and a contest
 * with the buttons its state and the company allow.
 */
class EventMenusTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 12, 0);

    private Plugin plugin;
    private MessageSender messages;
    private PlayerMock player;
    private MenuActions actions;

    @BeforeEach
    void setUp() {
        var server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        messages = StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        player = server.addPlayer("Viewer");
        actions = new MenuActions(plugin, player, MenuActions.DEFAULT_LIFETIME);
    }

    @AfterEach
    void tearDown() {
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    @Test
    void theOffersOfAnEventAreListedUnderItEachWithAnOpenButton() {
        var event = event();
        var inEvent = offer(1, "Harvest goal", OfferState.ACTIVE, NOW, 7);
        var alone = offer(2, "Log race", OfferState.ACTIVE, NOW, null);
        var model = new EventsMenu.Model(List.of(event), List.of(alone, inEvent), Map.of(2, OptionalInt.of(3)), NOW);

        var text = new EventsMenu(messages, EventMenusTest::money, (clicker, offer) -> {}).chat(player, model, actions);

        var built = text.build();
        assertTrue(built.indexOf("<row_1>") < built.indexOf("<row_2>"));
        var shown = plain(text);
        var lines = shown.split("\n");
        assertTrue(lines[1].contains("company.events.eventEntry"), shown);
        assertTrue(lines[2].startsWith("  [offers.kind.contest]"), shown);
        assertFalse(lines[2].contains("company.events.place"), shown);
        assertTrue(lines[3].contains("company.events.place"), shown);
        assertTrue(lines[2].endsWith("[company.events.open]") && lines[3].endsWith("[company.events.open]"), shown);
    }

    @Test
    void contestsOfferToJoinOnlyToManagersWhereTheyStarted() {
        var started = offer(1, "Log race", OfferState.ACTIVE, NOW, null);
        var waiting = offer(2, "Stone race", OfferState.SCHEDULED, null, null);
        var joined = offer(3, "Sand race", OfferState.ACTIVE, NOW, null);
        var model = new ContestsMenu.Model(List.of(started, waiting, joined), Set.of(3), true, NOW);

        var built = new ContestsMenu(messages, EventMenusTest::money, new NoActions())
                .chat(player, model, actions)
                .build();

        assertTrue(built.contains("<act_info_0>") && built.contains("<act_info_2>"), built);
        assertTrue(built.contains("<act_join_0>"), built);
        assertFalse(built.contains("<act_join_1>"), built);
        assertFalse(built.contains("<act_join_2>"), built);
        var withoutPermission = new ContestsMenu.Model(List.of(started), Set.of(), false, NOW);
        assertFalse(new ContestsMenu(messages, EventMenusTest::money, new NoActions())
                .chat(player, withoutPermission, actions)
                .build()
                .contains("<act_join_0>"));
    }

    @Test
    void aJoinedContestOffersToDeliverAndToLeaveToManagers() {
        var contest = offer(1, "Log race", OfferState.ACTIVE, NOW, null);
        var entry = new ContestEntry(1, 7, "Miners", 30, 60, NOW, null, NOW);

        var managing = contest(contest, Optional.of(entry), true);
        var working = contest(contest, Optional.of(entry), false);
        var outside = contest(contest, Optional.empty(), true);

        assertTrue(managing.contains("company.contest.deliverAll") && managing.contains("company.contest.leave"));
        assertTrue(working.contains("company.contest.deliverAll"), working);
        assertFalse(working.contains("company.contest.leave"), working);
        assertTrue(outside.contains("company.contest.join"), outside);
        assertFalse(outside.contains("company.contest.deliverAll"), outside);
        assertTrue(outside.contains("company.contest.back"), outside);
    }

    @Test
    void aPausedContestTakesNoDeliveriesAndACompanyThatLeftMayJoinAgain() {
        var paused = offer(1, "Log race", OfferState.PAUSED, NOW, null);
        var active = offer(2, "Sand race", OfferState.ACTIVE, NOW, null);
        var joined = new ContestEntry(1, 7, "Miners", 30, 60, NOW, null, NOW);
        var left = new ContestEntry(2, 7, "Miners", 30, 60, NOW, NOW, NOW);

        var whilePaused = contest(paused, Optional.of(joined), true);
        var afterLeaving = contest(active, Optional.of(left), true);

        assertFalse(whilePaused.contains("company.contest.deliverAll"), whilePaused);
        assertTrue(whilePaused.contains("company.contest.leave"), whilePaused);
        assertTrue(afterLeaving.contains("company.contest.join"), afterLeaving);
        assertFalse(afterLeaving.contains("company.contest.deliverAll"), afterLeaving);
    }

    @Test
    void aClosedContestOnlyLeadsBack() {
        var closed = offer(1, "Log race", OfferState.ENDED, NOW, null);
        var entry = new ContestEntry(1, 7, "Miners", 30, 60, NOW, null, NOW);

        var built = contest(closed, Optional.of(entry), true);

        assertFalse(built.contains("company.contest.deliverAll"), built);
        assertFalse(built.contains("company.contest.leave"), built);
        assertTrue(built.contains("company.contest.back"), built);
    }

    private String contest(EventOffer contest, Optional<ContestEntry> own, boolean mayManage) {
        var model = new ContestMenu.Model(
                contest,
                own.map(List::of).orElse(List.of()),
                own,
                own.isPresent() ? OptionalInt.of(1) : OptionalInt.empty(),
                true,
                mayManage,
                NOW,
                Optional.empty());
        return new ContestMenu(messages, EventMenusTest::money, new NoActions())
                .chat(player, model, actions)
                .build();
    }

    private String plain(MenuText text) {
        return PlainTextComponentSerializer.plainText()
                .serialize(messages.serializeMessage(
                        player, text.build(), text.replacements().toArray(TagResolver[]::new)));
    }

    private static String money(double value) {
        return String.format(Locale.ROOT, "$%.2f", value);
    }

    private static ServerEvent event() {
        return new ServerEvent(
                7,
                "Harvest week",
                EventKind.TAX_REDUCTION,
                5,
                List.of(),
                List.of(),
                NOW.minusHours(1),
                NOW.plusDays(1),
                null,
                null,
                null,
                0,
                EventState.ACTIVE,
                null,
                "Ada",
                NOW,
                null,
                null);
    }

    private static EventOffer offer(int id, String name, OfferState state, LocalDateTime started, Integer event) {
        var contest = EventOffer.contest(
                name,
                OfferLine.material("OAK_LOG"),
                100,
                1.0,
                List.of(50.0),
                SourceType.MINTED,
                null,
                150,
                NOW.minusHours(1),
                NOW.plusDays(1),
                "Admin",
                NOW);
        return new EventOffer(
                id,
                contest.kind(),
                name,
                event,
                contest.line(),
                contest.amount(),
                contest.unitPrice(),
                0,
                0,
                contest.source(),
                null,
                contest.budget(),
                0,
                30,
                contest.starts(),
                contest.ends(),
                state,
                "Admin",
                NOW,
                state.closed() ? NOW : null,
                contest.prizes(),
                0,
                started,
                null,
                null,
                null);
    }

    private static final class NoActions implements ContestMenu.Handlers, ContestsMenu.Handlers {
        @Override
        public void deliverAll(Player player, EventOffer contest) {}

        @Override
        public void join(Player player, EventOffer contest) {}

        @Override
        public void leave(Player player, EventOffer contest) {}

        @Override
        public void list(Player player) {}

        @Override
        public void info(Player player, EventOffer contest) {}
    }
}
