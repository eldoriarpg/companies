/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.data.wrapper.order.MaterialPrice;
import de.eldoria.companies.data.wrapper.series.RepeatInterval;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.companies.orders.OrderDraftService;
import de.eldoria.companies.orders.PriceLines;
import de.eldoria.companies.orders.Repetition;
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
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The order builder: the draft of an order with its lines, limits and price.
 * <p>
 * In chat, the lines carry buttons that remove a line or take the market average, and the texts to change name,
 * amounts and prices are suggested as {@code /order draft …} commands. As dialog, the builder has a name input and a
 * button per line, which open the add and line dialogs with amount slider and unit price input. Both renderings
 * finish with done, which works once, and cancel.
 * <p>
 * A player who may make orders repeat sees how the order repeats and what each copy charges. In chat the repetition is
 * typed as {@code /order draft repeat …}; as dialog it is chosen in its own dialog with interval, copy count and end
 * date.
 * <p>
 * Exact lines never offer the market average.
 */
@SuppressWarnings("UnstableApiUsage")
public final class OrderBuilderMenu implements Menu<OrderBuilderMenu.View> {
    /**
     * Key of the name input.
     */
    static final String NAME = "name";

    /**
     * Key of the material input.
     */
    static final String MATERIAL = "material";

    /**
     * Key of the amount slider.
     */
    static final String AMOUNT = "amount";

    /**
     * Key of the unit price input.
     */
    static final String UNIT_PRICE = "unit_price";

    /**
     * Key of the interval choice of a repeating order.
     */
    static final String INTERVAL = "interval";

    /**
     * Key of the copy count input of a repeating order.
     */
    static final String COUNT = "count";

    /**
     * Key of the end date input of a repeating order.
     */
    static final String UNTIL = "until";

    /**
     * Option of the interval choice for a one-off order.
     */
    static final String ONCE = "once";

    private static final String DRAFT = "/order draft";

    private final OrderBuilderController controller;
    private final Economy economy;
    private final MessageSender messages;

    /**
     * @param controller performs the clicked actions
     * @param economy    economy formatting the amounts
     * @param messages   message sender rendering the dialog texts
     */
    public OrderBuilderMenu(OrderBuilderController controller, Economy economy, MessageSender messages) {
        this.controller = controller;
        this.economy = economy;
        this.messages = messages;
    }

    @Override
    public MenuText chat(Player player, View view, MenuActions actions) {
        var draft = view.draft();
        var text = MenuText.create()
                .text(
                        "<name><order_name> <click:suggest_command:" + DRAFT + " name ><modify>[",
                        Values.text("order_name", draft.name()))
                .locale("words.change")
                .text("]</click>")
                .newLine()
                .text("<name>")
                .locale("words.items")
                .text(": ");
        if (draft.canAdd()) {
            text.space()
                    .text("<click:suggest_command:" + DRAFT + " add ><add>[")
                    .locale("words.add")
                    .text("]</click> <click:suggest_command:" + DRAFT + " add hand ><add>[")
                    .locale("order.draft.addHand")
                    .text("]</click>");
        }
        for (var line : draft.lines()) {
            text.newLine().text("  ").append(chatLine(line, actions));
        }
        totals(text.newLine(), draft);
        if (draft.repeatable() || draft.repetition().isPresent()) {
            text.newLine().append(repeatChat(draft, actions));
        }
        return text.newLine()
                .text("<act_done><add>[", actions.chatActionOnce("act_done", controller::done))
                .locale("words.done")
                .text("]</act_done>")
                .space()
                .text("<act_cancel><remove>[", actions.chatActionOnce("act_cancel", controller::cancel))
                .locale("words.cancel")
                .text("]</act_cancel>");
    }

    @Override
    public Optional<Dialog> dialog(Player player, View view, MenuActions actions) {
        var dialog =
                switch (view.screen()) {
                    case Screen.Main main -> mainDialog(player, view, actions);
                    case Screen.Done done -> mainDialog(player, view, actions);
                    case Screen.Add add -> addDialog(player, view, add, actions);
                    case Screen.Edit edit -> editDialog(player, view, edit, actions);
                    case Screen.Repeat repeat -> repeatDialog(player, view, repeat, actions);
                };
        return Optional.of(dialog);
    }

