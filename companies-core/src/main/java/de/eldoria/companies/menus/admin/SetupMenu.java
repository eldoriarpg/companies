/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuService;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The in-game part of the setup, {@code /companyadmin setup}: what only the server can set before the dashboard can
 * take over. It shows the database, the network, the server's role and the dashboard with what to change, and lets an
 * admin name the server, switch the dashboard on and enter its key. Everything else is set up on the dashboard, which
 * the menu points to at the end.
 * <p>
 * The database and the role are only shown, with where to change them, since both are read at start. The key is only
 * asked for while the network has none and only from an admin who may run {@code /companyadmin dashboard key}; it is
 * never filled in or shown, and it is stored like that command stores it. Only what the admin changed is applied, so
 * a dashboard another admin switched meanwhile is not switched back. The chat rendering offers the same through commands.
 */
public final class SetupMenu implements Menu<SetupMenu.View> {
    /**
     * The command of the in-game setup.
     */
    public static final String COMMAND = "/companyadmin setup";

    static final String NAME = "name";
    static final String DASHBOARD = "dashboard";
    static final String KEY = "key";
    private static final int MAX_KEY = 256;
    private static final int MAX_NAME = 32;

    private final MessageSender messages;
    private final MenuService menus;
    private final Changes changes;

    /**
     * @param messages renders the texts
     * @param menus    shows the menu again after a refused change
     * @param changes  applies what the admin entered
     */
    public SetupMenu(MessageSender messages, MenuService menus, Changes changes) {
        this.messages = messages;
        this.menus = menus;
        this.changes = changes;
    }

    @Override
    public MenuText chat(Player player, View model, MenuActions actions) {
        var text = status(model.status());
        text.newLine()
                .text(
                        "<name><i18n:companyadmin.setup.serverName>: <value><server_name></value> "
                                + "<click:suggest_command:'" + COMMAND
                                + " name '><modify>[<i18n:words.change>]</click>",
                        Values.text("server_name", shown(model.status().name())));
        text.newLine()
                .text("<name><i18n:companyadmin.setup.dashboard>: "
                        + "<click:run_command:'" + COMMAND
                        + " dashboard on'><add>[<i18n:companyadmin.setup.on>]</click> "
                        + "<click:run_command:'" + COMMAND
                        + " dashboard off'><remove>[<i18n:companyadmin.setup.off>]</click>");
        if (!model.status().keySet() && model.mayEnterKey()) {
            text.newLine()
                    .text(
                            "<name><i18n:companyadmin.setup.key>: "
                                    + "<click:suggest_command:'/companyadmin dashboard key '><modify>[<i18n:companyadmin.setup.enterKey>]</click>");
        }
        return text.newLine().append(next());
    }

    @Override
    public Optional<Dialog> dialog(Player player, View model, MenuActions actions) {
        List<DialogBody> body = new ArrayList<>();
        model.error()
                .ifPresent(error -> body.add(DialogBody.plainMessage(
                        messages.serializeError(player, "<i18n:" + error.localeKey() + ">", error.values()))));
        body.add(DialogBody.plainMessage(render(player, status(model.status()))));
        body.add(DialogBody.plainMessage(render(player, MenuText.create().locale("companyadmin.setup.keyHint"))));
        var base = DialogBase.builder(
                        render(player, MenuText.create().text("<heading>").locale("companyadmin.setup.title")))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(body)
                .inputs(inputs(player, model))
                .build();
        var save = actions.buttonOnce(
                render(player, MenuText.create().locale("companyadmin.setup.save")),
                null,
                (viewer, response) -> save(viewer, model, response));
        var cancel = actions.button(
                render(player, MenuText.create().locale("words.cancel")),
                null,
                (viewer, response) -> menus.close(viewer));
        return Optional.of(
                Dialog.create(factory -> factory.empty().base(base).type(DialogType.confirmation(save, cancel))));
    }

    /**
     * Reads the form of the dialog. An empty key means none was entered.
     *
     * @param response values of the dialog inputs
     * @return what the admin entered
     */
    static Input input(DialogResponseView response) {
        var key = Optional.ofNullable(response.getText(KEY)).map(String::strip).filter(entered -> !entered.isEmpty());
        var dashboard = response.getBoolean(DASHBOARD);
        return new Input(
                Optional.ofNullable(response.getText(NAME)).map(String::strip).orElse(""),
                dashboard != null && dashboard,
                key);
    }

