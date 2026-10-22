/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.menus.Keys;
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

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Edits the company permissions of a member.
 * <p>
 * The dialog has one toggle per permission the editor may change and a {@code [Save]} button that applies the
 * differences; permissions the editor may not change are listed in the body. In chat every permission is a button
 * that gives or removes it, greyed out when the editor may not change it.
 */
public final class PermissionMenu implements Menu<PermissionEdit> {
    private final MessageSender messages;
    private final MenuService menus;
    private final Changes changes;

    /**
     * @param messages renders the dialog texts
     * @param menus    shows the chat rendering again after a change
     * @param changes  applies the changes the editor asks for
     */
    public PermissionMenu(MessageSender messages, MenuService menus, Changes changes) {
        this.messages = messages;
        this.menus = menus;
        this.changes = changes;
    }

    @Override
    public MenuText chat(Player player, PermissionEdit model, MenuActions actions) {
        var text = title(model).newLine();
        for (var permission : PermissionEdit.shown()) {
            text.append(chatToggle(model, actions, permission)).space();
        }
        return text;
    }

    @Override
    public Optional<Dialog> dialog(Player player, PermissionEdit model, MenuActions actions) {
        var base = DialogBase.builder(render(player, title(model)))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(body(player, model))
                .inputs(inputs(player, model))
                .build();
        var save = actions.button(
                render(player, MenuText.create().locale("company.permission.save")),
                null,
                (viewer, response) -> save(viewer, model, response));
        var cancel = actions.button(
                render(player, MenuText.create().locale("words.cancel")),
                null,
                (viewer, response) -> viewer.closeDialog());
        return Optional.of(
                Dialog.create(factory -> factory.empty().base(base).type(DialogType.confirmation(save, cancel))));
    }

    /**
     * The key of the dialog toggle of a permission.
     *
     * @param permission permission of the toggle
     * @return input key
     */
    static String inputKey(CompanyPermission permission) {
        return permission.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Reads the toggles of the permissions the editor may change from a dialog response.
     *
     * @param model    the edit the dialog was built for
     * @param response values of the dialog inputs
     * @return the wanted state of each answered permission
     */
    static Map<CompanyPermission, Boolean> wanted(PermissionEdit model, DialogResponseView response) {
        Map<CompanyPermission, Boolean> wanted = new EnumMap<>(CompanyPermission.class);
        for (var permission : PermissionEdit.shown()) {
            if (!model.editable(permission)) continue;
            var value = response.getBoolean(inputKey(permission));
            if (value != null) wanted.put(permission, value);
        }
        return wanted;
    }

    private void save(Player viewer, PermissionEdit model, DialogResponseView response) {
        viewer.closeDialog();
        changes.apply(
                viewer,
                model.target().uuid(),
                wanted(model, response),
                applied -> messages.sendMessage(
                        viewer, MenuText.create().locale("company.permission.saved", member(applied))));
    }

    private MenuText chatToggle(PermissionEdit model, MenuActions actions, CompanyPermission permission) {
        var label = "[<i18n:" + permission.translationKey() + ">]";
        if (!model.editable(permission)) {
            return MenuText.create().text("<inactive>" + label + "</inactive>");
        }
        var granted = model.granted(permission);
        var key = Keys.of("toggle", inputKey(permission));
        var toggle = actions.chatAction(
                key,
                viewer -> changes.apply(
                        viewer,
                        model.target().uuid(),
                        Map.of(permission, !granted),
                        applied -> menus.show(viewer, this, applied)));
        var style = granted ? "add" : "remove";
        return MenuText.create()
                .text("<" + key + "><u><" + style + ">" + label + "</" + style + "></u></" + key + ">", toggle);
    }

    private List<DialogInput> inputs(Player player, PermissionEdit model) {
        return PermissionEdit.shown().stream()
                .filter(model::editable)
                .map(permission -> (DialogInput) DialogInput.bool(
                                inputKey(permission),
                                render(player, MenuText.create().locale(permission.translationKey())))
                        .initial(model.granted(permission))
                        .build())
                .toList();
    }

    private List<DialogBody> body(Player player, PermissionEdit model) {
        var locked = PermissionEdit.shown().stream()
                .filter(permission -> !model.editable(permission))
                .toList();
        if (locked.isEmpty()) return List.of();
        var text = MenuText.create().text("<inactive>").locale("company.permission.locked");
        for (var permission : locked) {
            var style = model.granted(permission) ? "add" : "remove";
            text.space().text("<" + style + ">[<i18n:" + permission.translationKey() + ">]</" + style + ">");
        }
        return List.of(DialogBody.plainMessage(render(player, text)));
    }

    private static MenuText title(PermissionEdit model) {
        return MenuText.create()
                .text("<heading>")
                .locale("company.permission.permissions")
                .text(" <value><member>:", member(model));
    }

    private static TagResolver.Single member(PermissionEdit model) {
        var player = model.target().player();
        return Values.text(
                "member",
                Objects.requireNonNullElse(
                        player.getName(), player.getUniqueId().toString()));
    }

    private Component render(Player player, MenuText text) {
        return messages.serializeMessage(
                player, text.build(), text.replacements().toArray(TagResolver[]::new));
    }

    /**
     * Applies the permission changes an editor asks for. It checks the editor's rights again, since the menu may be
     * older than the company's current state.
     */
    @FunctionalInterface
    public interface Changes {
        /**
         * Applies the wanted permission states to a member.
         *
         * @param editor    player asking for the changes
         * @param target    member whose permissions change
         * @param wanted    whether each permission should be held
         * @param onApplied runs with the edit once the changes are applied
         */
        void apply(
                Player editor, UUID target, Map<CompanyPermission, Boolean> wanted, Consumer<PermissionEdit> onApplied);
    }
}
