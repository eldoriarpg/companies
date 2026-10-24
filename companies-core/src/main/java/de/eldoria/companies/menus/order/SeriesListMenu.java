/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import de.eldoria.companies.data.wrapper.series.OrderSeries;
import de.eldoria.companies.data.wrapper.series.SeriesState;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The repeating orders of a player: the active ones and the most recently ended ones. Every series shows how it
 * repeats, its copies, its state and what comes next when hovered, and has an info button.
 */
@SuppressWarnings("UnstableApiUsage")
public final class SeriesListMenu implements Menu<SeriesListMenu.Model> {
    /**
     * Most ended series shown.
     */
    static final int ENDED_SHOWN = 10;

    private final SeriesActions actions;
    private final MessageSender messages;

    /**
     * @param actions  what the buttons do
     * @param messages renders the hover texts and the dialog texts
     */
    public SeriesListMenu(SeriesActions actions, MessageSender messages) {
        this.actions = actions;
        this.messages = messages;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions buttons) {
        var components = new MenuComponents(messages, player);
        var text = MenuText.create().text("<name><i18n:order.series.title>:");
        if (model.series().isEmpty()) return text.newLine().text("<inactive><i18n:order.series.none>");
        var series = model.series();
        for (var i = 0; i < series.size(); i++) {
            var entry = series.get(i);
            var prefix = Keys.of("series", i);
            var details = Keys.of(prefix, "details");
            var info = Keys.of("act_info", i);
            var id = entry.id();
            text.newLine()
                    .text(
                            "<hover:show_text:'<" + details + ">'>",
                            Values.component(details, components.of(SeriesTexts.details(entry, model.now()))))
                    .append(SeriesTexts.title(prefix, entry))
                    .text(" <inactive>(<i18n:" + entry.state().translationKey() + ">)</hover> ")
                    .text(
                            "<" + info + "><add>[<i18n:words.info>]</" + info + ">",
                            buttons.chatAction(info, p -> actions.info(p, id)));
        }
        return text;
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions buttons) {
        var components = new MenuComponents(messages, player);
        List<DialogBody> body = new ArrayList<>();
        if (model.series().isEmpty()) body.add(DialogBody.plainMessage(components.locale("order.series.none")));
        List<ActionButton> list = new ArrayList<>();
        for (var entry : model.series()) {
            var id = entry.id();
            var label = SeriesTexts.title("", entry)
                    .text(" <inactive>(<i18n:" + entry.state().translationKey() + ">)");
            list.add(buttons.button(
                    components.of(label),
                    components.of(SeriesTexts.details(entry, model.now())),
                    (p, response) -> actions.info(p, id)));
        }
        var base = DialogBase.builder(components.locale("order.series.title"))
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .build();
        var type = DialogType.multiAction(list).columns(1).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    /**
     * The repeating orders as the list shows them.
     *
     * @param series active series first, then the most recently ended ones
     * @param now    current time
     */
    public record Model(List<OrderSeries> series, LocalDateTime now) {
        /**
         * @param series series
         * @param now    current time
         */
        public Model {
            series = List.copyOf(series);
        }

        /**
         * Keeps every active series and the {@link #ENDED_SHOWN} ended ones listed first.
         *
         * @param series series of the player, newest first
         * @param now    current time
         * @return the model
         */
        public static Model of(List<OrderSeries> series, LocalDateTime now) {
            var active = series.stream()
                    .filter(entry -> entry.state() == SeriesState.ACTIVE)
                    .toList();
            var ended = series.stream()
                    .filter(entry -> entry.state() != SeriesState.ACTIVE)
                    .limit(ENDED_SHOWN)
                    .toList();
            var shown = new ArrayList<>(active);
            shown.addAll(ended);
            return new Model(shown, now);
        }
    }
}
