/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.configuration.elements.companysettings.ProfileSettings;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuService;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.services.ProfileChange;
import de.eldoria.companies.services.ProfileService.Outcome;
import de.eldoria.companies.util.ProfileColors;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The profile editor: description, tag and colour of a company, and its join mode for reference.
 * <p>
 * In chat every field has a {@code [Change]} button suggesting the typed command and a {@code [Clear]} callback; the
 * colour is picked from the palette by clicking it. After an edit the chat shows the editor again with the new values,
 * a refusal is sent as an error. As a dialog it is one form; {@code [Save]} applies every field at once and closes the
 * dialog, a refusal shows the form again with the error on top.
 */
public final class ProfileEditMenu implements Menu<ProfileEditView> {
    /**
     * The command editing the profile.
     */
    public static final String COMMAND = "/company profile edit";

    static final String DESCRIPTION = "description";
    static final String TAG = "tag";
    static final String COLOR = "color";
    static final String NO_COLOR = "none";

    private final MessageSender messages;
    private final MenuService menus;
    private final Supplier<ProfileSettings> settings;
    private final Edits edits;

    /**
     * @param messages renders the dialog texts and confirms a save
     * @param menus    shows the editor again after an edit
     * @param settings current limits of the profile fields
     * @param edits    applies the edits
     */
    public ProfileEditMenu(MessageSender messages, MenuService menus, Supplier<ProfileSettings> settings, Edits edits) {
        this.messages = messages;
        this.menus = menus;
        this.settings = settings;
        this.edits = edits;
    }

    @Override
    public MenuText chat(Player player, ProfileEditView model, MenuActions actions) {
        return title(model)
                .newLine()
                .append(descriptionLine(model, actions))
                .newLine()
                .append(tagLine(model, actions))
                .newLine()
                .append(colorLine(model, actions))
                .newLine()
                .append(joinModeLine(model));
    }

    @Override
    public Optional<Dialog> dialog(Player player, ProfileEditView model, MenuActions actions) {
        var texts = new SearchTexts(messages, player);
        List<DialogBody> body = new ArrayList<>();
        model.error()
                .ifPresent(error -> body.add(DialogBody.plainMessage(
                        messages.serializeError(player, "<i18n:" + error.localeKey() + ">", error.values()))));
        body.add(DialogBody.plainMessage(texts.of(joinModeLine(model))));
        var base = DialogBase.builder(texts.of(title(model)))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(body)
                .inputs(inputs(texts, model))
                .build();
        var save = actions.button(
                texts.locale("company.profile.edit.save"), null, (viewer, response) -> save(viewer, model, response));
        var cancel = actions.button(texts.locale("words.cancel"), null, (viewer, response) -> menus.close(viewer));
        return Optional.of(
                Dialog.create(factory -> factory.empty().base(base).type(DialogType.confirmation(save, cancel))));
    }

    /**
     * Reads the form of the dialog. An input without an answer is left unchanged.
     *
     * @param response values of the dialog inputs
     * @return the change the form asks for
     */
    static ProfileChange change(DialogResponseView response) {
        var color = Optional.ofNullable(response.getText(COLOR)).map(id -> NO_COLOR.equals(id) ? "" : id);
        return new ProfileChange(
                Optional.ofNullable(response.getText(DESCRIPTION)), Optional.ofNullable(response.getText(TAG)), color);
    }

    private void save(Player viewer, ProfileEditView model, DialogResponseView response) {
        edits.apply(viewer, change(response), outcome -> {
            switch (outcome) {
                case Outcome.Saved saved ->
                    menus.close(viewer)
                            .thenRun(() -> messages.sendMessage(
                                    viewer, MenuText.create().locale("company.profile.edit.saved")));
                case Outcome.Refused refused -> menus.show(viewer, this, model.withError(refused.failure()));
            }
        });
    }

    private void chatEdit(Player viewer, ProfileEditView model, ProfileChange change) {
        edits.apply(viewer, change, outcome -> {
            switch (outcome) {
                case Outcome.Saved saved ->
                    menus.show(viewer, this, ProfileEditView.of(saved.current(), settings.get()));
                case Outcome.Refused refused -> {
                    refused.failure().sendTo(viewer, messages);
                    menus.show(viewer, this, model);
                }
            }
        });
    }

    private MenuText descriptionLine(ProfileEditView model, MenuActions actions) {
        var text = MenuText.create().text("<name><i18n:company.profile.description>: ");
        text.append(model.description()
                .map(description ->
                        MenuText.create().text("<value><description>", Values.text("description", description)))
                .orElseGet(ProfileEditMenu::unset));
        return text.space()
                .append(change(DESCRIPTION))
                .append(clear("clear_description", model, actions, ProfileChange.description(""), model.description()));
    }

