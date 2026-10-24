/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import de.eldoria.companies.util.Confirmations;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmFlowTest {
    private final List<String> confirmed = new ArrayList<>();
    private final List<String> answers = new ArrayList<>();
    private Instant now = Instant.parse("2026-01-01T12:00:00Z");
    private Player player;
    private ConfirmFlow<String> flow;

    @BeforeEach
    void setUp() {
        var server = MockBukkit.mock();
        player = server.addPlayer();
        flow = new ConfirmFlow<>(
                new Confirmations<>(() -> now),
                new ConfirmMenu(MockBukkit.createMockPlugin()),
                (clicked, value) -> confirmed.add(value),
                clicked -> answers.add("expired"),
                clicked -> answers.add("canceled"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ConfirmMenu.Prompt ask(String value) {
        return flow.prompt(player, value, MenuText.create().text(value));
    }

    @Test
    void confirmButtonPerformsTheActionOnce() {
        var prompt = ask("name");

        prompt.onConfirm().accept(player);
        prompt.onConfirm().accept(player);

        assertEquals(List.of("name"), confirmed);
        assertEquals(List.of("expired"), answers);
    }

    @Test
    void typedConfirmPerformsTheAction() {
        ask("name");

        flow.confirm(player);

        assertEquals(List.of("name"), confirmed);
        assertTrue(answers.isEmpty());
    }

    @Test
    void typedConfirmUsesUpTheButton() {
        var prompt = ask("name");

        flow.confirm(player);
        prompt.onConfirm().accept(player);

        assertEquals(List.of("name"), confirmed);
        assertEquals(List.of("expired"), answers);
    }

    @Test
    void confirmationExpiresAfterThirtySeconds() {
        var prompt = ask("name");
        now = now.plus(Confirmations.VALIDITY);

        prompt.onConfirm().accept(player);

        assertTrue(confirmed.isEmpty());
        assertEquals(List.of("expired"), answers);
    }

    @Test
    void typedConfirmWithoutRequestAnswersExpired() {
        flow.confirm(player);

        assertTrue(confirmed.isEmpty());
        assertEquals(List.of("expired"), answers);
    }

    @Test
    void denyButtonDropsTheRequest() {
        var prompt = ask("name");

        prompt.onCancel().accept(player);
        flow.confirm(player);

        assertTrue(confirmed.isEmpty());
        assertEquals(List.of("canceled", "expired"), answers);
    }

    @Test
    void typedCancelDropsTheRequest() {
        var prompt = ask("name");

        flow.cancel(player);
        prompt.onConfirm().accept(player);

        assertTrue(confirmed.isEmpty());
        assertEquals(List.of("canceled", "expired"), answers);
    }

    @Test
    void earlierButtonsLeaveTheNewerRequestAlone() {
        var first = ask("first");
        ask("second");

        first.onConfirm().accept(player);
        first.onCancel().accept(player);
        flow.confirm(player);

        assertEquals(List.of("second"), confirmed);
        assertEquals(List.of("expired", "canceled"), answers);
    }

    private ConfirmMenu.Prompt askWithAlternative(String value, List<String> chosen) {
        return flow.prompt(
                player,
                value,
                MenuText.create().text(value),
                MenuText.create().text("Pay myself"),
                new ConfirmFlow.Alternative<>(
                        MenuText.create().text("Pay from treasury"),
                        (clicked, confirmedValue) -> chosen.add(confirmedValue)));
    }

    @Test
    void alternativeButtonPerformsItsOwnActionOnce() {
        List<String> chosen = new ArrayList<>();
        var prompt = askWithAlternative("name", chosen);

        prompt.alternative().orElseThrow().onChoose().accept(player);
        prompt.onConfirm().accept(player);

        assertEquals(List.of("name"), chosen);
        assertTrue(confirmed.isEmpty());
        assertEquals(List.of("expired"), answers);
    }

    @Test
    void confirmButtonOfAPromptWithAlternativeUsesItUp() {
        List<String> chosen = new ArrayList<>();
        var prompt = askWithAlternative("name", chosen);

        prompt.onConfirm().accept(player);
        prompt.alternative().orElseThrow().onChoose().accept(player);

        assertEquals(List.of("name"), confirmed);
        assertTrue(chosen.isEmpty());
        assertEquals(List.of("expired"), answers);
    }

    @Test
    void typedAlternativePerformsItsAction() {
        List<String> chosen = new ArrayList<>();
        askWithAlternative("name", chosen);

        flow.confirmWith(player, (clicked, value) -> chosen.add(value));
        flow.confirm(player);

        assertEquals(List.of("name"), chosen);
        assertTrue(confirmed.isEmpty());
        assertEquals(List.of("expired"), answers);
    }
}
