/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.profile;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.PaletteColors;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.configuration.elements.companysettings.ProfileSettings;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.company.ProfileEditMenu;
import de.eldoria.companies.menus.company.ProfileEditView;
import de.eldoria.companies.services.ProfileChange;
import de.eldoria.companies.services.ProfileService;
import de.eldoria.companies.services.ProfileService.Outcome;
import de.eldoria.companies.util.Threading;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * {@code /company profile edit [description [text…]|tag <tag>|tag clear|color <color>|color clear]}: shows the
 * profile editor or changes one field of the profile of the sender's company.
 * <p>
 * The typed commands answer in chat; the editor, opened without arguments, shows the new values instead.
 */
@Subcommand("edit")
@PlayerOnly
public final class ProfileEditCommand {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public ProfileEditCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Shows the profile editor of the sender's company.
     *
     * @param sender sender of the command
     */
    @Executes
    public void show(CommandSender sender) {
        show(Senders.player(sender));
    }

    /**
     * Shows the profile editor of the player's company.
     *
     * @param player editing player
     */
    public void show(Player player) {
        var menus = deps.services().menus();
        var menu = menu();
        deps.companyData()
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .thenAcceptAsync(
                        optProfile -> {
                            if (optProfile.isEmpty()) {
                                deps.messages().sendErrorActionBar(player, "error.noMember");
                                return;
                            }
                            if (!ProfileService.mayEdit(optProfile.get(), player)) {
                                deps.messages().sendErrorActionBar(player, "error.permission.editProfile");
                                return;
                            }
                            menus.show(player, menu, ProfileEditView.of(optProfile.get(), settings()));
                        },
                        Threading.mainThread(deps.plugin()))
                .exceptionally(this::logFailure);
    }

    /**
     * Removes the description.
     *
     * @param sender sender of the command
     */
    @Executes("description")
    public void clearDescription(CommandSender sender) {
        typed(sender, ProfileChange.description(""));
    }

    /**
     * Sets the description.
     *
     * @param sender      sender of the command
     * @param description new description
     */
    @Executes("description")
    public void description(CommandSender sender, @StringArg(StringArgType.GREEDY) String description) {
        typed(sender, ProfileChange.description(description));
    }

    /**
     * Removes the tag.
     *
     * @param sender sender of the command
     */
    @Executes("tag clear")
    public void clearTag(CommandSender sender) {
        typed(sender, ProfileChange.tag(""));
    }

    /**
     * Sets the tag.
     *
     * @param sender sender of the command
     * @param tag    new tag, normalised to upper case
     */
    @Executes("tag")
    public void tag(CommandSender sender, String tag) {
        typed(sender, ProfileChange.tag(tag));
    }

    /**
     * Removes the colour.
     *
     * @param sender sender of the command
     */
    @Executes("color clear")
    public void clearColor(CommandSender sender) {
        typed(sender, ProfileChange.color(""));
    }

    /**
     * Sets the colour.
     *
     * @param sender sender of the command
     * @param color  a colour of the palette
     */
    @Executes("color")
    public void color(CommandSender sender, @PaletteColors @StringArg(StringArgType.GREEDY) String color) {
        typed(sender, ProfileChange.color(color));
    }

    private void typed(CommandSender sender, ProfileChange change) {
        var player = Senders.player(sender);
        deps.requireReady();
        apply(player, change, outcome -> {
            switch (outcome) {
                case Outcome.Saved saved ->
                    deps.messages().sendMessage(player, MenuText.create().locale("company.profile.edit.saved"));
                case Outcome.Refused refused -> refused.failure().sendTo(player, deps.messages());
            }
        });
    }

    private void apply(Player player, ProfileChange change, Consumer<Outcome> then) {
        deps.services()
                .profiles()
                .edit(player, change)
                .thenAcceptAsync(then, Threading.mainThread(deps.plugin()))
                .exceptionally(this::logFailure);
    }

    private ProfileEditMenu menu() {
        return new ProfileEditMenu(deps.messages(), deps.services().menus(), this::settings, this::apply);
    }

    private ProfileSettings settings() {
        return deps.configuration().companySettings().profile();
    }

    private Void logFailure(Throwable err) {
        deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
        return null;
    }
}
