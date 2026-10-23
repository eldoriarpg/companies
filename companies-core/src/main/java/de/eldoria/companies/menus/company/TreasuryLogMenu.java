/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.data.wrapper.treasury.TreasuryLogEntry;
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
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * One page of a company's treasury log, newest first, readable by every member.
 * <p>
 * Payout shares name the company's own order only, never the worker or the order owner.
 */
@SuppressWarnings("UnstableApiUsage")
public final class TreasuryLogMenu implements Menu<TreasuryLogMenu.Model> {
    private static final int DIALOG_WIDTH = 400;

    private final MessageSender messages;
    private final Economy economy;
    private final Handlers handlers;

    /**
     * @param messages renders the dialog texts
     * @param economy  economy formatting the money
     * @param handlers what the buttons do
     */
    public TreasuryLogMenu(MessageSender messages, Economy economy, Handlers handlers) {
        this.messages = messages;
        this.economy = economy;
        this.handlers = handlers;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions actions) {
        var text = title(model).newLine().append(entries(model));
        text.newLine().append(paging(model).chat(actions, previous(model), next(model)));
        return text.space()
                .text(
                        "<act_back><modify>[<i18n:words.back>]</act_back>",
                        actions.chatAction("act_back", handlers::back));
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions actions) {
        var components = new MenuComponents(messages, player);
        List<ActionButton> buttons = new ArrayList<>();
        var paging = paging(model);
        paging.addButtons(buttons, actions, previous(model), next(model));
        buttons.add(
                actions.button(components.locale("words.back"), null, (clicker, response) -> handlers.back(clicker)));
        var body = entries(model).newLine().text("<heading><i18n:words.page> ").append(paging.label());
        var base = DialogBase.builder(components.of(title(model)))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(DialogBody.plainMessage(components.of(body), DIALOG_WIDTH)))
                .build();
        var type = DialogType.multiAction(buttons).columns(2).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    private MenuText entries(Model model) {
        return TreasuryTexts.entries("entry", model.entries(), model.names(), economy);
    }

    private static MenuText title(Model model) {
        return MenuText.create()
                .text("<heading><i18n:company.bank.log.heading>", Values.text("company_name", model.companyName()));
    }

    private static Paging paging(Model model) {
        return Paging.open(model.page(), model.hasNext());
    }

    private Consumer<Player> previous(Model model) {
        return viewer -> handlers.page(viewer, model.page() - 1);
    }

    private Consumer<Player> next(Model model) {
        return viewer -> handlers.page(viewer, model.page() + 1);
    }

    /**
     * One page of the log.
     *
     * @param companyName name of the company
     * @param page        number of the page, from one
     * @param entries     entries of the page, newest first
     * @param names       names of the actors in the entries by id
     * @param hasNext     whether another page may follow
     */
    public record Model(
            String companyName, int page, List<TreasuryLogEntry> entries, Map<UUID, String> names, boolean hasNext) {
        public Model {
            entries = List.copyOf(entries);
            names = Map.copyOf(names);
        }

        /**
         * A page as the repository returned it. A full page may be followed by another one.
         *
         * @param companyName name of the company
         * @param page        number of the page, from one
         * @param entries     entries of the page
         * @param names       names of the actors by id
         * @param pageSize    entries on a full page
         * @return the model
         */
        public static Model of(
                String companyName, int page, List<TreasuryLogEntry> entries, Map<UUID, String> names, int pageSize) {
            return new Model(companyName, page, entries, names, entries.size() >= pageSize);
        }
    }

    /**
     * What the buttons of the menu do.
     */
    public interface Handlers {
        /**
         * @param player viewing member
         * @param page   page to show, from one
         */
        void page(Player player, int page);

        /**
         * @param player viewing member, back to the treasury
         */
        void back(Player player);
    }
}