    private MenuText tagLine(ProfileEditView model, MenuActions actions) {
        var text = MenuText.create().text("<name><i18n:company.profile.tag>: ");
        text.append(model.tag()
                .map(tag -> MenuText.create()
                        .text(
                                "<company_tag>",
                                Values.component(
                                        "company_tag",
                                        Component.text(
                                                "[" + tag + "]",
                                                model.textColor().orElse(null)))))
                .orElseGet(ProfileEditMenu::unset));
        return text.space()
                .append(change(TAG))
                .append(clear("clear_tag", model, actions, ProfileChange.tag(""), model.tag()));
    }

    private MenuText colorLine(ProfileEditView model, MenuActions actions) {
        var text = MenuText.create().text("<name><i18n:company.profile.color>:");
        var palette = model.palette();
        for (var i = 0; i < palette.size(); i++) {
            var entry = palette.get(i);
            var key = Keys.of("pick", i);
            var swatch = Keys.of("swatch", i);
            var change = ProfileChange.color(entry);
            text.space()
                    .text(
                            "<" + key + "><" + swatch + "></" + key + ">",
                            actions.chatAction(key, viewer -> chatEdit(viewer, model, change)),
                            Values.component(swatch, swatch(entry, model.selected(entry))));
        }
        return text.space().append(clear("clear_color", model, actions, ProfileChange.color(""), model.color()));
    }

    private static Component swatch(String entry, boolean selected) {
        var color = ProfileColors.resolve(entry).orElse(null);
        var label = selected ? "[■]" : "■";
        return Component.text(label, color).hoverEvent(HoverEvent.showText(Component.text(entry, color)));
    }

    private MenuText clear(
            String key, ProfileEditView model, MenuActions actions, ProfileChange change, Optional<String> current) {
        if (current.isEmpty()) return MenuText.create();
        return MenuText.create()
                .text(
                        " <" + key + "><remove>[<i18n:words.clear>]</remove></" + key + ">",
                        actions.chatAction(key, viewer -> chatEdit(viewer, model, change)));
    }

    private static MenuText change(String field) {
        return MenuText.create()
                .text("<click:suggest_command:'" + COMMAND + " " + field + " '><modify>[<i18n:words.change>]</click>");
    }

    private static MenuText unset() {
        return MenuText.create().text("<inactive>-");
    }

    private static MenuText joinModeLine(ProfileEditView model) {
        return MenuText.create()
                .text("<name><i18n:company.profile.joinMode>: <value><i18n:"
                        + model.joinMode().translationKey() + ">");
    }

    private static MenuText title(ProfileEditView model) {
        return MenuText.create()
                .text("<heading>")
                .locale("company.profile.edit.title", Values.text("company_name", model.name()));
    }

    private static List<DialogInput> inputs(SearchTexts texts, ProfileEditView model) {
        var description = model.description().orElse("");
        var tag = model.tag().orElse("");
        return List.of(
                DialogInput.text(DESCRIPTION, texts.locale("company.profile.description"))
                        .initial(description.substring(0, Math.min(description.length(), model.maxDescriptionLength())))
                        .maxLength(model.maxDescriptionLength())
                        .width(300)
                        .build(),
                DialogInput.text(TAG, texts.locale("company.profile.tag"))
                        .initial(tag.substring(0, Math.min(tag.length(), model.maxTagLength())))
                        .maxLength(model.maxTagLength())
                        .build(),
                colorInput(texts, model));
    }

    private static DialogInput colorInput(SearchTexts texts, ProfileEditView model) {
        List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
        entries.add(SingleOptionDialogInput.OptionEntry.create(
                NO_COLOR,
                texts.locale("company.profile.edit.noColor"),
                model.color().isEmpty()));
        for (var entry : model.palette()) {
            entries.add(SingleOptionDialogInput.OptionEntry.create(
                    entry, Component.text(entry, ProfileColors.resolve(entry).orElse(null)), model.selected(entry)));
        }
        if (entries.stream().noneMatch(SingleOptionDialogInput.OptionEntry::initial)) {
            entries.set(
                    0,
                    SingleOptionDialogInput.OptionEntry.create(
                            NO_COLOR, texts.locale("company.profile.edit.noColor"), true));
        }
        return DialogInput.singleOption(COLOR, texts.locale("company.profile.color"), entries)
                .build();
    }

    /**
     * Applies the edits made in the menu. The answer runs on the main thread.
     */
    @FunctionalInterface
    public interface Edits {
        /**
         * @param editor player editing the profile
         * @param change the fields to change
         * @param then   receives what happened, on the main thread
         */
        void apply(Player editor, ProfileChange change, Consumer<Outcome> then);
    }
}