    @Override
    public Duration lifetime() {
        return OrderDraftService.LIFETIME;
    }

    private MenuText chatLine(DraftView.Line line, MenuActions actions) {
        var number = line.number();
        var item = Keys.of("line", number, "item");
        var remove = Keys.of("line", number, "remove");
        var text = MenuText.create().text("<value>");
        if (line.market().isPresent()) {
            text.text("<hover:show_text:'")
                    .append(marketText(number, line.market().get()))
                    .text("'>");
        }
        text.text("<" + item + ">", Values.component(item, line.itemName()));
        if (line.market().isPresent()) text.text("</hover>");
        text.text(" <value>")
                .append(lineSum(number, line))
                .text(
                        " <" + remove + "><remove>[",
                        actions.chatActionOnce(
                                remove,
                                viewer -> change(
                                        viewer, draft -> controller.drafts().remove(draft, number))))
                .locale("words.remove")
                .text("]</" + remove + ">")
                .text(" <click:suggest_command:" + DRAFT + " amount " + number + " ><modify>[")
                .locale("words.amount")
                .text("]</click> <click:suggest_command:" + DRAFT + " price " + number + " ><modify>[")
                .locale("words.price")
                .text("]</click>");
        if (line.market().isPresent()) {
            var market = Keys.of("line", number, "market");
            text.text(
                            " <" + market + "><modify>[",
                            actions.chatAction(
                                    market,
                                    viewer -> change(
                                            viewer, draft -> controller.drafts().marketPrice(draft, number))))
                    .locale("order.draft.marketAverage")
                    .text("]</" + market + ">");
        }
        return text;
    }

    private MenuText repeatChat(DraftView draft, MenuActions actions) {
        var text = repeatSummary(draft);
        if (draft.repeatable()) {
            text.text(" <click:suggest_command:" + DRAFT + " repeat ><modify>[")
                    .locale("order.draft.repeat.change")
                    .text("]</click>");
        }
        if (draft.repetition().isPresent()) {
            text.text(
                            " <act_once><remove>[",
                            actions.chatActionOnce(
                                    "act_once",
                                    viewer -> change(
                                            viewer,
                                            current -> controller.drafts().once(current))))
                    .locale("order.draft.repeat.once")
                    .text("]</act_once>");
        }
        return text;
    }

    private MenuText repeatSummary(DraftView draft) {
        var text = MenuText.create()
                .text("<name>")
                .locale("order.draft.repeat.label")
                .text(": ");
        if (draft.repetition().isEmpty()) return text.text("<value>").locale("order.draft.repeat.once");
        return text.append(RepetitionTexts.summary(draft.repetition().get()))
                .newLine()
                .text("<name>")
                .locale("order.draft.repeat.charge", Values.money("copy_total", economy, draft.total()));
    }

    private void change(Player viewer, Consumer<OrderBuilder> change) {
        controller.apply(viewer, new Screen.Main(), (player, draft) -> {
            change.accept(draft);
            return new Screen.Main();
        });
    }

    private MenuText marketText(int number, MaterialPrice price) {
        var avg = Keys.of("line", number, "avg");
        var min = Keys.of("line", number, "min");
        var max = Keys.of("line", number, "max");
        return MenuText.create()
                .text("<yellow>")
                .locale("words.avg")
                .text(": <" + avg + "><br><green>", Values.money(avg, economy, price.avgPrice()))
                .locale("words.min")
                .text(": <" + min + "><br><red>", Values.money(min, economy, price.minPrice()))
                .locale("words.max")
                .text(": <" + max + ">", Values.money(max, economy, price.maxPrice()));
    }

    private MenuText lineSum(int number, DraftView.Line line) {
        var unit = Keys.of("line", number, "unit_price");
        var amount = Keys.of("line", number, "amount");
        var total = Keys.of("line", number, "total");
        return MenuText.create()
                .text(
                        "<" + unit + "> × <" + amount + "> = <" + total + ">",
                        Values.money(unit, economy, line.unitPrice()),
                        Values.number(amount, line.amount()),
                        Values.money(total, economy, line.total()));
    }

