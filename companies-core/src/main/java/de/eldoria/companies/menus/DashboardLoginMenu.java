/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * How a player logs in to the web dashboard: the short link, clickable and spelled out, and the six-letter code in
 * spaced capitals, so a player on a console can type both on a phone. As a dialog it adds a button opening the link;
 * Geyser shows the dialog to Bedrock players as a form, where the spelled out link and code are what counts, since a
 * Bedrock form cannot open a link. The dialog stays open after the link was opened, so the code is still there to
 * type into the browser.
 */
public final class DashboardLoginMenu implements Menu<DashboardLoginMenu.Login> {
    private static final int BUTTON_WIDTH = 200;

    private final Plugin plugin;

    /**
     * @param plugin plugin whose messages render the dialog
     */
    public DashboardLoginMenu(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public MenuText chat(Player player, Login model, MenuActions actions) {
        return MenuText.create()
                .locale(
                        "dashboard.link",
                        Values.click("act_open", ClickEvent.openUrl(model.link())),
                        Values.text("address", model.address()))
                .newLine()
                .locale("dashboard.code", Values.text("code", spaced(model.code())));
    }

    @Override
    public Optional<Dialog> dialog(Player player, Login model, MenuActions actions) {
        var title = render(player, MenuText.create().locale("dashboard.dialog.title"));
        var body = render(player, chat(player, model, actions));
        var open = ActionButton.create(
                render(player, MenuText.create().text("<add>").locale("dashboard.dialog.open")),
                null,
                BUTTON_WIDTH,
                DialogAction.staticAction(ClickEvent.openUrl(model.link())));
        var base = DialogBase.builder(title)
                .body(List.of(DialogBody.plainMessage(body)))
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(DialogType.notice(open))));
    }

    /**
     * @return false, the chat shows two lines and holds nothing back
     */
    @Override
    public boolean holdsChat() {
        return false;
    }

    /**
     * Spaces the letters of a login code, e.g. {@code K P M W Q R}.
     *
     * @param code the code
     * @return the spaced code in capitals
     */
    public static String spaced(String code) {
        return code.toUpperCase(Locale.ROOT)
                .chars()
                .mapToObj(letter -> String.valueOf((char) letter))
                .collect(Collectors.joining(" "));
    }

    private Component render(Player player, MenuText text) {
        return MessageSender.getPluginMessageSender(plugin)
                .serializeMessage(player, text.build(), text.replacements().toArray(TagResolver[]::new));
    }

    /**
     * A login the gateway prepared.
     *
     * @param link the short link
     * @param code the login code
     */
    public record Login(String link, String code) {
        /**
         * @return the link without its scheme, as a player types it
         */
        public String address() {
            return link.replaceFirst("^https?://", "");
        }
    }
}
