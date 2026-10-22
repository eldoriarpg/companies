/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuService;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Page;
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
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The members of a company, one page at a time, and the details of one member.
 * <p>
 * Each member shows whether they are online and, to members of the company, their permissions. Members who may manage
 * permissions get a button that opens the permission editor. In chat the details are the hover text of a name, and
 * clicking the name shows them as a screen of their own; as a dialog every member is a button showing the details.
 */
public final class MemberMenu implements Menu<MemberMenu.Model> {
    /**
     * Members on one page.
     */
    static final int PAGE_SIZE = 10;

    private final MessageSender messages;
    private final MenuService menus;
    private final BiConsumer<Player, String> permissions;

    /**
     * @param messages    renders the hover and dialog texts
     * @param menus       shows other pages and the details
     * @param permissions opens the permission editor for a member, by name
     */
    public MemberMenu(MessageSender messages, MenuService menus, BiConsumer<Player, String> permissions) {
        this.messages = messages;
        this.menus = menus;
        this.permissions = permissions;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions actions) {
        var selected = model.member();
        if (selected.isPresent()) return chatDetails(model, selected.get(), actions);
        var texts = new SearchTexts(messages, player);
        var page = model.page();
        var text = title(model.view()).newLine();
        var members = page.items();
        for (var i = 0; i < members.size(); i++) {
            text.append(chatLine(model, i, members.get(i), texts, actions)).newLine();
        }
        return text.append(Paging.of(page).chat(actions, previous(model), next(model)));
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions actions) {
        var texts = new SearchTexts(messages, player);
        var selected = model.member();
        if (selected.isPresent()) return Optional.of(dialogDetails(model, selected.get(), texts, actions));
        var page = model.page();
        List<ActionButton> buttons = new ArrayList<>();
        for (var member : page.items()) {
            buttons.add(actions.button(
                    texts.of(MenuText.create().text("<value><member_name>", Values.text("member_name", member.name()))),
                    texts.of(details(model.view(), member)),
                    (clicker, response) -> menus.show(clicker, this, model.select(member.uuid()))));
        }
        var paging = Paging.of(page);
        paging.addButtons(buttons, actions, previous(model), next(model));
        var body = DialogBody.plainMessage(
                texts.of(MenuText.create().text("<heading><i18n:words.page> ").append(paging.label())));
        var base = DialogBase.builder(texts.of(title(model.view())))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(body))
                .build();
        return Optional.of(Dialog.create(factory ->
                factory.empty().base(base).type(DialogType.multiAction(buttons).build())));
    }

    private MenuText chatLine(Model model, int index, MemberEntry member, SearchTexts texts, MenuActions actions) {
        var detailsKey = Keys.of("member", index, "details");
        var nameKey = Keys.of("member", index, "name");
        var openKey = Keys.of("act_member", index);
        var text = MenuText.create()
                .text(
                        "<" + openKey + "><hover:show_text:'<" + detailsKey + ">'><value><" + nameKey + "></hover></"
                                + openKey + ">",
                        actions.chatAction(openKey, viewer -> menus.show(viewer, this, model.select(member.uuid()))),
                        Values.component(detailsKey, texts.of(details(model.view(), member))),
                        Values.text(nameKey, member.name()));
        if (editable(model.view(), member)) {
            var permissionKey = Keys.of("act_permissions", index);
            text.space().append(permissionButton(permissionKey, member, actions));
        }
        return text;
    }

    private MenuText chatDetails(Model model, MemberEntry member, MenuActions actions) {
        var text = MenuText.create()
                .text("<heading><member_name>", Values.text("member_name", member.name()))
                .newLine()
                .append(details(model.view(), member))
                .newLine();
        if (editable(model.view(), member)) {
            text.append(permissionButton("act_permissions", member, actions)).space();
        }
        return text.text(
                "<act_back><show>[<i18n:words.back>]</act_back>",
                actions.chatAction("act_back", viewer -> menus.show(viewer, this, model.deselect())));
    }

    private Dialog dialogDetails(Model model, MemberEntry member, SearchTexts texts, MenuActions actions) {
        List<ActionButton> buttons = new ArrayList<>();
        if (editable(model.view(), member)) {
            buttons.add(actions.button(
                    texts.locale("words.permissions"),
                    null,
                    (clicker, response) -> permissions.accept(clicker, member.name())));
        }
        buttons.add(actions.button(
                texts.locale("words.back"), null, (clicker, response) -> menus.show(clicker, this, model.deselect())));
        var title = MenuText.create().locale("company.member.details", Values.text("member_name", member.name()));
        var base = DialogBase.builder(texts.of(title))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(DialogBody.plainMessage(texts.of(details(model.view(), member)))))
                .build();
        return Dialog.create(factory ->
                factory.empty().base(base).type(DialogType.multiAction(buttons).build()));
    }

    private MenuText permissionButton(String key, MemberEntry member, MenuActions actions) {
        return MenuText.create()
                .text(
                        "<" + key + "><modify>[<i18n:words.permissions>]</" + key + ">",
                        actions.chatAction(key, viewer -> permissions.accept(viewer, member.name())));
    }

    /**
     * The online status of a member and, when the viewer may see them, the permissions.
     *
     * @param view   the member list
     * @param member the member
     * @return the details
     */
    static MenuText details(MemberView view, MemberEntry member) {
        var text = MenuText.create();
        member.lastSeen()
                .ifPresentOrElse(
                        lastSeen -> text.text(
                                "<neutral><i18n:words.lastSeen>: <last_seen>", Values.text("last_seen", lastSeen)),
                        () -> text.text("<active><i18n:words.online>"));
        if (view.showPermissions() && !member.permissions().isEmpty()) {
            var names = member.permissions().stream()
                    .map(permission -> permission.name().toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining(", "));
            text.newLine()
                    .text("<heading><i18n:words.permissions>:")
                    .newLine()
                    .text("<active><member_permissions>", Values.text("member_permissions", names));
        }
        return text;
    }

    private static boolean editable(MemberView view, MemberEntry member) {
        return view.managePermissions() && !member.owner();
    }

    private static MenuText title(MemberView view) {
        return MenuText.create()
                .text(
                        "<heading><i18n:company.member.members>: <value><company_name>",
                        Values.text("company_name", view.companyName()));
    }

    private Consumer<Player> previous(Model model) {
        return viewer -> menus.show(viewer, this, model.page(model.number() - 1));
    }

    private Consumer<Player> next(Model model) {
        return viewer -> menus.show(viewer, this, model.page(model.number() + 1));
    }

    /**
     * A page of the member list, or the details of one member.
     *
     * @param view     the member list
     * @param number   index of the shown page, from zero
     * @param selected the member whose details are shown, or empty for the list
     */
    public record Model(MemberView view, int number, Optional<UUID> selected) {
        /**
         * @param view the member list
         * @return the first page of the list
         */
        public static Model of(MemberView view) {
            return new Model(view, 0, Optional.empty());
        }

        /**
         * @return the shown page of the list
         */
        public Page<MemberEntry> page() {
            return Page.of(view.members(), number, PAGE_SIZE);
        }

        /**
         * @return the member whose details are shown
         */
        public Optional<MemberEntry> member() {
            return selected.flatMap(view::member);
        }

        Model page(int requested) {
            return new Model(view, Page.of(view.members(), requested, PAGE_SIZE).number(), Optional.empty());
        }

        Model select(UUID member) {
            return new Model(view, number, Optional.of(member));
        }

        Model deselect() {
            return new Model(view, number, Optional.empty());
        }
    }
}
