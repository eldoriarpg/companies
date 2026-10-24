/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.ranking.TimeRange;
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
import net.milkbowl.vault.economy.Economy;
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
 * Each member shows whether they are online and, to members of the company, their permissions and what they
 * contributed all time and in the last 30 days. Members of the company can sort the list by a contribution. Members who
 * may manage permissions get a button that opens the permission editor. In chat the details are the hover text of a name, and
 * clicking the name shows them as a screen of their own; as a dialog every member is a button showing the details.
 */
public final class MemberMenu implements Menu<MemberMenu.Model> {
    /**
     * Members on one page.
     */
    static final int PAGE_SIZE = 10;

    private final MessageSender messages;
    private final Economy economy;
    private final MenuService menus;
    private final BiConsumer<Player, String> permissions;

    /**
     * @param messages    renders the hover and dialog texts
     * @param economy     economy formatting the earned money
     * @param menus       shows other pages and the details
     * @param permissions opens the permission editor for a member, by name
     */
    public MemberMenu(
            MessageSender messages, Economy economy, MenuService menus, BiConsumer<Player, String> permissions) {
        this.messages = messages;
        this.economy = economy;
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
        if (model.view().contributions().isPresent()) {
            text.append(sortControls(model, actions)).newLine();
        }
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
                    texts.of(details(model.view(), member, economy)),
                    (clicker, response) -> menus.show(clicker, this, model.select(member.uuid()))));
        }
        if (model.view().contributions().isPresent()) {
            buttons.add(actions.button(
                    texts.of(sortLabel(model.sorting())),
                    null,
                    (clicker, response) ->
                            menus.show(clicker, this, model.sort(model.sorting().next()))));
            buttons.add(actions.button(
                    texts.of(MenuText.create().text("<i18n:" + model.range().translationKey() + ">")),
                    null,
                    (clicker, response) -> menus.show(clicker, this, model.range(otherRange(model.range())))));
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
                        Values.component(detailsKey, texts.of(details(model.view(), member, economy))),
                        Values.text(nameKey, member.name()));
        sortValue(model, index, member).ifPresent(value -> text.space().append(value));
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
                .append(details(model.view(), member, economy))
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
                .body(List.of(DialogBody.plainMessage(texts.of(details(model.view(), member, economy)))))
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
     * The buttons choosing what the list is sorted by and which range of contributions it shows. The current choices
     * are highlighted and not clickable.
     *
     * @param model   the shown list
     * @param actions binds the buttons
     * @return the line
     */
    private MenuText sortControls(Model model, MenuActions actions) {
        var text = MenuText.create().text("<name><i18n:company.member.stats.sort>:");
        for (var sorting : MemberSorting.values()) {
            var label = "<i18n:" + sorting.translationKey() + ">";
            if (sorting == model.sorting()) {
                text.text(" <active>[" + label + "]");
                continue;
            }
            var key = Keys.of("act_sort", sorting.id());
            text.text(
                    " <" + key + "><show>[" + label + "]</" + key + ">",
                    actions.chatAction(key, viewer -> menus.show(viewer, this, model.sort(sorting))));
        }
        text.text(" <inactive>|");
        for (var index = 0; index < MemberContributions.RANGES.size(); index++) {
            var range = MemberContributions.RANGES.get(index);
            var label = "<i18n:" + range.translationKey() + ">";
            if (range == model.range()) {
                text.text(" <active>[" + label + "]");
                continue;
            }
            var key = Keys.of("act_range", index);
            text.text(
                    " <" + key + "><show>[" + label + "]</" + key + ">",
                    actions.chatAction(key, viewer -> menus.show(viewer, this, model.range(range))));
        }
        return text;
    }

    private static MenuText sortLabel(MemberSorting sorting) {
        return MenuText.create().text("<i18n:company.member.stats.sort>: <i18n:" + sorting.translationKey() + ">");
    }

    private static TimeRange otherRange(TimeRange range) {
        return range == TimeRange.ALL ? TimeRange.LAST_30_DAYS : TimeRange.ALL;
    }

    /**
     * The value the list is sorted by, shown next to the name.
     *
     * @param model  the shown list
     * @param index  position of the member on the page
     * @param member the member
     * @return the value, or empty when the list is sorted by name or shows no contributions
     */
    private Optional<MenuText> sortValue(Model model, int index, MemberEntry member) {
        var contributions = model.view().contributions();
        if (model.sorting() == MemberSorting.NAME || contributions.isEmpty()) return Optional.empty();
        var stats = contributions.get().of(member.uuid(), model.range());
        var key = Keys.of("member", index, "contribution");
        var value = model.sorting() == MemberSorting.EARNED
                ? Values.money(key, economy, stats.earned())
                : Values.number(key, (int) model.sorting().value(stats));
        return Optional.of(MenuText.create().text("<gold><" + key + ">", value));
    }

    /**
     * The online status of a member and, when the viewer may see them, the permissions and the contributions.
     *
     * @param view    the member list
     * @param member  the member
     * @param economy economy formatting the earned money
     * @return the details
     */
    static MenuText details(MemberView view, MemberEntry member, Economy economy) {
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
        view.contributions()
                .ifPresent(contributions -> text.newLine().append(contribution(contributions, member, economy)));
        return text;
    }

    /**
     * What a member contributed, all time and in the last 30 days.
     *
     * @param contributions contributions of the members
     * @param member        the member
     * @param economy       economy formatting the earned money
     * @return the lines
     */
    static MenuText contribution(MemberContributions contributions, MemberEntry member, Economy economy) {
        var all = contributions.of(member.uuid(), TimeRange.ALL);
        var recent = contributions.of(member.uuid(), TimeRange.LAST_30_DAYS);
        return MenuText.create()
                .text("<heading><i18n:company.member.stats.allTime> <inactive>/ <heading>")
                .locale("company.member.stats.last30Days")
                .newLine()
                .text(
                        "<name><i18n:company.member.stats.items>: <value><items_all> <inactive>/ <value><items_recent>",
                        Values.number("items_all", all.items()),
                        Values.number("items_recent", recent.items()))
                .newLine()
                .text(
                        "<name><i18n:company.member.stats.earned>: <value><earned_all> <inactive>/ <value><earned_recent>",
                        Values.money("earned_all", economy, all.earned()),
                        Values.money("earned_recent", economy, recent.earned()))
                .newLine()
                .text(
                        "<name><i18n:company.member.stats.orders>: <value><orders_all> <inactive>/ <value><orders_recent>",
                        Values.number("orders_all", all.orders()),
                        Values.number("orders_recent", recent.orders()));
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
     * @param sorting  what the list is sorted by
     * @param range    range of the contributions the list is sorted by
     */
    public record Model(MemberView view, int number, Optional<UUID> selected, MemberSorting sorting, TimeRange range) {
        /**
         * @param view the member list
         * @return the first page of the list, sorted by name
         */
        public static Model of(MemberView view) {
            return of(view, MemberSorting.NAME, TimeRange.ALL);
        }

        /**
         * @param view    the member list
         * @param sorting what the list is sorted by
         * @param range   range of the contributions the list is sorted by
         * @return the first page of the sorted list
         */
        public static Model of(MemberView view, MemberSorting sorting, TimeRange range) {
            return new Model(view, 0, Optional.empty(), sorting, range);
        }

        /**
         * @return the shown page of the list
         */
        public Page<MemberEntry> page() {
            return Page.of(view.sorted(sorting, range), number, PAGE_SIZE);
        }

        /**
         * @return the member whose details are shown
         */
        public Optional<MemberEntry> member() {
            return selected.flatMap(view::member);
        }

        Model page(int requested) {
            return new Model(
                    view, Page.of(view.members(), requested, PAGE_SIZE).number(), Optional.empty(), sorting, range);
        }

        Model select(UUID member) {
            return new Model(view, number, Optional.of(member), sorting, range);
        }

        Model deselect() {
            return new Model(view, number, Optional.empty(), sorting, range);
        }

        Model sort(MemberSorting sorting) {
            return new Model(view, 0, Optional.empty(), sorting, range);
        }

        Model range(TimeRange range) {
            return new Model(view, 0, Optional.empty(), sorting, range);
        }
    }
}