    private MenuText totals(MenuText text, DraftView draft) {
        text.text("<name>")
                .locale("words.lines")
                .text(
                        ": <value><lines>/<max_lines>",
                        Values.number("lines", draft.lines().size()),
                        Values.number("max_lines", draft.maxLines()))
                .newLine()
                .text("<name>")
                .locale("words.items")
                .text(
                        ": <value><items>/<max_items>",
                        Values.number("items", draft.items()),
                        Values.number("max_items", draft.maxItems()))
                .newLine()
                .text("<name>")
                .locale("words.price")
                .text(": <value><price>", Values.money("price", economy, draft.price()));
        return PriceLines.fee(text, economy, draft.price(), draft.fee());
    }

    private Dialog mainDialog(Player player, View view, MenuActions actions) {
        var draft = view.draft();
        var body = errorBody(player, view);
        for (var line : draft.lines()) {
            body.add(DialogBody.item(line.template())
                    .description(DialogBody.plainMessage(render(player, lineSum(line.number(), line))))
                    .build());
        }
        body.add(DialogBody.plainMessage(render(player, totals(MenuText.create(), draft))));

        var name = DialogInput.text(NAME, render(player, MenuText.create().locale("words.name")))
                .initial(draft.name())
                .maxLength(OrderDraftService.MAX_NAME_LENGTH - 1)
                .build();

        var buttons = new ArrayList<ActionButton>();
        for (var line : draft.lines()) {
            var number = line.number();
            buttons.add(actions.button(
                    line.itemName().append(Component.text(" × " + line.amount())),
                    render(player, lineSum(number, line)),
                    (viewer, response) -> controller.apply(viewer, new Screen.Main(), (p, current) -> {
                        rename(current, response);
                        var edited = current.line(number).orElseThrow(OrderBuilderMenu::unknownLine);
                        return new Screen.Edit(number, edited.amount(), DraftInput.unitPrice(edited.unitPrice()));
                    })));
        }
        if (draft.canAdd()) {
            buttons.add(actions.button(
                    locale(player, "words.add"),
                    null,
                    (viewer, response) -> controller.apply(viewer, new Screen.Main(), (p, current) -> {
                        rename(current, response);
                        return Screen.Add.empty();
                    })));
            buttons.add(actions.button(
                    locale(player, "order.draft.addHand"),
                    null,
                    (viewer, response) -> controller.apply(viewer, new Screen.Main(), (p, current) -> {
                        rename(current, response);
                        return Screen.Add.hand(controller.drafts(), p);
                    })));
        }
        if (draft.repeatable()) {
            buttons.add(actions.button(
                    locale(player, "order.draft.repeat.change"),
                    render(player, repeatSummary(draft)),
                    (viewer, response) -> controller.apply(viewer, new Screen.Main(), (p, current) -> {
                        rename(current, response);
                        return Screen.Repeat.of(current.repetition());
                    })));
        }
        buttons.add(actions.buttonOnce(
                locale(player, "words.done"),
                null,
                (viewer, response) -> controller.apply(viewer, new Screen.Main(), (p, current) -> {
                    rename(current, response);
                    return new Screen.Done();
                })));
        buttons.add(actions.buttonOnce(
                locale(player, "words.cancel"), null, (viewer, response) -> controller.cancel(viewer)));

        return dialog(
                render(player, MenuText.create().locale("order.draft.title", Values.text("order_name", draft.name()))),
                body,
                List.of(name),
                buttons);
    }

    private Dialog addDialog(Player player, View view, Screen.Add add, MenuActions actions) {
        var body = errorBody(player, view);
        var inputs = new ArrayList<DialogInput>();
        if (add.hand().isPresent()) {
            body.add(DialogBody.item(add.hand().get().template()).build());
        } else {
            inputs.add(DialogInput.text(MATERIAL, locale(player, "words.material"))
                    .initial(add.material())
                    .build());
        }
        var remaining = view.draft().remainingItems();
        inputs.add(amountSlider(player, add.amount(), remaining));
        inputs.add(unitPriceInput(player, add.unitPrice()));

        var buttons = new ArrayList<ActionButton>();
        buttons.add(actions.button(locale(player, "words.add"), null, (viewer, response) -> {
            var entered = add.entered(response, remaining);
            controller.apply(viewer, entered, (p, current) -> {
                var price = unitPrice(entered.unitPrice());
                controller.drafts().add(current, entered.item(), entered.amount(), price);
                return new Screen.Main();
            });
        }));
        if (add.hand().map(requested -> !requested.exact()).orElse(true)) {
            buttons.add(actions.button(
                    locale(player, "order.draft.marketAverage"),
                    null,
                    (viewer, response) -> controller.marketPrice(viewer, add.entered(response, remaining))));
        }
        buttons.add(back(player, actions));
        return dialog(locale(player, "order.draft.addTitle"), body, inputs, buttons);
    }