    private void save(Player viewer, View model, DialogResponseView response) {
        changes.apply(viewer, model, input(response), failure -> {
            if (failure != null) {
                menus.show(viewer, this, new View(model.status(), model.mayEnterKey(), Optional.of(failure)));
                return;
            }
            menus.close(viewer)
                    .thenRun(() -> messages.sendMessage(
                            viewer,
                            MenuText.create()
                                    .locale("companyadmin.setup.saved")
                                    .newLine()
                                    .append(next())));
        });
    }

    /**
     * What the setup shows about the server, for the chat, the console and the dialog.
     *
     * @param status the server's state
     * @return the text
     */
    public static MenuText status(SetupStatus status) {
        var text = MenuText.create().text("<heading>").locale("companyadmin.setup.title");
        text.newLine()
                .locale(
                        "companyadmin.setup.database",
                        Values.text("storage", status.storage().name().toLowerCase(Locale.ROOT)),
                        Values.number("nodes", status.nodes()));
        text.newLine()
                .locale(
                        "companyadmin.setup.role",
                        Values.text("role", status.role().name().toLowerCase(Locale.ROOT)),
                        Values.text("server_name", shown(status.name())));
        text.newLine()
                .locale(
                        status.dashboardEnabled()
                                ? "companyadmin.setup.dashboardOn"
                                : "companyadmin.setup.dashboardOff",
                        Values.text("connection", status.connection()));
        for (var recommendation : status.recommendations()) {
            text.newLine().text("<neutral>• ").locale(recommendation);
        }
        return text;
    }

    private static MenuText next() {
        return MenuText.create()
                .text("<click:run_command:'/company dashboard'><add>[<i18n:companyadmin.setup.openDashboard>]</click>");
    }

    private static String shown(String name) {
        return name.isEmpty() ? "-" : name;
    }

    private List<DialogInput> inputs(Player player, View model) {
        var status = model.status();
        var inputs = new ArrayList<DialogInput>();
        inputs.add(DialogInput.text(NAME, render(player, MenuText.create().locale("companyadmin.setup.serverName")))
                .initial(status.name())
                .maxLength(MAX_NAME)
                .build());
        inputs.add(DialogInput.bool(DASHBOARD, render(player, MenuText.create().locale("companyadmin.setup.dashboard")))
                .initial(status.dashboardEnabled())
                .build());
        if (!status.keySet() && model.mayEnterKey()) {
            inputs.add(DialogInput.text(KEY, render(player, MenuText.create().locale("companyadmin.setup.key")))
                    .initial("")
                    .maxLength(MAX_KEY)
                    .width(300)
                    .build());
        }
        return inputs;
    }

    private Component render(Player player, MenuText text) {
        return messages.serializeMessage(
                player, text.build(), text.replacements().toArray(TagResolver[]::new));
    }

    /**
     * What the in-game setup shows.
     *
     * @param status      the server's state, as the menu was opened
     * @param mayEnterKey whether the admin may enter the dashboard key, which needs the dashboard permission
     * @param error       why the last change was refused, if it was
     */
    public record View(SetupStatus status, boolean mayEnterKey, Optional<CommandFailure> error) {
        /**
         * @param status      the server's state
         * @param mayEnterKey whether the admin may enter the dashboard key
         * @return the view without an error
         */
        public static View of(SetupStatus status, boolean mayEnterKey) {
            return new View(status, mayEnterKey, Optional.empty());
        }
    }

    /**
     * What the admin entered.
     *
     * @param name      the server's name, empty for none
     * @param dashboard whether the dashboard connects
     * @param key       the dashboard key, none when none was entered
     */
    public record Input(String name, boolean dashboard, Optional<String> key) {}

    /**
     * Applies what the admin entered. The answer runs on the main thread.
     */
    @FunctionalInterface
    public interface Changes {
        /**
         * @param admin  the admin
         * @param opened what the menu showed when the admin opened it, so only what the admin changed is applied
         * @param input  what was entered
         * @param then   receives why it was refused, null when it was applied
         */
        void apply(Player admin, View opened, Input input, Consumer<@Nullable CommandFailure> then);
    }
}
