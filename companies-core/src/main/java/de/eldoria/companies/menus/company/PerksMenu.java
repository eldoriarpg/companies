/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.perks.PerkService;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * The perks of the server as a company sees them: each with its name, what it gives, its price, its minimum level and
 * whether the company owns it, has it paused, may buy it or needs a higher level. A member who may buy perks gets a buy
 * button on every perk the company may buy: on its line in chat, as a button of the dialog.
 */
public final class PerksMenu implements Menu<PerksMenu.Model> {
    private static final String PREFIX = "company.perks.";

    private final MessageSender messages;
    private final Function<BigDecimal, String> money;
    private final Handlers handlers;

    /**
     * @param messages renders the rows and the dialog texts
     * @param money    formats a price
     * @param handlers what the buttons do
     */
    public PerksMenu(MessageSender messages, Function<BigDecimal, String> money, Handlers handlers) {
        this.messages = messages;
        this.money = money;
        this.handlers = handlers;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions actions) {
        var components = new MenuComponents(messages, player);
        var text = MenuText.create().text("<heading>").locale(PREFIX + "title");
        var row = 0;
        for (var offer : model.offers()) {
            var key = Keys.of("row", row);
            var line = line(offer);
            if (buyable(model, offer)) {
                var buy = Keys.of("act_buy", row);
                line = MenuText.create()
                        .append(line)
                        .text(
                                " <" + buy + "><show>[<i18n:" + PREFIX + "buy>]</" + buy + ">",
                                actions.chatAction(buy, clicker -> handlers.buy(clicker, offer)));
            }
            text.newLine().text("<" + key + ">", Values.component(key, components.of(line)));
            row++;
        }
        return text;
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions actions) {
        var components = new MenuComponents(messages, player);
        List<DialogBody> body = new ArrayList<>();
        List<ActionButton> buttons = new ArrayList<>();
        for (var offer : model.offers()) {
            body.add(DialogBody.plainMessage(components.of(line(offer))));
            if (!buyable(model, offer)) continue;
            buttons.add(actions.button(
                    components.of(MenuText.create()
                            .text(
                                    "<show><i18n:" + PREFIX + "buy> <value><perk_name>",
                                    Values.text("perk_name", offer.perk().name()))),
                    null,
                    (clicker, response) -> handlers.buy(clicker, offer)));
        }
        var base = DialogBase.builder(components.locale(PREFIX + "title"))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(body)
                .build();
        var type = buttons.isEmpty()
                ? DialogType.notice()
                : DialogType.multiAction(buttons).columns(2).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    @Override
    public boolean holdsChat() {
        return false;
    }

    private static boolean buyable(Model model, PerkService.Offer offer) {
        return model.mayBuy() && offer.state() == PerkService.State.AVAILABLE;
    }

    private MenuText line(PerkService.Offer offer) {
        return MenuText.create()
                .text("<neutral>[<i18n:" + PREFIX + "state."
                        + offer.state().name().toLowerCase(Locale.ROOT) + ">]</neutral> ")
                .locale(
                        PREFIX + "entry",
                        Values.text("perk_name", offer.perk().name()),
                        Values.text("description", offer.perk().description()),
                        Values.text("price", money.apply(offer.price())),
                        Values.number("level", offer.minLevel()));
    }

    /**
     * What the list shows.
     *
     * @param offers the perks, in the order they were registered
     * @param mayBuy whether the viewer may buy perks for the company
     */
    public record Model(List<PerkService.Offer> offers, boolean mayBuy) {}

    /**
     * What the buttons of the list do.
     */
    public interface Handlers {
        /**
         * @param player player who clicked
         * @param offer  the perk to buy
         */
        void buy(Player player, PerkService.Offer offer);
    }
}