    private Dialog editDialog(Player player, View view, Screen.Edit edit, MenuActions actions) {
        var line = view.draft().line(edit.line());
        if (line.isEmpty()) return mainDialog(player, view, actions);
        var shown = line.get();
        var number = edit.line();
        var max = view.draft().maxAmount(shown);

        var body = errorBody(player, view);
        body.add(DialogBody.item(shown.template())
                .description(DialogBody.plainMessage(render(player, lineSum(number, shown))))
                .build());
        shown.market().ifPresent(price -> body.add(DialogBody.plainMessage(render(player, marketText(number, price)))));

        var buttons = new ArrayList<ActionButton>();
        buttons.add(actions.button(locale(player, "order.draft.save"), null, (viewer, response) -> {
            var entered = edit.entered(response, max);
            controller.apply(viewer, entered, (p, current) -> {
                var price = unitPrice(entered.unitPrice());
                controller.drafts().amount(current, number, entered.amount());
                controller.drafts().unitPrice(current, number, price);
                return new Screen.Main();
            });
        }));
        shown.market()
                .ifPresent(price -> buttons.add(actions.button(
                        locale(player, "order.draft.marketAverage"),
                        null,
                        (viewer, response) -> controller.apply(viewer, edit, (p, current) -> edit.entered(response, max)
                                .withUnitPrice(price.avgPrice())))));
        buttons.add(actions.buttonOnce(
                locale(player, "words.remove"),
                null,
                (viewer, response) -> controller.apply(viewer, edit, (p, current) -> {
                    controller.drafts().remove(current, number);
                    return new Screen.Main();
                })));
        buttons.add(back(player, actions));

        var inputs = List.<DialogInput>of(
                amountSlider(player, edit.amount(), max), unitPriceInput(player, edit.unitPrice()));
        return dialog(
                render(
                        player,
                        MenuText.create()
                                .locale("order.draft.editTitle", Values.component("line_item", shown.itemName()))),
                body,
                inputs,
                buttons);
    }

    private Dialog repeatDialog(Player player, View view, Screen.Repeat repeat, MenuActions actions) {
        var body = errorBody(player, view);
        body.add(DialogBody.plainMessage(render(player, repeatSummary(view.draft()))));
        body.add(DialogBody.plainMessage(render(player, MenuText.create().locale("order.draft.repeat.hint"))));
        var options = List.of(
                option(ONCE, locale(player, "order.draft.repeat.once"), repeat),
                option(RepeatInterval.DAILY.name(), locale(player, RepeatInterval.DAILY.translationKey()), repeat),
                option(RepeatInterval.WEEKLY.name(), locale(player, RepeatInterval.WEEKLY.translationKey()), repeat));
        var inputs = List.<DialogInput>of(
                DialogInput.singleOption(INTERVAL, locale(player, "order.draft.repeat.interval"), options)
                        .build(),
                DialogInput.text(COUNT, locale(player, "order.draft.repeat.count"))
                        .initial(repeat.count())
                        .build(),
                DialogInput.text(UNTIL, locale(player, "order.draft.repeat.until"))
                        .initial(repeat.until())
                        .build());
        var buttons = new ArrayList<ActionButton>();
        buttons.add(actions.button(locale(player, "order.draft.save"), null, (viewer, response) -> {
            var entered = repeat.entered(response);
            controller.apply(viewer, entered, (p, current) -> {
                entered.applyTo(controller.drafts(), p, current);
                return new Screen.Main();
            });
        }));
        buttons.add(back(player, actions));
        return dialog(locale(player, "order.draft.repeat.title"), body, inputs, buttons);
    }

