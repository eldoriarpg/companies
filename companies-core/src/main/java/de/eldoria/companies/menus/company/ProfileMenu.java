/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The profile of a company: level, founding date, leader and members, and on the own profile the progress towards the
 * next level and the orders the company works on.
 * <p>
 * The tag and the name are shown in the company colour, followed by the description and the join mode. Members who may
 * edit the profile get an {@code [Edit]} button on their own profile.
 * <p>
 * The level and the next level show their requirements and limits when hovered. In chat the member and order counts
 * have {@code [List]} buttons; as a dialog they are buttons below the profile.
 */
public final class ProfileMenu implements Menu<ProfileView> {
    private final MessageSender messages;
    private final Economy economy;
    private final Navigation navigation;

    /**
     * @param messages   renders the hover and dialog texts
     * @param economy    economy formatting the earned money
     * @param navigation opens the member and order lists
     */
    public ProfileMenu(MessageSender messages, Economy economy, Navigation navigation) {
        this.messages = messages;
        this.economy = economy;
        this.navigation = navigation;
    }

    @Override
    public MenuText chat(Player player, ProfileView model, MenuActions actions) {
        var texts = new SearchTexts(messages, player);
        var text = title(model).newLine().append(details(model, texts));
        text.append(memberLine(model))
                .text(
                        " <act_members><show>[<i18n:words.list>]</act_members>",
                        actions.chatAction("act_members", viewer -> navigation.members(viewer, model)));
        if (model.own()) {
            text.newLine()
                    .append(orderLine(model))
                    .text(
                            " <act_orders><show>[<i18n:words.list>]</act_orders>",
                            actions.chatAction("act_orders", navigation::orders));
        }
        if (model.editable()) {
            text.newLine()
                    .text(
                            "<act_edit><modify>[<i18n:company.profile.edit.button>]</act_edit>",
                            actions.chatAction("act_edit", navigation::edit));
        }
        return text;
    }

    @Override
    public Optional<Dialog> dialog(Player player, ProfileView model, MenuActions actions) {
        var texts = new SearchTexts(messages, player);
        var body = details(model, texts).append(memberLine(model));
        if (model.own()) body.newLine().append(orderLine(model));
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(actions.button(
                texts.locale("words.member"), null, (clicker, response) -> navigation.members(clicker, model)));
        if (model.own()) {
            buttons.add(actions.button(texts.locale("words.orders"), null, (clicker, response) -> {
                clicker.closeDialog();
                navigation.orders(clicker);
            }));
        }
        if (model.editable()) {
            buttons.add(actions.button(
                    texts.locale("company.profile.edit.button"),
                    null,
                    (clicker, response) -> navigation.edit(clicker)));
        }
        var base = DialogBase.builder(texts.of(title(model)))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(DialogBody.plainMessage(texts.of(body))))
                .build();
        return Optional.of(Dialog.create(factory -> factory.empty()
                .base(base)
                .type(DialogType.multiAction(buttons).columns(2).build())));
    }

    private MenuText details(ProfileView model, SearchTexts texts) {
        var text = MenuText.create();
        model.description().ifPresent(description -> text.text(
                        "<name><i18n:company.profile.description>: <value><description>",
                        Values.text("description", description))
                .newLine());
        text.text(
                "<name><i18n:words.level>: <hover:show_text:'<level_details>'><value><level_number> -"
                        + " <level_name></hover>",
                levelDetails("level_details", model.level(), texts),
                Values.number("level_number", model.level().level()),
                Values.text("level_name", model.level().levelName()));
        model.next().ifPresent(next -> text.append(nextLevel(next, texts)));
        return text.newLine()
                .text("<name><i18n:words.founded>: <value><founded>", Values.text("founded", model.founded()))
                .newLine()
                .text("<name><i18n:words.leader>: <value><leader_name>", Values.text("leader_name", model.leader()))
                .newLine()
                .text("<name><i18n:company.profile.joinMode>: <value><i18n:"
                        + model.joinMode().translationKey() + ">")
                .newLine();
    }

    private MenuText nextLevel(ProfileView.NextLevel next, SearchTexts texts) {
        var text = MenuText.create()
                .text(
                        " <hover:show_text:'<next_level_details>'><u><show><i18n:company.level.nextLevel></show></u>"
                                + "</hover>",
                        levelDetails("next_level_details", next.level(), texts));
        var progress = next.progress();
        for (var i = 0; i < progress.size(); i++) {
            text.newLine().append(progressLine(i, progress.get(i)));
        }
        return text;
    }

    /**
     * One requirement of the next level: the company's value, the required value and a bar.
     *
     * @param index    index of the requirement
     * @param progress the company's progress towards the requirement
     * @return the line
     */
    MenuText progressLine(int index, ProfileView.Progress progress) {
        var valueKey = Keys.of("progress", index, "value");
        var requiredKey = Keys.of("progress", index, "required");
        return MenuText.create()
                .text(
                        "  <name><i18n:" + progress.label() + ">: <value><" + valueKey + ">/<" + requiredKey + "> ",
                        amount(valueKey, progress.value(), progress.money()),
                        amount(requiredKey, progress.required(), progress.money()))
                .text(Texts.progressBar(progress.share()));
    }

    private TagResolver.Single amount(String key, double value, boolean money) {
        if (money) return Values.money(key, economy, value);
        return Values.number(key, (long) value);
    }

    private TagResolver.Single levelDetails(String key, CompanyLevel level, SearchTexts texts) {
        return Values.component(key, texts.of(MenuText.create().text(level.asComponent())));
    }

    private static MenuText title(ProfileView model) {
        var text = MenuText.create();
        model.tag().ifPresent(tag -> text.text("<name><company_tag> ", colored("company_tag", "[" + tag + "]", model)));
        return text.text("<heading><company_name>", colored("company_name", model.name(), model));
    }

    /**
     * A value in the company colour, inserted unparsed. Without a colour it takes the style around it.
     *
     * @param key   placeholder key
     * @param value text to insert
     * @param model the profile with the colour
     * @return placeholder
     */
    private static TagResolver.Single colored(String key, String value, ProfileView model) {
        return Values.component(key, Component.text(value, model.color().orElse(null)));
    }

    private static MenuText memberLine(ProfileView model) {
        return MenuText.create()
                .text(
                        "<name><i18n:words.member>: <value><member_count>",
                        Values.number("member_count", model.memberCount()));
    }

    private static MenuText orderLine(ProfileView model) {
        return MenuText.create()
                .text(
                        "<name><i18n:words.orders>: <value><order_count>",
                        Values.number("order_count", model.orderCount().orElse(0)));
    }

    /**
     * Opens the lists the profile links to.
     */
    public interface Navigation {
        /**
         * Shows the members of the company.
         *
         * @param viewer  player who clicked
         * @param profile the shown profile
         */
        void members(Player viewer, ProfileView profile);

        /**
         * Shows the orders the viewer's company works on.
         *
         * @param viewer player who clicked
         */
        void orders(Player viewer);

        /**
         * Opens the profile editor.
         *
         * @param viewer player who clicked
         */
        void edit(Player viewer);
    }
}
