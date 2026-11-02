/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.job;

import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A list of jobs, each with an info button that opens the job, as {@code /order job info} or {@code /company job info}
 * does. As dialog every job is a button of its own, named by its id and title, with the rest shown when hovered.
 */
@SuppressWarnings("UnstableApiUsage")
public final class JobListMenu implements Menu<JobListMenu.Model> {
    private final MessageSender messages;

    /**
     * @param messages renders the texts
     */
    public JobListMenu(MessageSender messages) {
        this.messages = messages;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions buttons) {
        var components = new MenuComponents(messages, player);
        var text = MenuText.create().text("<heading>").append(model.title());
        if (model.entries().isEmpty()) return text.newLine().append(model.empty());
        var entries = model.entries();
        for (var i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            var line = Keys.of("job", i);
            var info = Keys.of("act_info", i);
            text.newLine()
                    .text("<" + line + "> ", Values.component(line, components.of(entry.line())))
                    .text(
                            "<" + info + "><show>[<i18n:words.info>]</" + info + ">",
                            buttons.chatAction(info, clicked -> clicked.performCommand(entry.info())));
        }
        return text;
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions buttons) {
        var components = new MenuComponents(messages, player);
        List<DialogBody> body = new ArrayList<>();
        if (model.entries().isEmpty()) body.add(DialogBody.plainMessage(components.of(model.empty())));
        List<ActionButton> list = new ArrayList<>();
        for (var entry : model.entries()) {
            list.add(buttons.button(
                    components.of(entry.label()),
                    components.of(entry.line()),
                    (clicked, response) -> clicked.performCommand(entry.info())));
        }
        var base = DialogBase.builder(components.of(model.title()))
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .build();
        var type = list.isEmpty()
                ? DialogType.notice()
                : DialogType.multiAction(list).columns(1).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    /**
     * A job of the list.
     *
     * @param label the job in a few words, the text of its dialog button
     * @param line  what the list shows of it, the tooltip of its dialog button
     * @param info  the command opening it, without the slash
     */
    public record Entry(MenuText label, MenuText line, String info) {}

    /**
     * What the list shows.
     *
     * @param title   the heading
     * @param empty   shown when there are no jobs
     * @param entries the jobs
     */
    public record Model(MenuText title, MenuText empty, List<Entry> entries) {
        /**
         * Keeps the entries unchangeable.
         */
        public Model {
            entries = List.copyOf(entries);
        }
    }
}
