/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.alerts;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.data.repository.AAlertData;
import de.eldoria.companies.data.wrapper.alert.OrderAlert;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.orders.query.OrderFilter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.OfflinePlayer;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Saves, lists, switches and removes the order alerts of a company.
 * <p>
 * Every member may look at the alerts of their company; saving, switching and removing them needs
 * {@link CompanyPermission#MANAGE_ORDERS}. Every call reads the company again, so a menu older than the company's state
 * cannot bypass the checks. The number of alerts is limited per level; the limit is enforced by the repository in the
 * transaction that saves an alert.
 */
public final class AlertService {
    /**
     * The names an alert may have.
     */
    public static final Pattern NAME = Pattern.compile("[a-zA-Z0-9_-]{1," + OrderFilter.MAX_NAME_LENGTH + "}");

    private final AAlertData alerts;
    private final AlertCompanies companies;
    private final Supplier<CompanySettings> settings;
    private final Clock clock;

    /**
     * @param alerts    alert repository
     * @param companies reads the company of a member
     * @param settings  the current company settings
     * @param clock     clock giving the current time
     */
    public AlertService(AAlertData alerts, AlertCompanies companies, Supplier<CompanySettings> settings, Clock clock) {
        this.alerts = alerts;
        this.companies = companies;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * The alerts of a member's company.
     *
     * @param member member looking at the alerts
     * @return the alerts with the limit of the company
     */
    public CompletableFuture<Outcome<Overview>> overview(OfflinePlayer member) {
        return companies.profileOf(member).thenCompose(found -> {
            if (found.isEmpty()) return refuse("error.noMember");
            var company = found.get();
            return alerts.retrieveByCompany(company.id())
                    .thenApply(list -> new Done<>(new Overview(
                            company, list, settings.get().maxAlerts(company.level()), mayManage(company, member))));
        });
    }

    /**
     * An alert of a member's company.
     *
     * @param member member looking at the alert
     * @param name   name of the alert
     * @return the alert
     */
    public CompletableFuture<Outcome<OrderAlert>> find(OfflinePlayer member, String name) {
        return overview(member).thenApply(outcome -> switch (outcome) {
            case Done<Overview> done ->
                done.value().alert(name).<Outcome<OrderAlert>>map(Done::new).orElseGet(() -> refusal(unknown(name)));
            case Refused<Overview> refused -> new Refused<>(refused.failure());
        });
    }

    /**
     * Saves a search filter as an alert of the member's company.
     *
     * @param member member saving the alert
     * @param name   name of the alert
     * @param filter orders the alert matches
     * @return the name of the saved alert, or why it was refused
     */
    public CompletableFuture<Outcome<String>> add(OfflinePlayer member, String name, OrderFilter filter) {
        if (!NAME.matcher(name).matches()) {
            return refuse(CommandFailure.of(
                    "company.alerts.error.name",
                    Placeholder.unparsed("max", String.valueOf(OrderFilter.MAX_NAME_LENGTH))));
        }
        return managed(member, company -> {
            var limit = settings.get().maxAlerts(company.level());
            if (limit <= 0) return refuse("company.alerts.error.disabled");
            return alerts.submitAlert(company.id(), name, filter, member.getUniqueId(), limit, LocalDateTime.now(clock))
                    .thenApply(result -> switch (result) {
                        case CREATED -> new Done<>(name);
                        case NAME_TAKEN ->
                            refusal(CommandFailure.of(
                                    "company.alerts.error.nameTaken", Placeholder.unparsed("alert_name", name)));
                        case LIMIT_REACHED ->
                            refusal(CommandFailure.of(
                                    "company.alerts.error.limit", Placeholder.unparsed("max", String.valueOf(limit))));
                        case FAILED -> refusal(CommandFailure.of("company.alerts.error.failed"));
                    });
        });
    }

    /**
     * Removes an alert of the member's company.
     *
     * @param member member removing the alert
     * @param name   name of the alert
     * @return the name of the removed alert, or why it was refused
     */
    public CompletableFuture<Outcome<String>> remove(OfflinePlayer member, String name) {
        return managed(member, company -> alerts.submitRemoval(company.id(), name)
                .thenApply(removed -> removed ? new Done<>(name) : refusal(unknown(name))));
    }

    /**
     * Turns an alert of the member's company on or off.
     *
     * @param member  member switching the alert
     * @param name    name of the alert
     * @param enabled whether the alert notifies
     * @return the name of the switched alert, or why it was refused
     */
    public CompletableFuture<Outcome<String>> enable(OfflinePlayer member, String name, boolean enabled) {
        return managed(member, company -> alerts.submitEnabled(company.id(), name, enabled)
                .thenApply(found -> found ? new Done<>(name) : refusal(unknown(name))));
    }

    private <T> CompletableFuture<Outcome<T>> managed(
            OfflinePlayer member, Function<CompanyProfile, CompletableFuture<Outcome<T>>> change) {
        return companies.profileOf(member).thenCompose(found -> {
            if (found.isEmpty()) return refuse("error.noMember");
            if (!mayManage(found.get(), member)) return refuse("company.alerts.error.permission");
            return change.apply(found.get());
        });
    }

    /**
     * Whether a player may change the alerts of a company.
     *
     * @param company the company
     * @param player  the player
     * @return true for members with {@link CompanyPermission#MANAGE_ORDERS}
     */
    public static boolean mayManage(CompanyProfile company, OfflinePlayer player) {
        return company.member(player)
                .map(member -> member.hasPermission(CompanyPermission.MANAGE_ORDERS))
                .orElse(false);
    }

    private static CommandFailure unknown(String name) {
        return CommandFailure.of("company.alerts.error.unknown", Placeholder.unparsed("alert_name", name));
    }

    private static <T> CompletableFuture<Outcome<T>> refuse(String localeKey) {
        return refuse(CommandFailure.of(localeKey));
    }

    private static <T> CompletableFuture<Outcome<T>> refuse(CommandFailure failure) {
        return CompletableFuture.completedFuture(refusal(failure));
    }

    private static <T> Outcome<T> refusal(CommandFailure failure) {
        return new Refused<>(failure);
    }

    /**
     * The result of a call.
     *
     * @param <T> what a successful call returns
     */
    public sealed interface Outcome<T> permits Done, Refused {}

    /**
     * The call succeeded.
     *
     * @param value its result
     * @param <T>   type of the result
     */
    public record Done<T>(T value) implements Outcome<T> {}

    /**
     * The call was refused and changed nothing.
     *
     * @param failure why it was refused
     * @param <T>     type of the result it would have had
     */
    public record Refused<T>(CommandFailure failure) implements Outcome<T> {}

    /**
     * The alerts of a company.
     *
     * @param company   the company
     * @param alerts    its alerts by name
     * @param limit     alerts the company may have
     * @param mayManage whether the viewing member may change them
     */
    public record Overview(CompanyProfile company, List<OrderAlert> alerts, int limit, boolean mayManage) {
        /**
         * @param name name of an alert
         * @return the alert with this name
         */
        public Optional<OrderAlert> alert(String name) {
            return alerts.stream().filter(alert -> alert.name().equals(name)).findFirst();
        }
    }
}
