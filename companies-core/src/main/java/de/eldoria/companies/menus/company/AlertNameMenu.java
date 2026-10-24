/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.orders.query.OrderFilter;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Asks for the name under which the player's current search is saved as an order alert.
 * <p>
 * The dialog runs {@code /company alerts add <name>} with the entered name, so saving goes through the same checks as
 * typing the command. In chat the button suggests that command.
 */
public final class AlertNameMenu implements Menu<AlertNameMenu.Model> {
    /**
     * The command saving the current search as an alert, followed by the name.
     */
    public static final String ADD_COMMAND = "/company alerts add ";

    static final String NAME_INPUT = "alert_name";

    private final MessageSender messages;
    private final Consumer<Player> back;

    /**
     * @param messages renders the dialog texts
     * @param back     shows the search form again
     */
    public AlertNameMenu(MessageSender messages, Consumer<Player> back) {
        this.messages = messages;
        this.back = back;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions actions) {
        return MenuText.create()
                .text("<neutral><i18n:company.alerts.namePrompt> ")
                .append(saveButton());
    }

    /**
     * The chat button suggesting the command that saves the current search as an alert.
     *
     * @return the button
     */
    public static MenuText saveButton() {
        return MenuText.create()
                .text("<click:suggest_command:'" + ADD_COMMAND
                        + "'><add>[<i18n:company.order.search.saveAlert>]</click>");
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions actions) {
        var components = new MenuComponents(messages, player);
        var input = DialogInput.text(NAME_INPUT, components.locale("words.name"))
                .initial(model.name())
                .maxLength(OrderFilter.MAX_NAME_LENGTH)
                .build();
        var save = ActionButton.builder(components.locale("words.create"))
                .action(DialogAction.commandTemplate(ADD_COMMAND + "$(" + NAME_INPUT + ")"))
                .build();
        var cancel = actions.button(components.locale("words.back"), null, (clicker, response) -> back.accept(clicker));
        var base = DialogBase.builder(components.locale("company.order.search.saveAlert"))
                .body(List.of(DialogBody.plainMessage(components.locale("company.alerts.namePrompt"))))
                .inputs(List.of(input))
                .build();
        var type = DialogType.multiAction(List.of(save, cancel)).columns(2).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    @Override
    public boolean holdsChat() {
        return false;
    }

    /**
     * What the prompt shows.
     *
     * @param name name filled in at first
     */
    public record Model(String name) {}
}
