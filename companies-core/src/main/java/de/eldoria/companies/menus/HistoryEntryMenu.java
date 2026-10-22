/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One entry of the order history: times, lines with what was delivered, and the price and fee for owners or the
 * payouts with every member's contribution for companies, with a button back to the list.
 */
@SuppressWarnings("UnstableApiUsage")
public final class HistoryEntryMenu implements Menu<HistoryEntryMenu.Model> {
    private final MessageSender messages;
    private final Economy economy;
    private final HistoryNavigation navigation;

    /**
     * @param messages   renders the dialog texts
     * @param economy    economy formatting the money
     * @param navigation returns to the list
     */
    public HistoryEntryMenu(MessageSender messages, Economy economy, HistoryNavigation navigation) {
        this.messages = messages;
        this.economy = economy;
        this.navigation = navigation;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions actions) {
        return title(model.entry())
                .newLine()
                .append(HistoryTexts.details(model.entry(), model.names(), economy))
                .newLine()
                .text(
                        "<act_back><show>[<i18n:words.back>]</act_back>",
                        actions.chatAction("act_back", viewer -> navigation.page(viewer, model.returnPage())));
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions actions) {
        var components = new MenuComponents(messages, player);
        var back = actions.button(
                components.locale("words.back"),
                null,
                (clicker, response) -> navigation.page(clicker, model.returnPage()));
        var base = DialogBase.builder(components.of(title(model.entry())))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(DialogBody.plainMessage(
                        components.of(HistoryTexts.details(model.entry(), model.names(), economy)))))
                .build();
        var type = DialogType.multiAction(List.of(back)).columns(1).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    private static MenuText title(HistoryEntry entry) {
        return MenuText.create().text("<heading><order_name>", Values.text("order_name", entry.name()));
    }

    /**
     * An entry with the names of the members it lists.
     *
     * @param entry      the entry
     * @param names      names of the members of a company entry, by id
     * @param returnPage page of the list the back button returns to
     */
    public record Model(HistoryEntry entry, Map<UUID, String> names, int returnPage) {
        public Model {
            names = Map.copyOf(names);
        }
    }
}
