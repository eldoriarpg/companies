/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.commands.company.order.search.OrderSearch;
import de.eldoria.companies.commands.company.order.search.SearchForm;
import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.commands.company.order.search.SortingType;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Colors;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The search form of the company order search.
 * <p>
 * As a dialog it is one form whose {@code [Search]} button runs the search. In chat every choice is a button that
 * changes the query at once; names, materials, prices and sizes are typed as {@code /company order search query …}
 * commands, which the buttons suggest.
 * <p>
 * {@code [Save as alert]} saves the search as an order alert of the company: in chat it suggests
 * {@code /company alerts add}, as a dialog it keeps the filled in form as the query and asks for the alert's name.
 * The search of orders to work on alone has no alerts, and its typed commands are {@code /order work search query …}.
 */
public final class SearchMenu implements Menu<SearchMenu.Model> {
    private static final String SETTING = "company.order.search.query.searchQuery.";
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_MATERIALS_LENGTH = 256;
    private static final int MAX_NUMBER_LENGTH = 16;
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry(SearchForm.NAME, "words.name"),
            Map.entry(SearchForm.MATERIALS, "company.order.search.dialog.materials"),
            Map.entry(SearchForm.MATERIAL_SEARCH, SETTING + "materialSearch"),
            Map.entry(SearchForm.MATERIAL_MATCH, SETTING + "materialMatch"),
            Map.entry(SearchForm.MIN_PRICE, SETTING + "minPrice"),
            Map.entry(SearchForm.MAX_PRICE, SETTING + "maxPrice"),
            Map.entry(SearchForm.MIN_SIZE, SETTING + "minSize"),
            Map.entry(SearchForm.MAX_SIZE, SETTING + "maxSize"),
            Map.entry(SearchForm.SORTING, "words.sorting"),
            Map.entry(SearchForm.DIRECTION, "words.ordering"),
            Map.entry(SearchForm.FILLABLE, SETTING + "fillable"),
            Map.entry(SearchForm.SERVER_ORDERS, SETTING + "serverOrders"));

    private final OrderSearch search;
    private final MessageSender messages;

    /**
     * @param search   the order search the buttons act on
     * @param messages renders the dialog texts
     */
    public SearchMenu(OrderSearch search, MessageSender messages) {
        this.search = search;
        this.messages = messages;
    }

    /**
     * Reads a submitted search dialog.
     *
     * @param response values of the dialog inputs
     * @return the form as filled in
     */
    public static SearchForm read(DialogResponseView response) {
        return new SearchForm(
                text(response, SearchForm.NAME),
                text(response, SearchForm.MATERIALS),
                text(response, SearchForm.MATERIAL_SEARCH),
                text(response, SearchForm.MATERIAL_MATCH),
                text(response, SearchForm.MIN_PRICE),
                text(response, SearchForm.MAX_PRICE),
                text(response, SearchForm.MIN_SIZE),
                text(response, SearchForm.MAX_SIZE),
                text(response, SearchForm.SORTING),
                text(response, SearchForm.DIRECTION),
                Boolean.TRUE.equals(response.getBoolean(SearchForm.FILLABLE)),
                text(response, SearchForm.SERVER_ORDERS));
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions actions) {
        var query = model.query();
        var text = MenuText.create()
                .text("<heading>")
                .locale(SETTING + "searchSetting")
                .newLine()
                .append(nameLine(query, actions))
                .append(materialLines(query, actions))
                .append(rangeLines(query))
                .append(sortingLines(query, actions))
                .append(fillableLine(query, actions))
                .append(search.marksServerOrders() ? serverLine(query, actions) : MenuText.create())
                .text(
                        "<act_search><show>[<i18n:words.search>]</act_search>",
                        actions.chatAction("act_search", p -> search.showPage(p, 0)))
                .space()
                .text(
                        "<act_reset><remove>[<i18n:words.clear>]</act_reset>",
                        actions.chatAction("act_reset", search::reset));
        if (search.mode().alerts()) text.space().append(AlertNameMenu.saveButton());
        return text;
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions actions) {
        var texts = new SearchTexts(messages, player);
        var form = model.form();
        List<DialogBody> body = new ArrayList<>();
        model.invalidInput()
                .map(input -> MenuText.create()
                        .locale(
                                "company.order.search.error.invalid",
                                Values.component("field", texts.locale(Objects.requireNonNull(LABELS.get(input))))))
                .ifPresent(error -> body.add(DialogBody.plainMessage(texts.error(error))));
        body.add(DialogBody.plainMessage(texts.locale("company.order.search.dialog.limits")));
        List<DialogInput> inputs = new ArrayList<>(List.of(
                textInput(texts, SearchForm.NAME, form.name(), MAX_NAME_LENGTH),
                textInput(texts, SearchForm.MATERIALS, form.materials(), MAX_MATERIALS_LENGTH),
                options(
                        texts,
                        SearchForm.MATERIAL_SEARCH,
                        form.materialSearch(),
                        Map.of(SearchForm.ANY, "words.any", SearchForm.ALL, "words.all")),
                options(
                        texts,
                        SearchForm.MATERIAL_MATCH,
                        form.materialMatch(),
                        Map.of(SearchForm.PART, "words.part", SearchForm.EXACT, "words.exact")),
                textInput(texts, SearchForm.MIN_PRICE, form.minPrice(), MAX_NUMBER_LENGTH),
                textInput(texts, SearchForm.MAX_PRICE, form.maxPrice(), MAX_NUMBER_LENGTH),
                textInput(texts, SearchForm.MIN_SIZE, form.minSize(), MAX_NUMBER_LENGTH),
                textInput(texts, SearchForm.MAX_SIZE, form.maxSize(), MAX_NUMBER_LENGTH),
                sortingInput(texts, form.sorting()),
                options(
                        texts,
                        SearchForm.DIRECTION,
                        form.direction(),
                        Map.of(SearchForm.ASC, "words.ascending", SearchForm.DESC, "words.descending")),
                DialogInput.bool(SearchForm.FILLABLE, label(texts, SearchForm.FILLABLE))
                        .initial(form.fillable())
                        .build()));
        if (search.marksServerOrders()) {
            inputs.add(options(
                    texts,
                    SearchForm.SERVER_ORDERS,
                    form.serverOrders().isBlank() ? SearchForm.ALL : form.serverOrders(),
                    Map.of(
                            SearchForm.ALL, SETTING + "serverOrders.all",
                            SearchForm.ONLY, SETTING + "serverOrders.only",
                            SearchForm.HIDE, SETTING + "serverOrders.hide")));
        }
        List<ActionButton> buttons = new ArrayList<>(List.of(
                actions.button(
                        texts.locale("words.search"),
                        null,
                        (clicker, response) -> search.submit(clicker, read(response))),
                actions.button(texts.locale("words.clear"), null, (clicker, response) -> search.reset(clicker))));
        if (search.mode().alerts()) {
            buttons.add(actions.button(
                    texts.locale("company.order.search.saveAlert"),
                    null,
                    (clicker, response) -> search.saveAlert(clicker, read(response))));
        }
        var base = DialogBase.builder(texts.locale("company.order.search.dialog.title"))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(body)
                .inputs(inputs)
                .build();
        var type = DialogType.multiAction(buttons).columns(buttons.size()).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    private String queryCommand() {
        return search.mode().queryCommand();
    }

    private MenuText nameLine(SearchQuery query, MenuActions actions) {
        var name = query.name().isBlank() ? "_____" : query.name();
        return MenuText.create()
                .text("<name><i18n:words.name>: <value><search_name> ", Values.text("search_name", name))
                .text("<click:suggest_command:'" + queryCommand() + " name '><modify>[<i18n:words.change>]</click>")
                .text(
                        "<act_name_clear><remove>[<i18n:words.clear>]</act_name_clear>",
                        actions.chatAction("act_name_clear", p -> search.change(p, q -> q.name(null))))
                .newLine();
    }

    private MenuText materialLines(SearchQuery query, MenuActions actions) {
        var text = MenuText.create()
                .text("<name><i18n:words.materials>: ")
                .text("<click:suggest_command:'" + queryCommand() + " material_add '><add>[<i18n:words.add>]</click>")
                .text(
                        "<act_materials_clear><remove>[<i18n:words.clear>]</act_materials_clear>",
                        actions.chatAction(
                                "act_materials_clear",
                                p -> search.change(p, q -> q.materials().clear())))
                .newLine();
        var materials = List.copyOf(query.materials());
        for (var i = 0; i < materials.size(); i++) {
            var material = materials.get(i);
            var valueKey = Keys.of("material", i);
            var removeKey = Keys.of("act_material", i, "remove");
            text.text("  <value><" + valueKey + "> ", Values.text(valueKey, material))
                    .text(
                            "<" + removeKey + "><remove>[<i18n:words.remove>]</" + removeKey + ">",
                            actions.chatAction(
                                    removeKey,
                                    p -> search.change(p, q -> q.materials().remove(material))))
                    .newLine();
        }
        if (materials.size() > 1) {
            text.append(toggle(
                    "<name><i18n:" + SETTING + "materialSearch> ",
                    actions,
                    "material_search",
                    List.of(
                            new Choice("any", "words.any", query.isAnyMaterial(), q -> q.anyMaterial(true)),
                            new Choice("all", "words.all", !query.isAnyMaterial(), q -> q.anyMaterial(false)))));
        }
        if (!materials.isEmpty()) {
            text.append(toggle(
                    "<name><i18n:" + SETTING + "materialMatch> ",
                    actions,
                    "material_match",
                    List.of(
                            new Choice("part", "words.part", !query.isExactMatch(), q -> q.exactMatch(false)),
                            new Choice("exact", "words.exact", query.isExactMatch(), q -> q.exactMatch(true)))));
        }
        return text;
    }

    private MenuText rangeLines(SearchQuery query) {
        var max = MenuText.create().text("<i18n:words.max>");
        return MenuText.create()
                .append(rangeLine("min_price", Values.number("min_price", query.minPrice())))
                .append(
                        query.maxPrice() == Double.MAX_VALUE
                                ? rangeLine("max_price", max)
                                : rangeLine("max_price", Values.number("max_price", query.maxPrice())))
                .append(rangeLine("min_size", Values.number("min_size", query.minOrderSize())))
                .append(
                        query.maxOrderSize() == Integer.MAX_VALUE
                                ? rangeLine("max_size", max)
                                : rangeLine("max_size", Values.number("max_size", query.maxOrderSize())));
    }

    private MenuText rangeLine(String input, TagResolver.Single value) {
        return MenuText.create()
                .text("<name><i18n:" + LABELS.get(input) + "> <value><" + value.key() + "> ", value)
                .text("<click:suggest_command:'" + queryCommand() + " " + input
                        + " '><modify>[<i18n:words.change>]</click>")
                .newLine();
    }

    private MenuText rangeLine(String input, MenuText max) {
        return MenuText.create()
                .text("<name><i18n:" + LABELS.get(input) + "> <value>")
                .append(max)
                .text(" <click:suggest_command:'" + queryCommand() + " " + input
                        + " '><modify>[<i18n:words.change>]</click>")
                .newLine();
    }

    private MenuText sortingLines(SearchQuery query, MenuActions actions) {
        var sortings = Arrays.stream(SortingType.values())
                .map(type -> new Choice(
                        SearchForm.sortingId(type),
                        type.translationKey(),
                        type == query.sortingType(),
                        q -> q.sortingType(type)))
                .toList();
        return MenuText.create()
                .text("<name><i18n:" + SETTING + "orderBy>:")
                .newLine()
                .append(toggle("  ", actions, "sorting", sortings))
                .append(toggle(
                        "  ",
                        actions,
                        "direction",
                        List.of(
                                new Choice("asc", "words.ascending", query.isAsc(), q -> q.asc(true)),
                                new Choice("desc", "words.descending", !query.isAsc(), q -> q.asc(false)))));
    }

    private MenuText serverLine(SearchQuery query, MenuActions actions) {
        var current = query.serverOrders();
        return toggle(
                "<name><i18n:" + SETTING + "serverOrders> ",
                actions,
                "server",
                List.of(
                        new Choice(
                                SearchForm.ALL,
                                SETTING + "serverOrders.all",
                                current == SearchQuery.ServerOrders.ALL,
                                q -> q.serverOrders(SearchQuery.ServerOrders.ALL)),
                        new Choice(
                                SearchForm.ONLY,
                                SETTING + "serverOrders.only",
                                current == SearchQuery.ServerOrders.ONLY,
                                q -> q.serverOrders(SearchQuery.ServerOrders.ONLY)),
                        new Choice(
                                SearchForm.HIDE,
                                SETTING + "serverOrders.hide",
                                current == SearchQuery.ServerOrders.HIDE,
                                q -> q.serverOrders(SearchQuery.ServerOrders.HIDE))));
    }

    private MenuText fillableLine(SearchQuery query, MenuActions actions) {
        return toggle(
                "<name><i18n:" + SETTING + "fillable> ",
                actions,
                "fillable",
                List.of(
                        new Choice("on", "words.on", query.fillable(), q -> q.fillable(true)),
                        new Choice("off", "words.off", !query.fillable(), q -> q.fillable(false))));
    }

    /**
     * A line of buttons of which one is active, each changing the query when clicked.
     */
    private MenuText toggle(String prefix, MenuActions actions, String group, List<Choice> choices) {
        var text = MenuText.create().text(prefix);
        for (var choice : choices) {
            var key = Keys.of("act", group, choice.id());
            text.text(
                    "<" + key + "><" + Colors.active(choice.active()) + ">[<i18n:" + choice.labelKey() + ">]</" + key
                            + "> ",
                    actions.chatAction(key, p -> search.change(p, choice.change())));
        }
        return text.newLine();
    }

    private static DialogInput textInput(SearchTexts texts, String key, String initial, int maxLength) {
        return DialogInput.text(key, label(texts, key))
                .initial(initial)
                .maxLength(maxLength)
                .build();
    }

    private static DialogInput options(SearchTexts texts, String key, String selected, Map<String, String> labels) {
        var entries = labels.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> SingleOptionDialogInput.OptionEntry.create(
                        entry.getKey(),
                        texts.locale(entry.getValue()),
                        entry.getKey().equals(selected)))
                .toList();
        return DialogInput.singleOption(key, label(texts, key), entries).build();
    }

    private static DialogInput sortingInput(SearchTexts texts, String selected) {
        var entries = Arrays.stream(SortingType.values())
                .map(type -> SingleOptionDialogInput.OptionEntry.create(
                        SearchForm.sortingId(type),
                        texts.locale(type.translationKey()),
                        SearchForm.sortingId(type).equals(selected)))
                .toList();
        return DialogInput.singleOption(SearchForm.SORTING, label(texts, SearchForm.SORTING), entries)
                .build();
    }

    private static Component label(SearchTexts texts, String input) {
        return texts.locale(Objects.requireNonNull(LABELS.get(input)));
    }

    private static String text(DialogResponseView response, String key) {
        return Objects.requireNonNullElse(response.getText(key), "");
    }

    /**
     * One button of a toggle line.
     *
     * @param id       id of the choice, part of the button's key
     * @param labelKey locale key of the label
     * @param active   whether the query holds this choice
     * @param change   applies the choice to the query
     */
    private record Choice(String id, String labelKey, boolean active, Consumer<SearchQuery> change) {}

    /**
     * What the search form shows.
     *
     * @param query        the player's query, shown in chat
     * @param form         the values of the dialog inputs
     * @param invalidInput key of the input whose value was refused, if any
     */
    public record Model(SearchQuery query, SearchForm form, Optional<String> invalidInput) {
        /**
         * @param query the player's query
         * @return the form showing the query
         */
        public static Model of(SearchQuery query) {
            return new Model(query, SearchForm.of(query), Optional.empty());
        }

        /**
         * @param query the player's query
         * @param form  the form as the player filled it in
         * @param input key of the input whose value was refused
         * @return the form as filled in, naming the refused input
         */
        public static Model invalid(SearchQuery query, SearchForm form, String input) {
            return new Model(query, form, Optional.of(input));
        }
    }
}