    private static SingleOptionDialogInput.OptionEntry option(String id, Component label, Screen.Repeat repeat) {
        return SingleOptionDialogInput.OptionEntry.create(id, label, id.equalsIgnoreCase(repeat.interval()));
    }

    private ActionButton back(Player player, MenuActions actions) {
        return actions.button(
                locale(player, "order.draft.back"),
                null,
                (viewer, response) -> controller.apply(viewer, new Screen.Main(), (p, current) -> new Screen.Main()));
    }

    private DialogInput amountSlider(Player player, int amount, int max) {
        var end = Math.max(2, max);
        return DialogInput.numberRange(AMOUNT, locale(player, "words.amount"), 1, end)
                .step(1f)
                .initial((float) Math.min(Math.max(1, amount), end))
                .build();
    }

    private DialogInput unitPriceInput(Player player, String unitPrice) {
        return DialogInput.text(UNIT_PRICE, locale(player, "order.draft.unitPrice"))
                .initial(unitPrice)
                .build();
    }

    private List<DialogBody> errorBody(Player player, View view) {
        var body = new ArrayList<DialogBody>();
        view.error().ifPresent(error -> body.add(DialogBody.plainMessage(error.render(player, messages))));
        return body;
    }

    private static Dialog dialog(
            Component title, List<DialogBody> body, List<DialogInput> inputs, List<ActionButton> buttons) {
        var base = DialogBase.builder(title)
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(body)
                .inputs(inputs)
                .build();
        return Dialog.create(factory -> factory.empty()
                .base(base)
                .type(DialogType.multiAction(buttons).columns(2).build()));
    }

    private void rename(OrderBuilder draft, DialogResponseView response) {
        var name = response.getText(NAME);
        if (name == null || name.isBlank() || name.equals(draft.name())) return;
        controller.drafts().rename(draft, name);
    }

    private Component locale(Player player, String key) {
        return render(player, MenuText.create().locale(key));
    }

    private Component render(Player player, MenuText text) {
        return messages.serializeMessage(
                player, text.build(), text.replacements().toArray(TagResolver[]::new));
    }

    private static double unitPrice(String entered) {
        return DraftInput.unitPrice(entered)
                .orElseThrow(() -> CommandFailure.actionBar("order.draft.error.invalidPrice"));
    }

    private static CommandFailure unknownLine() {
        return CommandFailure.actionBar("order.draft.error.unknownLine");
    }

    /**
     * What the builder shows.
     *
     * @param draft  the draft
     * @param screen which part of the builder is shown; chat always shows the whole draft
     * @param error  why the last action was refused, shown on top of a dialog
     */
    public record View(DraftView draft, Screen screen, Optional<CommandFailure> error) {
        /**
         * @param draft  the draft
         * @param screen which part of the builder is shown
         * @param error  why the last action was refused
         */
        public View {
            Objects.requireNonNull(screen);
        }
    }

    /**
     * Which dialog of the builder is shown, with what was entered into it.
     */
    public sealed interface Screen permits Screen.Main, Screen.Add, Screen.Edit, Screen.Repeat, Screen.Done {
        /**
         * The draft with its lines.
         */
        record Main() implements Screen {}

        /**
         * Finishing the draft, which creates the order instead of showing a dialog.
         */
        record Done() implements Screen {}

