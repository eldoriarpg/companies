/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.orders.OrderDraftService;
import de.eldoria.eldoutilities.localization.ILocalizer;
import org.bukkit.Bukkit;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Where the suggestion providers read their data from.
 * <p>
 * StrokkCommands calls suggestion providers statically, so the plugin hands in the sources when it enables and takes
 * them back when it disables. Database data is kept in {@link SuggestionCache}s for {@link #LIFETIME}.
 */
public final class SuggestionSources {
    /**
     * How long data read from the database is suggested before it is loaded again.
     */
    public static final Duration LIFETIME = Duration.ofSeconds(30);

    private static final String ALL = "all";
    private static volatile Optional<SuggestionSources> current = Optional.empty();

    private final Configuration configuration;
    private final SuggestionCache<UUID, List<String>> memberNames;
    private final SuggestionCache<String, List<String>> companyNames;
    private final SuggestionCache<UUID, List<OwnOrder>> ownOrders;
    private final AOrderData orderData;
    private final OrderDraftService drafts;
    private final ILocalizer localizer;

    /**
     * @param companyData company repository
     * @param orderData   order repository
     * @param drafts      order drafts of the players
     * @param config      configuration of the plugin
     * @param localizer   localizer of the plugin
     * @param executor    executor loading data from the database
     */
    public SuggestionSources(
            ACompanyData companyData,
            AOrderData orderData,
            OrderDraftService drafts,
            Configuration config,
            ILocalizer localizer,
            Executor executor) {
        this.configuration = config;
        this.orderData = orderData;
        this.drafts = drafts;
        this.localizer = localizer;
        this.ownOrders = new SuggestionCache<>(
                LIFETIME,
                executor,
                player -> OwnOrder.of(orderData.toFullOrders(orderData
                        .retrieveOrdersByPlayer(
                                Bukkit.getOfflinePlayer(player), OrderState.UNCLAIMED, OrderState.DELIVERED)
                        .asFuture()
                        .join())));
        this.memberNames = new SuggestionCache<>(LIFETIME, executor, player -> companyData
                .retrievePlayerCompanyProfile(Bukkit.getOfflinePlayer(player))
                .asFuture()
                .join()
                .map(SuggestionSources::memberNames)
                .orElse(List.of()));
        this.companyNames = new SuggestionCache<>(LIFETIME, executor, ignored -> companyData.getCompanies().stream()
                .map(SimpleCompany::name)
                .toList());
    }

    /**
     * @param sources the sources the providers read from
     */
    public static void use(SuggestionSources sources) {
        current = Optional.of(sources);
    }

    /**
     * Stops suggesting data of the plugin.
     */
    public static void clear() {
        current = Optional.empty();
    }

    /**
     * @return the sources, empty while the plugin is disabled
     */
    public static Optional<SuggestionSources> current() {
        return current;
    }

    /**
     * @return member names of the company of a player, by the player's id
     */
    public SuggestionCache<UUID, List<String>> memberNames() {
        return memberNames;
    }

    /**
     * @return names of all companies, under the key {@link #allCompanies()}
     */
    public SuggestionCache<String, List<String>> companyNames() {
        return companyNames;
    }

    /**
     * @return the orders of a player which are not received yet, by the player's id
     */
    public SuggestionCache<UUID, List<OwnOrder>> ownOrders() {
        return ownOrders;
    }

    /**
     * @return order repository, for the market prices
     */
    public AOrderData orderData() {
        return orderData;
    }

    /**
     * @return order drafts of the players
     */
    public OrderDraftService drafts() {
        return drafts;
    }

    /**
     * @param key locale key
     * @return the localized text
     */
    public String localize(String key) {
        return localizer.localize(key);
    }

    /**
     * @return the key of all companies in {@link #companyNames()}
     */
    public static String allCompanies() {
        return ALL;
    }

    /**
     * @return how many company levels are configured
     */
    public int levelCount() {
        return configuration.companySettings().level().size();
    }

    /**
     * @return the colours a company may choose, as configured
     */
    public List<String> profileColors() {
        return configuration.companySettings().profile().colors();
    }

    /**
     * @return the names of the configured board presets
     */
    public List<String> boardPresets() {
        return List.copyOf(configuration.boardSettings().presets().keySet());
    }

    private static List<String> memberNames(CompanyProfile profile) {
        return profile.members().stream()
                .map(member -> member.player().getName())
                .filter(Objects::nonNull)
                .toList();
    }
}
