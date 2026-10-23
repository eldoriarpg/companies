/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.data.wrapper.treasury.Treasury;
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
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickEvent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The treasury of the player's company: balance, payout share and cap, the latest log entries and the actions.
 * <p>
 * Every member may deposit and read the log; members with the treasury permission may also withdraw and set the share.
 * In chat the money actions suggest their command, since chat has no input; the dialog has an amount field and a share
 * slider from 0 to the cap.
 */
@SuppressWarnings("UnstableApiUsage")
public final class TreasuryMenu implements Menu<TreasuryMenu.Model> {
    static final String AMOUNT = "amount";
    static final String SHARE = "share";
    private static final int DIALOG_WIDTH = 400;

    private final MessageSender messages;
    private final Economy economy;
    private final Handlers handlers;

    /**
     * @param messages renders the dialog texts
     * @param economy  economy formatting the money
     * @param handlers what the buttons do
     */
    public TreasuryMenu(MessageSender messages, Economy economy, Handlers handlers) {
        this.messages = messages;
        this.economy = economy;
        this.handlers = handlers;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions actions) {
        var text = notice(model).append(title(model)).newLine().append(overview(model));
        text.newLine()
                .text(
                        "<act_deposit><add>[<i18n:company.bank.deposit>]</act_deposit>",
                        Values.click("act_deposit", ClickEvent.suggestCommand("/company bank deposit ")));
        if (model.manage()) {
            text.space()
                    .text(
                            "<act_withdraw><remove>[<i18n:words.withdraw>]</act_withdraw>",
                            Values.click("act_withdraw", ClickEvent.suggestCommand("/company bank withdraw ")));
            if (model.cap() > 0) {
                text.space()
                        .text(
                                "<act_share><modify>[<i18n:company.bank.setShare>]</act_share>",
                                Values.click("act_share", ClickEvent.suggestCommand("/company bank share ")));
            }
        }
        text.space()
                .text(
                        "<act_log><modify>[<i18n:company.bank.log.title>]</act_log>",
                        actions.chatAction("act_log", viewer -> handlers.log(viewer, 1)));
        return text.newLine().append(recent(model));
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions actions) {
        var components = new MenuComponents(messages, player);
        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.text(AMOUNT, components.locale("words.amount"))
                .initial("")
                .build());
        var shareSlider = model.manage() && model.cap() > 0;
        if (shareSlider) {
            inputs.add(DialogInput.numberRange(SHARE, components.locale("company.bank.share"), 0, model.cap())
                    .step(1f)
                    .initial((float) Math.min(model.treasury().payoutShare(), model.cap()))
                    .build());
        }
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(actions.button(
                components.locale("company.bank.deposit"),
                null,
                (clicker, response) -> handlers.deposit(clicker, text(response.getText(AMOUNT)))));
        if (model.manage()) {
            buttons.add(actions.button(
                    components.locale("words.withdraw"),
                    null,
                    (clicker, response) -> handlers.withdraw(clicker, text(response.getText(AMOUNT)))));
        }
        if (shareSlider) {
            buttons.add(actions.button(
                    components.locale("company.bank.setShare"),
                    null,
                    (clicker, response) -> handlers.share(clicker, percent(response.getFloat(SHARE)))));
        }
        buttons.add(actions.button(
                components.locale("company.bank.log.title"), null, (clicker, response) -> handlers.log(clicker, 1)));
        var body = notice(model).append(overview(model)).newLine().append(recent(model));
        var base = DialogBase.builder(components.of(title(model)))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(DialogBody.plainMessage(components.of(body), DIALOG_WIDTH)))
                .inputs(inputs)
                .build();
        var type = DialogType.multiAction(buttons).columns(2).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    /**
     * Balance, share and cap.
     *
     * @param model what the menu shows
     * @return the overview lines
     */
    MenuText overview(Model model) {
        var treasury = model.treasury();
        return MenuText.create()
                .text(
                        "<name><i18n:company.bank.balance>: <value><bank_balance>",
                        Values.money("bank_balance", economy, treasury.balance().doubleValue()))
                .newLine()
                .text(
                        "<name><i18n:company.bank.share>: <value><bank_share> % <inactive>(<i18n:company.bank.shareCap>"
                                + " <bank_cap> %)",
                        Values.number("bank_share", Math.min(treasury.payoutShare(), model.cap())),
                        Values.number("bank_cap", model.cap()));
    }

    private MenuText recent(Model model) {
        return MenuText.create()
                .text("<heading><i18n:company.bank.log.title>:")
                .newLine()
                .append(TreasuryTexts.entries("recent", model.recent(), model.names(), economy));
    }

    private static MenuText title(Model model) {
        return MenuText.create()
                .text("<heading><i18n:company.bank.title>", Values.text("company_name", model.companyName()));
    }

    private static MenuText notice(Model model) {
        var text = MenuText.create();
        model.notice().ifPresent(notice -> text.append(notice).newLine());
        return text;
    }

    private static String text(String value) {
        return Objects.requireNonNullElse(value, "");
    }

    private static int percent(Float value) {
        return value == null ? 0 : Math.round(value);
    }

    /**
     * What the menu shows.
     *
     * @param companyName name of the company
     * @param treasury    balance and share
     * @param cap         highest share the company may choose
     * @param manage      whether the viewer may withdraw and set the share
     * @param recent      latest log entries, newest first
     * @param names       names of the actors in the entries by id
     * @param notice      a line shown on top, such as the result of the last action
     */
    public record Model(
            String companyName,
            Treasury treasury,
            int cap,
            boolean manage,
            List<TreasuryLogEntry> recent,
            Map<UUID, String> names,
            Optional<MenuText> notice) {
        public Model {
            recent = List.copyOf(recent);
            names = Map.copyOf(names);
        }
    }

    /**
     * What the buttons of the menu do.
     */
    public interface Handlers {
        /**
         * @param player member paying in
         * @param amount entered amount
         */
        void deposit(Player player, String amount);

        /**
         * @param player member taking money out
         * @param amount entered amount
         */
        void withdraw(Player player, String amount);

        /**
         * @param player  member setting the share
         * @param percent chosen share
         */
        void share(Player player, int percent);

        /**
         * @param player viewing member
         * @param page   page of the log, from one
         */
        void log(Player player, int page);
    }
}