        /**
         * Adding an item, either the one held when the dialog was opened or a typed material.
         *
         * @param hand      the held item, empty when a material is typed
         * @param material  entered material
         * @param amount    entered amount
         * @param unitPrice entered unit price
         */
        record Add(Optional<OrderDraftService.RequestedItem> hand, String material, int amount, String unitPrice)
                implements Screen {
            /**
             * @return the add dialog for a typed material, without entries
             */
            static Add empty() {
                return new Add(Optional.empty(), "", 1, "");
            }

            /**
             * The add dialog for the item in the main hand of a player.
             *
             * @param drafts turns the item into what a line requests
             * @param player player holding the item
             * @return the screen
             * @throws CommandFailure when the hand is empty or the item can not be ordered
             */
            static Add hand(OrderDraftService drafts, Player player) {
                var hand = player.getInventory().getItemInMainHand();
                if (hand.isEmpty()) throw CommandFailure.actionBar("order.draft.error.emptyHand");
                var requested = drafts.request(hand)
                        .orElseThrow(() -> CommandFailure.actionBar("order.draft.error.notOrderable"));
                return new Add(Optional.of(requested), "", 1, "");
            }

            /**
             * @param response values of the dialog inputs
             * @param max      largest amount of the slider
             * @return this screen with what was entered
             */
            Add entered(DialogResponseView response, int max) {
                return new Add(
                        hand,
                        Objects.requireNonNullElse(response.getText(MATERIAL), material),
                        DraftInput.amount(response.getFloat(AMOUNT), max),
                        Objects.requireNonNullElse(response.getText(UNIT_PRICE), unitPrice));
            }

            /**
             * @param price market average to fill in
             * @return this screen with the unit price filled in
             */
            Add withUnitPrice(double price) {
                return new Add(hand, material, amount, DraftInput.unitPrice(price));
            }

            /**
             * @return the item to add: the held one, or the entered material
             * @throws CommandFailure when the entered material is no item
             */
            ItemStack item() {
                if (hand.isPresent()) return hand.get().template();
                return DraftInput.material(material)
                        .map(ItemStack::of)
                        .orElseThrow(() -> CommandFailure.actionBar(
                                "order.draft.error.unknownMaterial", Values.text("material", material)));
            }
        }

        /**
         * Changing a line.
         *
         * @param line      number of the line, starting at 1
         * @param amount    entered amount
         * @param unitPrice entered unit price
         */
        record Edit(int line, int amount, String unitPrice) implements Screen {
            /**
             * @param response values of the dialog inputs
             * @param max      largest amount of the slider
             * @return this screen with what was entered
             */
            Edit entered(DialogResponseView response, int max) {
                return new Edit(
                        line,
                        DraftInput.amount(response.getFloat(AMOUNT), max),
                        Objects.requireNonNullElse(response.getText(UNIT_PRICE), unitPrice));
            }

            /**
             * @param price market average to fill in
             * @return this screen with the unit price filled in
             */
            Edit withUnitPrice(double price) {
                return new Edit(line, amount, DraftInput.unitPrice(price));
            }
        }

        /**
         * Choosing how the order repeats.
         *
         * @param interval chosen interval, {@link #ONCE} for a one-off order
         * @param count    entered number of copies, blank without count limit
         * @param until    entered end date, blank without end date
         */
        record Repeat(String interval, String count, String until) implements Screen {
            /**
             * @param repetition current repetition of the draft
             * @return the dialog showing it
             */
            static Repeat of(Optional<Repetition> repetition) {
                if (repetition.isEmpty()) return new Repeat(ONCE, "", "");
                var current = repetition.get();
                var count = current.count().isPresent()
                        ? String.valueOf(current.count().getAsInt())
                        : "";
                var until = current.until().map(LocalDate::toString).orElse("");
                return new Repeat(current.interval().name(), count, until);
            }

            /**
             * @param response values of the dialog inputs
             * @return this screen with what was entered
             */
            Repeat entered(DialogResponseView response) {
                return new Repeat(
                        Objects.requireNonNullElse(response.getText(INTERVAL), interval),
                        Objects.requireNonNullElse(response.getText(COUNT), count),
                        Objects.requireNonNullElse(response.getText(UNTIL), until));
            }

            /**
             * Makes the draft repeat as entered, or a one-off order when no interval is chosen.
             *
             * @param drafts performs the change
             * @param player owner of the draft
             * @param draft  the draft
             * @throws CommandFailure when the count or the date can not be read, or the repetition is refused
             */
            void applyTo(OrderDraftService drafts, Player player, OrderBuilder draft) {
                var chosen = Repetition.interval(interval);
                if (chosen.isEmpty()) {
                    drafts.once(draft);
                    return;
                }
                var copies = Repetition.count(count);
                if (!count.isBlank() && copies.isEmpty()) throw CommandFailure.actionBar("error.invalidNumber");
                var end = Repetition.date(until);
                if (!until.isBlank() && end.isEmpty()) {
                    throw CommandFailure.actionBar("order.draft.repeat.error.date");
                }
                drafts.repeat(draft, player, new Repetition(chosen.get(), copies, end));
            }
        }
    }
}
