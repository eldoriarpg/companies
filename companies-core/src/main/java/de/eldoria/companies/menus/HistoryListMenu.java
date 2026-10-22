/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.companies.menus.company.Paging;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * One page of the order history of an owner or of a company, the latest ended order first.
 * <p>
 * Every entry shows its name, outcome, end time and money, with its details when hovered, and opens the entry when
 * clicked. As a dialog every entry is a button.
 */
@SuppressWarnings("UnstableApiUsage")
public final class HistoryListMenu implements Menu<HistoryListMenu.Model> {
    private final MessageSender messages;
    private final Economy economy;
    private final HistoryNavigation navigation;
    private final MenuText title;

    /**
     * @param messages   renders the hover and dialog texts
     * @param economy    economy formatting the money
     * @param navigation opens other pages and entries
     * @param title      title of the list
     */
    public HistoryListMenu(MessageSender messages, Economy economy, HistoryNavigation navigation, MenuText title) {
        this.messages = messages;
        this.economy = economy;
        this.navigation = navigation;
        this.title = title;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions actions) {
        var components = new MenuComponents(messages, player);
        var text = MenuText.create().text("<heading>").append(title);
        if (model.entries().isEmpty()) {
            text.newLine().text("<inactive><i18n:history.empty>");
        }
        var entries = model.entries();
        for (var index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            var details = Keys.of("entry", index, "details");
            var open = Keys.of("act_entry", index);
            text.newLine()
                    .text(
                            "<" + open + "><hover:show_text:'<" + details + ">'>",
                            actions.chatAction(open, viewer -> navigation.entry(viewer, entry.id(), model.page())),
                            Values.component(details, components.of(details(entry))))
                    .append(HistoryTexts.summary(Keys.of("entry", index), entry, economy))
                    .text("</hover></" + open + ">");
        }
        return text.newLine().append(model.paging().chat(actions, previous(model), next(model)));
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions actions) {
        var components = new MenuComponents(messages, player);
        List<ActionButton> buttons = new ArrayList<>();
        for (var entry : model.entries()) {
            buttons.add(actions.button(
                    components.of(HistoryTexts.summary("entry", entry, economy)),
                    components.of(details(entry)),
                    (clicker, response) -> navigation.entry(clicker, entry.id(), model.page())));
        }
        var paging = model.paging();
        paging.addButtons(buttons, actions, previous(model), next(model));
        var body = MenuText.create();
        if (model.entries().isEmpty())
            body.text("<inactive><i18n:history.empty>").newLine();
        body.text("<heading><i18n:words.page> ").append(paging.label());
        var base = DialogBase.builder(components.of(MenuText.create().append(title)))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(DialogBody.plainMessage(components.of(body))))
                .build();
        var type = DialogType.multiAction(buttons).columns(1).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    private MenuText details(HistoryEntry entry) {
        return HistoryTexts.overview(entry, economy);
    }

    private Consumer<Player> previous(Model model) {
        return viewer -> navigation.page(viewer, model.page() - 1);
    }

    private Consumer<Player> next(Model model) {
        return viewer -> navigation.page(viewer, model.page() + 1);
    }

    /**
     * One page of a history as the repository returned it.
     *
     * @param page    number of the page, from one
     * @param entries the entries of the page, the latest first
     * @param hasNext whether another page may follow
     */
    public record Model(int page, List<HistoryEntry> entries, boolean hasNext) {
        public Model {
            entries = List.copyOf(entries);
        }

        /**
         * A page as the repository returned it. A full page may be followed by another one.
         *
         * @param page     number of the page, from one
         * @param entries  entries of the page
         * @param pageSize entries on a full page
         * @return the model
         */
        public static Model of(int page, List<HistoryEntry> entries, int pageSize) {
            return new Model(page, entries, entries.size() >= pageSize);
        }

        Paging paging() {
            return Paging.open(page, hasNext);
        }
    }
}
