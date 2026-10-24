/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.configuration.elements.companysettings.ProfileSettings;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.DetailsResult;
import de.eldoria.companies.events.company.CompanyProfileEditEvent;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationType;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Changes the description, tag and colour of a company.
 * <p>
 * Only members with {@link CompanyPermission#EDIT_PROFILE} may edit, the owner always. Every edit reads the company
 * again, so a menu older than the company's state can not bypass the checks. A saved change fires
 * {@link CompanyProfileEditEvent} and notifies the other members with {@link NotificationType#PROFILE_CHANGED}. Tag
 * uniqueness is left to the database, so two servers can not hand out the same tag.
 */
public final class ProfileService {
    private final Store store;
    private final Supplier<ProfileSettings> settings;
    private final Consumer<Event> events;
    private final Notifier notifier;
    private final Executor mainThread;

    /**
     * @param store      reads and saves the companies
     * @param settings   current limits of the profile fields
     * @param events     fires the edit event
     * @param notifier   notifies the members of a change
     * @param mainThread runs the event and notifications after a save
     */
    public ProfileService(
            Store store,
            Supplier<ProfileSettings> settings,
            Consumer<Event> events,
            Notifier notifier,
            Executor mainThread) {
        this.store = store;
        this.settings = settings;
        this.events = events;
        this.notifier = notifier;
        this.mainThread = mainThread;
    }

    /**
     * Edits the profile of the editor's company.
     *
     * @param editor member making the change
     * @param change the fields to change
     * @return what happened; completes after the event was fired
     */
    public CompletableFuture<Outcome> edit(OfflinePlayer editor, ProfileChange change) {
        return store.profileOf(editor).thenCompose(profile -> edit(editor, profile, change));
    }

    private CompletableFuture<Outcome> edit(
            OfflinePlayer editor, Optional<CompanyProfile> optProfile, ProfileChange change) {
        if (optProfile.isEmpty()) return refuse("error.noMember");
        var profile = optProfile.get();
        if (!mayEdit(profile, editor)) return refuse("error.permission.editProfile");
        CompanyDetails details;
        try {
            details = change.applyTo(profile.details(), settings.get());
        } catch (CommandFailure failure) {
            return CompletableFuture.completedFuture(new Outcome.Refused(failure));
        }
        if (details.equals(profile.details())) {
            return CompletableFuture.completedFuture(new Outcome.Saved(profile, profile));
        }
        return store.save(profile, details)
                .thenApplyAsync(result -> saved(editor, profile, details, result), mainThread);
    }

    private Outcome saved(OfflinePlayer editor, CompanyProfile previous, CompanyDetails details, DetailsResult result) {
        return switch (result) {
            case SAVED -> {
                var current = previous.withDetails(details);
                events.accept(new CompanyProfileEditEvent(previous, current, editor));
                notifyMembers(current, editor);
                yield new Outcome.Saved(previous, current);
            }
            case TAG_TAKEN -> refusal("company.profile.edit.error.tagTaken");
            case UNKNOWN_COMPANY -> refusal("error.unknownCompany");
            case FAILED -> refusal("company.profile.edit.error.failed");
        };
    }

    private void notifyMembers(CompanyProfile company, OfflinePlayer editor) {
        var args = NotificationArgs.create()
                .text(
                        "player_name",
                        Objects.requireNonNullElse(
                                editor.getName(), editor.getUniqueId().toString()));
        for (var member : company.members()) {
            var uuid = member.uuid();
            if (uuid.equals(editor.getUniqueId())) continue;
            notifier.send(uuid, NotificationType.PROFILE_CHANGED, args);
        }
    }

    /**
     * Whether a player may edit the profile of a company.
     *
     * @param company the company
     * @param player  the player
     * @return true for the owner and members with {@link CompanyPermission#EDIT_PROFILE}
     */
    public static boolean mayEdit(CompanyProfile company, OfflinePlayer player) {
        return company.member(player)
                .map(member -> member.hasPermission(CompanyPermission.EDIT_PROFILE))
                .orElse(false);
    }

    private static CompletableFuture<Outcome> refuse(String localeKey) {
        return CompletableFuture.completedFuture(refusal(localeKey));
    }

    private static Outcome refusal(String localeKey) {
        return new Outcome.Refused(CommandFailure.actionBar(localeKey));
    }

    /**
     * The result of an edit.
     */
    public sealed interface Outcome {
        /**
         * The edit was saved, or changed nothing.
         *
         * @param previous the company before the edit
         * @param current  the company after the edit
         */
        record Saved(CompanyProfile previous, CompanyProfile current) implements Outcome {}

        /**
         * The edit was refused and nothing was saved.
         *
         * @param failure why it was refused
         */
        record Refused(CommandFailure failure) implements Outcome {}
    }

    /**
     * Reads the company of a player and saves its details.
     */
    public interface Store {
        /**
         * @param player a player
         * @return the company of the player, empty when they have none
         */
        CompletableFuture<Optional<CompanyProfile>> profileOf(OfflinePlayer player);

        /**
         * @param company the company
         * @param details its new details
         * @return whether they were saved
         */
        CompletableFuture<DetailsResult> save(CompanyProfile company, CompanyDetails details);

        /**
         * @param companyData company repository
         * @return a store backed by the repository
         */
        static Store of(ACompanyData companyData) {
            return new Store() {
                @Override
                public CompletableFuture<Optional<CompanyProfile>> profileOf(OfflinePlayer player) {
                    return companyData.retrievePlayerCompanyProfile(player).asFuture();
                }

                @Override
                public CompletableFuture<DetailsResult> save(CompanyProfile company, CompanyDetails details) {
                    return companyData.submitDetails(company, details).asFuture();
                }
            };
        }
    }

    /**
     * Sends a notification to one player.
     */
    @FunctionalInterface
    public interface Notifier {
        /**
         * @param recipient player to notify
         * @param type      kind of notification
         * @param args      values of the notification
         */
        void send(UUID recipient, NotificationType type, NotificationArgs args);
    }
}
