/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration;

import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.configuration.elements.systemorders.GuessedSettings;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.integrations.discord.WebhookEvent;
import de.eldoria.companies.menus.MenuMode;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The shared settings the dashboard shows and edits, in the order it shows them: per file, sections of fields with
 * their type. Labels and help texts live in the dashboard, keyed by file and key; this list carries only what the
 * plugin checks and what the form needs to pick an input.
 * <p>
 * A key that is not here is edited in the file only: the levels (they have their own editor), the board presets, the
 * rules of the system orders, which have their own editor, and the templates of the server events.
 * {@link #FILE_ONLY} names them, so a test can tell a setting that was forgotten from one left out on purpose.
 */
public final class SettingsCatalogue {
    /**
     * The settings edited in the file only, as dotted keys per file.
     */
    public static final List<Field> FILE_ONLY = List.of(
            new Field("company.yml", "", "level", Type.TEXT, List.of(), false, false, null, null),
            new Field("config.yml", "", "boards.presets", Type.TEXT, List.of(), false, false, null, null),
            new Field("system-orders.yml", "", "rules", Type.TEXT, List.of(), false, false, null, null),
            new Field("events.yml", "", "templates", Type.TEXT, List.of(), false, false, null, null));

    private static final Set<String> AT_LEAST_ONE = Set.of(
            "generalSettings.historyDays",
            "generalSettings.orderCheckInterval",
            "notifications.pollSeconds",
            "notifications.retentionDays",
            "notifications.pageSize",
            "placeholders.refreshSeconds",
            "placeholders.leaderboardSize",
            "placeholders.offlineCacheSize",
            "boards.refreshSeconds",
            "discord.maxPerMinute",
            "discord.queueSize",
            "dashboard.sessionMaxHours",
            "inviteHours",
            "deliveryHours",
            "joining.maxApplicationLength",
            "profile.tagLength.min",
            "profile.tagLength.max",
            "profile.maxDescriptionLength",
            "treasury.logPageSize",
            "maxItems",
            "maxMaterials",
            "maxUnclaimedHours",
            "market.historyDays",
            "market.minOrders",
            "repeating.maxCopies",
            "repeating.maxDays",
            "repeating.checkMinutes",
            "checkMinutes",
            "solo.maxActive",
            "solo.deliveryHours",
            "guessed.recentDays",
            "guessed.minOrders",
            "guessed.maxItems",
            "guessed.repriceAfterHours",
            "guessed.learning.days",
            "guessed.learning.fastMinutes");

    private static final Set<String> PERCENT =
            Set.of("tax.percent", "fee.percent", "solo.taxPercent", "treasury.maxPayoutShare");

    private static final Map<String, Double> LOWEST = Map.ofEntries(
            Map.entry("placeholders.refreshSeconds", 10.0),
            Map.entry("boards.refreshSeconds", 10.0),
            Map.entry("market.historyDays", 14.0),
            Map.entry("guessed.historyDays", 2.0),
            Map.entry("guessed.margin", 0.05),
            Map.entry("guessed.sizeShare", 0.05),
            Map.entry("guessed.factor", 0.01),
            Map.entry("guessed.learning.minFactor", 0.01),
            Map.entry("guessed.learning.weightFloor", 0.01),
            Map.entry("dashboard.sessionIdleMinutes", 5.0),
            Map.entry("maxMultiplier", 1.0));

    private static final Map<String, Double> HIGHEST = Map.ofEntries(
            Map.entry("notifications.pageSize", 50.0),
            Map.entry("treasury.logPageSize", 50.0),
            Map.entry("discord.maxPerMinute", 60.0),
            Map.entry("discord.queueSize", 1000.0),
            Map.entry("profile.tagLength.max", 5.0),
            Map.entry("dashboard.sessionIdleMinutes", 10080.0),
            Map.entry("dashboard.sessionMaxHours", 720.0),
            Map.entry("guessed.margin", 0.95),
            Map.entry("guessed.jitter", 0.9),
            Map.entry("guessed.share", 0.9),
            Map.entry("guessed.sizeShare", 1.0),
            Map.entry("guessed.learning.weightFloor", 1.0));

    /**
     * Every field, grouped by file and section in the order they are shown.
     */
    public static final List<Field> FIELDS = build();

    private static final String CONFIG = "config.yml";
    private static final String COMPANY = "company.yml";
    private static final String ORDER = "order.yml";
    private static final String USER = "user.yml";
    private static final String SYSTEM_ORDERS = "system-orders.yml";
    private static final String EVENTS = "events.yml";
    private static final String JOBS = "jobs.yml";

    private SettingsCatalogue() {}

    /**
     * @param file a shared file
     * @return its fields, in the order they are shown
     */
    public static List<Field> fields(String file) {
        return FIELDS.stream().filter(field -> field.file().equals(file)).toList();
    }

    /**
     * @param file a shared file
     * @param key  a dotted key
     * @return the field of that key
     */
    public static Optional<Field> field(String file, String key) {
        return FIELDS.stream()
                .filter(field -> field.file().equals(file) && field.key().equals(key))
                .findFirst();
    }

    /**
     * @param file   a shared file
     * @param before its content before
     * @param after  its content after
     * @return the keys of the settings that changed, each once, as the catalogue or {@link #FILE_ONLY} names them; a
     *     section only one side has counts as all of its settings
     */
    public static List<String> changed(String file, JsonNode before, JsonNode after) {
        var leaves = new ArrayList<String>();
        leaves("", before, after, leaves);
        var keys = new LinkedHashSet<String>();
        for (var leaf : leaves) keys.add(owner(file, leaf));
        return List.copyOf(keys);
    }

    private static void leaves(String prefix, JsonNode before, JsonNode after, List<String> found) {
        if (before.isObject() || after.isObject()) {
            var names = new TreeSet<String>();
            before.properties().forEach(entry -> names.add(entry.getKey()));
            after.properties().forEach(entry -> names.add(entry.getKey()));
            for (var name : names) {
                leaves(prefix.isEmpty() ? name : prefix + "." + name, before.path(name), after.path(name), found);
            }
            return;
        }
        if (!before.equals(after)) found.add(prefix);
    }

    private static String owner(String file, String leaf) {
        return Stream.concat(FIELDS.stream(), FILE_ONLY.stream())
                .filter(field -> field.file().equals(file))
                .map(Field::key)
                .filter(key -> leaf.equals(key) || leaf.startsWith(key + "."))
                .findFirst()
                .orElse(leaf);
    }

    private static List<Field> build() {
        var fields = new ArrayList<Field>();
        var general = new Section(fields, CONFIG, "general");
        general.choice("generalSettings.language", "en_US", "de_DE", "fr_FR");
        general.text("generalSettings.timezone");
        general.whole("generalSettings.historyDays");
        general.restart(Type.WHOLE, "generalSettings.orderCheckInterval");
        general.restart(Type.FLAG, "generalSettings.messageBlocking");
        general.flag("generalSettings.updates.notice");
        general.flag("generalSettings.updates.autoUpdate");
        general.flag("generalSettings.updates.askForAutoUpdate");
        general.restart(Type.FLAG, "generalSettings.hooks.placeholderapi");
        general.restart(Type.FLAG, "generalSettings.hooks.citizens");
        general.restart(Type.FLAG, "generalSettings.hooks.fancynpcs");
        general.restart(Type.FLAG, "generalSettings.hooks.worldguard");
        general.restart(Type.FLAG, "generalSettings.hooks.plotsquared");
        general.restart(Type.FLAG, "generalSettings.hooks.worldedit");
        new Section(fields, CONFIG, "economy").text("economy.collectorAccount");
        var appearance = new Section(fields, CONFIG, "appearance");
        appearance.choice("menus", names(MenuMode.values()));
        for (var style : List.of(
                "prefix",
                "heading",
                "message",
                "value",
                "name",
                "neutral",
                "active",
                "inactive",
                "add",
                "modify",
                "remove",
                "show",
                "error")) {
            appearance.text("style." + style);
        }
        var notifications = new Section(fields, CONFIG, "notifications");
        notifications.whole("notifications.pollSeconds");
        notifications.whole("notifications.retentionDays");
        notifications.whole("notifications.pageSize");
        notifications.flag("notifications.actionBar");
        notifications.text("notifications.sound");
        var placeholders = new Section(fields, CONFIG, "placeholders");
        placeholders.whole("placeholders.refreshSeconds");
        placeholders.whole("placeholders.leaderboardSize");
        placeholders.text("placeholders.loadingText");
        placeholders.restart(Type.WHOLE, "placeholders.offlineCacheSize");
        placeholders.whole("top.cacheSeconds");
        var boards = new Section(fields, CONFIG, "boards");
        boards.restart(Type.FLAG, "boards.enabled");
        boards.text("boards.signHeader");
        boards.whole("boards.refreshSeconds");
        var discord = new Section(fields, CONFIG, "discord");
        discord.flag("discord.enabled");
        discord.secret("discord.url");
        discord.text("discord.username");
        discord.text("discord.avatarUrl");
        discord.choices("discord.events", names(WebhookEvent.values()));
        discord.whole("discord.maxPerMinute");
        discord.whole("discord.queueSize");
        var dashboard = new Section(fields, CONFIG, "dashboard");
        dashboard.flag("dashboard.enabled");
        dashboard.text("dashboard.gateway");
        dashboard.whole("dashboard.sessionIdleMinutes");
        dashboard.whole("dashboard.sessionMaxHours");
        dashboard.add(Type.FLAG_MAP, "dashboard.features", List.of(), false);
        dashboard.text("dashboard.branding.name");
        dashboard.text("dashboard.branding.icon");

        var company = new Section(fields, COMPANY, "company");
        company.decimal("foundingPrice");
        company.decimal("renamePrice");
        company.decimal("tax.percent");
        company.whole("inviteHours");
        company.flag("allowMemberOrders");
        company.whole("deliveryHours");
        company.whole("expiryWarningHours");
        company.whole("abortedOrderPenalty");
        company.whole("expiredOrderPenalty");
        var joining = new Section(fields, COMPANY, "joining");
        joining.choice("joining.defaultMode", names(JoinMode.values()));
        joining.whole("joining.maxPendingApplications");
        joining.whole("joining.maxApplicationLength");
        var profile = new Section(fields, COMPANY, "profile");
        profile.whole("profile.tagLength.min");
        profile.whole("profile.tagLength.max");
        profile.add(Type.TEXT_LIST, "profile.blockedTags", List.of(), false);
        profile.add(Type.TEXT_LIST, "profile.colors", List.of(), false);
        profile.whole("profile.maxDescriptionLength");
        var treasury = new Section(fields, COMPANY, "treasury");
        treasury.flag("treasury.enabled");
        treasury.whole("treasury.maxPayoutShare");
        treasury.decimal("treasury.minTransaction");
        treasury.flag("treasury.payRename");
        treasury.whole("treasury.logPageSize");
        treasury.whole("treasury.logRetentionDays");
        var alerts = new Section(fields, COMPANY, "alerts");
        alerts.flag("alerts.enabled");
        alerts.whole("alerts.maxPerCompany");
        alerts.whole("alerts.minMinutesBetween");

        var orders = new Section(fields, ORDER, "orders");
        orders.whole("maxItems");
        orders.whole("maxMaterials");
        orders.whole("maxExtensions");
        orders.whole("maxUnclaimedHours");
        orders.whole("purgeWarningHours");
        orders.whole("savedItems.max");
        var fee = new Section(fields, ORDER, "fee");
        fee.decimal("fee.fixed");
        fee.decimal("fee.percent");
        fee.decimal("fee.min");
        fee.decimal("fee.max");
        var market = new Section(fields, ORDER, "market");
        market.whole("market.historyDays");
        market.whole("market.minOrders");
        var matching = new Section(fields, ORDER, "matching");
        matching.flag("matching.allowDamaged");
        matching.add(Type.TEXT_LIST, "matching.ignoredComponents", List.of(), false);
        var repeating = new Section(fields, ORDER, "repeating");
        repeating.flag("repeating.enabled");
        repeating.whole("repeating.maxSeriesPerPlayer");
        repeating.whole("repeating.maxCopies");
        repeating.whole("repeating.maxDays");
        repeating.restart(Type.WHOLE, "repeating.checkMinutes");
        var solo = new Section(fields, ORDER, "solo");
        solo.flag("solo.enabled");
        solo.flag("solo.forCompanyMembers");
        solo.whole("solo.maxActive");
        solo.whole("solo.maxOrderSize");
        solo.whole("solo.deliveryHours");
        solo.decimal("solo.taxPercent");
        solo.whole("solo.abortCooldownMinutes");

        new Section(fields, USER, "orders").whole("maxOrders");

        var system = new Section(fields, SYSTEM_ORDERS, "systemOrders");
        system.flag("enabled");
        system.restart(Type.WHOLE, "checkMinutes");
        system.decimal("mintedDailyCap");
        system.flag("markSystemOrders");
        var guessed = new Section(fields, SYSTEM_ORDERS, "guessedOrders");
        guessed.flag("guessed.enabled");
        guessed.whole("guessed.historyDays");
        guessed.whole("guessed.recentDays");
        guessed.whole("guessed.minOrders");
        guessed.add(Type.TEXT_LIST, "guessed.allow", List.of(), false);
        guessed.add(Type.TEXT_LIST, "guessed.deny", List.of(), false);
        guessed.choice("guessed.competition", names(GuessedSettings.Competition.values()));
        guessed.decimal("guessed.margin");
        guessed.decimal("guessed.factor");
        guessed.decimal("guessed.maxFactor");
        guessed.decimal("guessed.jitter");
        guessed.decimal("guessed.minPrice");
        guessed.decimal("guessed.maxPrice");
        guessed.decimal("guessed.sizeShare");
        guessed.whole("guessed.maxItems");
        guessed.whole("guessed.repriceAfterHours");
        guessed.decimal("guessed.repriceStep");
        guessed.whole("guessed.maxReprices");
        guessed.whole("guessed.restHours");
        guessed.decimal("guessed.share");
        guessed.whole("guessed.minOpen");
        guessed.whole("guessed.maxOpen");
        guessed.whole("guessed.perCompanyDaily");
        guessed.whole("guessed.perPlayerDaily");
        guessed.choice("guessed.source.type", names(MoneySource.SourceType.values()));
        guessed.text("guessed.source.account");
        guessed.decimal("guessed.source.dailyBudget");
        var learning = new Section(fields, SYSTEM_ORDERS, "guessedLearning");
        learning.flag("guessed.learning.enabled");
        learning.whole("guessed.learning.days");
        learning.whole("guessed.learning.fastMinutes");
        learning.decimal("guessed.learning.step");
        learning.decimal("guessed.learning.minFactor");
        learning.decimal("guessed.learning.weightFloor");

        var events = new Section(fields, EVENTS, "events");
        events.decimal("maxMultiplier");
        events.flag("broadcast");
        events.flag("joinMessage");
        events.whole("createAheadHours");
        events.choice("goalRewardFunding", names(MoneySource.SourceType.values()));
        events.text("goalRewardAccount");
        events.decimal("goalRewardBudget");

        var jobs = new Section(fields, JOBS, "jobs");
        jobs.flag("enabled");
        jobs.add(Type.TEXT_LIST, "unownedWorlds", List.of(), false);
        jobs.whole("digMaxVolume");
        jobs.whole("terraformMaxVolume");
        jobs.whole("maxChunks");
        jobs.whole("maxOpenPerOwner");
        jobs.whole("maxDueHours");
        jobs.whole("openDays");
        jobs.whole("approveHours");
        return List.copyOf(fields);
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    /**
     * How a field is entered.
     */
    public enum Type {
        /** true or false */
        FLAG,
        /** a whole number */
        WHOLE,
        /** a number with decimals */
        DECIMAL,
        /** a line of text */
        TEXT,
        /** one of {@link Field#choices()} */
        CHOICE,
        /** any of {@link Field#choices()} */
        CHOICES,
        /** a list of texts */
        TEXT_LIST,
        /** named switches, a missing one is on */
        FLAG_MAP,
        /** a text that is never shown, only set, replaced or cleared */
        SECRET
    }

    /**
     * One setting.
     *
     * @param file    the shared file
     * @param section the section it is shown in
     * @param key     the dotted key
     * @param type    how it is entered
     * @param choices the values of a {@link Type#CHOICE} or {@link Type#CHOICES}
     * @param restart whether it applies only after a restart
     * @param secret  whether its value is never shown
     * @param min     the smallest number allowed, none unless a number
     * @param max     the largest number allowed, none when unbounded
     */
    public record Field(
            String file,
            String section,
            String key,
            Type type,
            List<String> choices,
            boolean restart,
            boolean secret,
            @Nullable Double min,
            @Nullable Double max) {
        /**
         * @return the key split into the names of the nested sections and the setting
         */
        public List<String> path() {
            return List.of(key.split("\\."));
        }

        /**
         * @param tree a file's content
         * @return this setting's node in it, missing when it is not set
         */
        public JsonNode at(JsonNode tree) {
            var node = tree;
            for (var part : path()) node = node.path(part);
            return node;
        }
    }

    private record Section(List<Field> fields, String file, String name) {
        void add(Type type, String key, List<String> choices, boolean restart) {
            var number = type == Type.WHOLE || type == Type.DECIMAL;
            var min = number ? LOWEST.getOrDefault(key, AT_LEAST_ONE.contains(key) ? 1.0 : 0.0) : null;
            var max = PERCENT.contains(key) ? Double.valueOf(100) : HIGHEST.get(key);
            fields.add(new Field(file, name, key, type, List.copyOf(choices), restart, type == Type.SECRET, min, max));
        }

        void flag(String key) {
            add(Type.FLAG, key, List.of(), false);
        }

        void whole(String key) {
            add(Type.WHOLE, key, List.of(), false);
        }

        void decimal(String key) {
            add(Type.DECIMAL, key, List.of(), false);
        }

        void text(String key) {
            add(Type.TEXT, key, List.of(), false);
        }

        void secret(String key) {
            add(Type.SECRET, key, List.of(), false);
        }

        void choice(String key, String... choices) {
            add(Type.CHOICE, key, List.of(choices), false);
        }

        void choice(String key, List<String> choices) {
            add(Type.CHOICE, key, choices, false);
        }

        void choices(String key, List<String> choices) {
            add(Type.CHOICES, key, choices, false);
        }

        void restart(Type type, String key) {
            add(type, key, List.of(), true);
        }
    }
}
