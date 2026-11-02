/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.job;

import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuService;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * One job, or the stock of a harvest job, with the actions the player may take on it.
 * <p>
 * Every action is a command of {@code /order job} or {@code /company job}, so a button does exactly what typing it
 * does. In chat an action that changes the job for good is suggested rather than run, so the player sends it on
 * purpose, and one that needs a text suggests the command to be completed; as dialog it asks to confirm first, and one
 * that needs hours or a text takes them from inputs of the dialog named after the action, the hours on a slider. A
 * text left empty brings the menu back.
 */
@SuppressWarnings("UnstableApiUsage")
public final class JobMenu implements Menu<JobMenu.Model> {
    /**
     * Allowed length of a text typed into the dialog unless the action says otherwise.
     */
    static final int MAX_INPUT = 256;

    /**
     * The most hours the hours slider of the dialog offers.
     */
    static final int MAX_HOURS = 720;

    private final MessageSender messages;
    private final Supplier<MenuService> menus;
    private final ConfirmFlow<String> confirms;

    /**
     * @param messages renders the texts
     * @param menus    shows the confirmation of an action as dialog
     * @param confirms asks to confirm an action and runs its command once confirmed
     */
    public JobMenu(MessageSender messages, Supplier<MenuService> menus, ConfirmFlow<String> confirms) {
        this.messages = messages;
        this.menus = menus;
        this.confirms = confirms;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions buttons) {
        var components = new MenuComponents(messages, player);
        var text = MenuText.create().append(model.title());
        var lines = model.lines();
        for (var i = 0; i < lines.size(); i++) {
            var key = Keys.of("line", i);
            text.newLine().text("<" + key + ">", Values.component(key, components.of(lines.get(i))));
        }
        var actions = model.actions();
        if (actions.isEmpty()) return text;
        text.newLine();
        for (var i = 0; i < actions.size(); i++) {
            var action = actions.get(i);
            var key = Keys.of("act", i);
            var label = "<" + action.style() + ">[<i18n:" + action.label() + ">]</" + key + "> ";
            switch (action.kind()) {
                case RUN ->
                    text.text(
                            "<" + key + ">" + label,
                            buttons.chatAction(key, clicked -> clicked.performCommand(action.command())));
                case CHECK ->
                    text.text(
                            "<" + key + ">" + label,
                            Values.click(key, ClickEvent.suggestCommand("/" + action.command())));
                case ASK ->
                    text.text(
                            "<" + key + ">" + label,
                            Values.click(key, ClickEvent.suggestCommand("/" + action.command() + " ")));
            }
        }
        return text;
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions buttons) {
        var components = new MenuComponents(messages, player);
        List<DialogBody> body = new ArrayList<>();
        for (var line : model.lines()) body.add(DialogBody.plainMessage(components.of(line)));
        List<DialogInput> inputs = new ArrayList<>();
        List<ActionButton> list = new ArrayList<>();
        var actions = model.actions();
        for (var i = 0; i < actions.size(); i++) {
            var action = actions.get(i);
            var label = components.locale(action.label());
            switch (action.kind()) {
                case RUN ->
                    list.add(buttons.button(
                            label, null, (clicked, response) -> clicked.performCommand(action.command())));
                case CHECK ->
                    list.add(buttons.button(
                            label,
                            null,
                            (clicked, response) -> confirms.ask(
                                    menus.get(),
                                    clicked,
                                    action.command(),
                                    MenuText.create()
                                            .locale(
                                                    "jobs.menu.confirm",
                                                    Values.component("action", label),
                                                    Values.component("job", components.of(model.name()))))));
                case ASK -> {
                    var hoursKey = Keys.of("hours", i);
                    var textKey = Keys.of("input", i);
                    if (action.hours()) {
                        inputs.add(DialogInput.numberRange(
                                        hoursKey, named(components, action, "jobs.menu.hoursOnly"), 1, MAX_HOURS)
                                .step(1f)
                                .initial(1f)
                                .width(300)
                                .build());
                    }
                    if (action.input() != null) {
                        inputs.add(DialogInput.text(textKey, named(components, action, action.input()))
                                .maxLength(action.maxLength())
                                .width(300)
                                .build());
                    }
                    list.add(buttons.button(label, null, (clicked, response) -> {
                        var command = new StringBuilder(action.command());
                        if (action.hours()) {
                            var hours = response.getFloat(hoursKey);
                            command.append(' ').append(hours == null ? 1 : Math.round(hours));
                        }
                        if (action.input() != null) {
                            var typed = response.getText(textKey);
                            if (typed == null || typed.isBlank()) {
                                messages.sendErrorActionBar(clicked, "jobs.menu.inputMissing");
                                menus.get().show(clicked, this, model);
                                return;
                            }
                            command.append(' ').append(typed.strip());
                        }
                        clicked.performCommand(command.toString());
                    }));
                }
            }
        }
        var base = DialogBase.builder(components.of(model.title()))
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .inputs(inputs)
                .build();
        var type = list.isEmpty()
                ? DialogType.notice()
                : DialogType.multiAction(list).columns(1).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    private static Component named(MenuComponents components, Action action, String input) {
        return components.locale(
                "jobs.menu.input",
                Values.component("action", components.locale(action.label())),
                Values.component("input", components.locale(input)));
    }

    /**
     * How an action is taken.
     */
    public enum Kind {
        /**
         * Runs at once; for looking at something or work that can be repeated.
         */
        RUN,
        /**
         * Changes the job for good; suggested in chat, confirmed in a dialog.
         */
        CHECK,
        /**
         * Needs a text, appended to the command: suggested in chat, typed into an input of the dialog.
         */
        ASK
    }

    /**
     * An action on a job.
     *
     * @param label     key of the button's locale text
     * @param style     MiniMessage tag colouring the button, such as {@code add}, {@code remove} or {@code show}
     * @param command   the command it runs, without the slash
     * @param kind      how it is taken
     * @param input     key of the locale text naming the text an {@link Kind#ASK} action needs, null for none
     * @param maxLength the longest text the dialog takes
     * @param hours     whether an {@link Kind#ASK} action needs hours first, picked on a slider in the dialog
     */
    public record Action(
            String label,
            String style,
            String command,
            Kind kind,
            @Nullable String input,
            int maxLength,
            boolean hours) {
        /**
         * An action that needs nothing typed.
         *
         * @param label   key of the button's locale text
         * @param style   MiniMessage tag colouring the button
         * @param command the command it runs, without the slash
         * @param kind    how it is taken
         */
        public Action(String label, String style, String command, Kind kind) {
            this(label, style, command, kind, null, MAX_INPUT, false);
        }

        /**
         * An action that needs a text.
         *
         * @param label     key of the button's locale text
         * @param style     MiniMessage tag colouring the button
         * @param command   the command it runs, without the slash
         * @param input     key of the locale text naming the text
         * @param maxLength the longest text taken
         * @return the action
         */
        public static Action text(String label, String style, String command, String input, int maxLength) {
            return new Action(label, style, command, Kind.ASK, input, maxLength, false);
        }

        /**
         * An action that needs hours and, unless {@code input} is null, a text after them.
         *
         * @param label     key of the button's locale text
         * @param style     MiniMessage tag colouring the button
         * @param command   the command it runs, without the slash
         * @param input     key of the locale text naming the text, null for hours alone
         * @param maxLength the longest text taken
         * @return the action
         */
        public static Action hours(String label, String style, String command, @Nullable String input, int maxLength) {
            return new Action(label, style, command, Kind.ASK, input, maxLength, true);
        }
    }

    /**
     * What the menu shows.
     *
     * @param title   the heading of the menu
     * @param name    the job in a few words, as a confirmation names it
     * @param lines   what is known about it, one text per line
     * @param actions what the player may do with it
     */
    public record Model(MenuText title, MenuText name, List<MenuText> lines, List<Action> actions) {
        /**
         * Keeps the lines and actions unchangeable.
         */
        public Model {
            lines = List.copyOf(lines);
            actions = List.copyOf(actions);
        }
    }
}
