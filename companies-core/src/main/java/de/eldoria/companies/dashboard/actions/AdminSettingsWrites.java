/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.SettingsCatalogue;
import de.eldoria.companies.configuration.SettingsEditor;
import de.eldoria.companies.configuration.SetupPages;
import de.eldoria.companies.dashboard.gateway.BrandingSource;
import de.eldoria.companies.dashboard.views.Payloads;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.nodes.SettingsChange;
import de.eldoria.companies.util.AdminLog;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import org.jetbrains.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.StringNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The dashboard's view of the shared settings, as {@code /companyadmin settings} shows them in game: a file as its
 * latest revision with every setting of the {@link SettingsCatalogue}, how the servers stand on it, its history and
 * what one revision changed.
 * <p>
 * Values are read from the latest revision in the database, not from the answering node's memory, so every node sends
 * the same values. What belongs to one server is the answering node's and named with its uid: the revision it uses,
 * its unshared edits, its own overrides and whether a secret comes from its environment. A secret is never sent, only
 * whether it is set and where from. Every action needs the general admin permission. The methods block and are called
 * off the main thread.
 */
public final class AdminSettingsWrites {
    /**
     * The actions performed here.
     */
    public static final Set<String> ACTIONS = Set.of(
            Actions.ADMIN_SETTINGS_GET,
            Actions.ADMIN_SETTINGS_HISTORY,
            Actions.ADMIN_SETTINGS_REVISION,
            Actions.ADMIN_SETTINGS_SAVE,
            Actions.ADMIN_SETTINGS_ROLLBACK,
            Actions.ADMIN_SETTINGS_ICON,
            Actions.ADMIN_SETUP_GET,
            Actions.ADMIN_SETUP_MARK);

    /**
     * The largest icon accepted, so its base64 fits the gateway's request limit.
     */
    public static final int MAX_ICON_BYTES = 40 * 1024;

    private static final String ICON_KEY = "dashboard.branding.icon";
    private static final Set<String> CHANGES = Set.of(
            Actions.ADMIN_SETTINGS_SAVE,
            Actions.ADMIN_SETTINGS_ROLLBACK,
            Actions.ADMIN_SETTINGS_ICON,
            Actions.ADMIN_SETUP_MARK);
    private static final String DISMISSED = "dismissed";
    private static final String BANNER = "banner";
    private static final Set<String> STATES = Set.of("done", "skipped", "open");

    /**
     * The revisions of one history page.
     */
    public static final int PAGE_SIZE = 20;

    private static final YAMLMapper YAML = YAMLMapper.builder().build();
    private static final String INVALID = "dashboard.invalid";
    private static final String UNKNOWN_REVISION = "companyadmin.settings.error.unknownRevision";

    private final Sources sources;
    private final Answers answers;

    /**
     * @param sources what the settings are read from
     */
    public AdminSettingsWrites(Sources sources) {
        this.sources = sources;
        answers = new Answers(sources.render());
    }

    /**
     * Performs a read of the shared settings.
     *
     * @param request the action, one of {@link #ACTIONS}
     * @param session the admin's session
     * @param args    the arguments
     * @return the result
     */
    ActionResult perform(ActionRequest request, StoredSession session, JsonNode args) {
        if (!session.permissions().contains(Permission.Admin.ADMIN)
                || (CHANGES.contains(request.name()) && !session.permissions().contains(Permission.Admin.RELOAD))) {
            return answers.refused(request, "companyadmin.error.permission");
        }
        var admin = session.playerName() + "@dashboard";
        try {
            if (request.name().equals(Actions.ADMIN_SETUP_GET)) return answers.data(request, setup());
            if (request.name().equals(Actions.ADMIN_SETUP_MARK)) return mark(request, admin, args);
            var file = file(args);
            return switch (request.name()) {
                case Actions.ADMIN_SETTINGS_GET ->
                    answers.data(request, get(file, session.permissions().contains(Permission.Admin.RELOAD)));
                case Actions.ADMIN_SETTINGS_HISTORY ->
                    answers.data(request, Map.of("revisions", history(file, args), "pageSize", PAGE_SIZE));
                case Actions.ADMIN_SETTINGS_REVISION -> revision(request, file, args);
                case Actions.ADMIN_SETTINGS_SAVE -> save(request, admin, file, args);
                case Actions.ADMIN_SETTINGS_ROLLBACK -> rollback(request, admin, file, args);
                case Actions.ADMIN_SETTINGS_ICON -> icon(request, admin, file, args);
                default -> answers.refused(request, "dashboard.unsupported");
            };
        } catch (CommandFailure failure) {
            return answers.refused(request, failure);
        }
    }

    private ActionResult save(ActionRequest request, String admin, String file, JsonNode args) {
        var values = new LinkedHashMap<String, JsonNode>();
        args.path("values").properties().forEach(entry -> values.put(entry.getKey(), entry.getValue()));
        if (values.isEmpty() || !args.path("base").canConvertToInt()) throw CommandFailure.of(INVALID);
        return saved(request, admin, file, args.path("base").asInt(), values);
    }

    private ActionResult saved(
            ActionRequest request, String admin, String file, int base, Map<String, JsonNode> values) {
        return switch (SettingsEditor.save(
                sources.configuration(), sources.nodeData().get(), file, base, values, admin)) {
            case SettingsEditor.Result.Invalid invalid ->
                answers.data(
                        request,
                        new Payloads.AdminSettingsSaved(
                                "invalid", 0, invalid.fields(), Map.of(), List.of(), List.of(), true));
            case SettingsEditor.Result.Newer newer ->
                answers.data(
                        request,
                        new Payloads.AdminSettingsSaved(
                                "newer", newer.latest(), Map.of(), newer.values(), newer.changed(), List.of(), true));
            case SettingsEditor.Result.Done done ->
                stored(request, done.outcome(), done.restart(), revision -> sources.log()
                        .entry(admin, "settings save")
                        .with("file", file)
                        .with("revision", revision)
                        .with("keys", String.join(",", values.keySet()))
                        .write());
        };
    }

    private ActionResult icon(ActionRequest request, String admin, String file, JsonNode args) {
        if (!file.equals(Configuration.CONFIG_YML.path().toString())
                || !args.path("base").canConvertToInt()) {
            throw CommandFailure.of(INVALID);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(args.path("png").asString(""));
        } catch (IllegalArgumentException e) {
            throw CommandFailure.of(INVALID);
        }
        if (bytes.length > MAX_ICON_BYTES || BrandingSource.problem(bytes).isPresent()) {
            return answers.data(
                    request,
                    new Payloads.AdminSettingsSaved(
                            "invalid", 0, Map.of(ICON_KEY, "icon"), Map.of(), List.of(), List.of(), true));
        }
        var hash = BrandingSource.sha256(bytes);
        var name = "icon-" + hash.substring(0, 16);
        var nodeData = sources.nodeData().get();
        if (!nodeData.storeAsset(name, Base64.getEncoder().encodeToString(bytes), hash, admin)) {
            return answers.refused(request, "companyadmin.settings.error.failed");
        }
        var values = Map.<String, JsonNode>of(ICON_KEY, StringNode.valueOf(BrandingSource.SHARED + name));
        return saved(request, admin, file, args.path("base").asInt(), values);
    }

    private Payloads.AdminSetup setup() {
        var progress = sources.nodeData().get().setupProgress();
        var pages = SetupPages.PAGES.stream()
                .map(page -> new Payloads.AdminSetupPage(
                        page.id(),
                        progress.getOrDefault(page.id(), "open"),
                        page.fields().stream()
                                .map(field -> new Payloads.AdminSetupField(field.file(), field.key()))
                                .toList()))
                .toList();
        return new Payloads.AdminSetup(pages, DISMISSED.equals(progress.get(BANNER)));
    }

    private ActionResult mark(ActionRequest request, String admin, JsonNode args) {
        var page = args.path("page").asString("");
        var state = args.path("state").asString("");
        var banner = page.equals(BANNER) && (state.equals(DISMISSED) || state.equals("open"));
        if (!banner && (SetupPages.page(page).isEmpty() || !STATES.contains(state))) throw CommandFailure.of(INVALID);
        sources.nodeData().get().setupProgress(page, state, admin);
        sources.log()
                .entry(admin, "setup")
                .with("page", page)
                .with("state", state)
                .write();
        return answers.data(request, setup());
    }

    private ActionResult rollback(ActionRequest request, String admin, String file, JsonNode args) {
        var revision = args.path("revision").asInt(0);
        var base = args.path("base").asInt(-1);
        if (revision <= 0 || revision >= base) throw CommandFailure.of(INVALID);
        var outcome = sources.configuration()
                .rollback(sources.nodeData().get(), file, revision, base, admin)
                .orElseThrow(() -> CommandFailure.of(UNKNOWN_REVISION));
        if (outcome instanceof Configuration.StoreOutcome.Newer newer) {
            return answers.data(
                    request,
                    new Payloads.AdminSettingsSaved(
                            "newer", newer.latest(), Map.of(), Map.of(), List.of(), List.of(), true));
        }
        return stored(request, outcome, List.of(), stored -> sources.log()
                .entry(admin, "settings rollback")
                .with("file", file)
                .with("revision", revision)
                .with("new", stored)
                .write());
    }

    private ActionResult stored(
            ActionRequest request, Configuration.StoreOutcome outcome, List<String> restart, IntConsumer log) {
        return switch (outcome) {
            case Configuration.StoreOutcome.Stored stored -> {
                log.accept(stored.revision());
                if (stored.replaced()) apply();
                yield answers.data(
                        request,
                        new Payloads.AdminSettingsSaved(
                                "saved", stored.revision(), Map.of(), Map.of(), List.of(), restart, stored.applied()));
            }
            case Configuration.StoreOutcome.Unchanged unchanged ->
                answers.data(
                        request,
                        new Payloads.AdminSettingsSaved(
                                "unchanged", unchanged.latest(), Map.of(), Map.of(), List.of(), List.of(), true));
            case Configuration.StoreOutcome.Newer newer ->
                answers.data(
                        request,
                        new Payloads.AdminSettingsSaved(
                                "newer", newer.latest(), Map.of(), Map.of(), List.of(), List.of(), true));
        };
    }

    private void apply() {
        try {
            sources.applyChanges().run();
        } catch (RuntimeException e) {
            sources.logger().log(Level.WARNING, "Could not schedule applying the saved settings.", e);
        }
    }

    private Payloads.AdminSettingsFile get(String file, boolean editable) {
        var nodeData = sources.nodeData().get();
        var configuration = sources.configuration();
        var latest = nodeData.latestSettings(file);
        var tree = latest.map(revision -> tree(revision.content()))
                .orElseGet(() -> configuration.currentTree(file).orElse(MissingNode.getInstance()));
        var status = configuration.status(nodeData).stream()
                .filter(entry -> entry.path().equals(file))
                .findFirst()
                .orElseThrow(() -> CommandFailure.of(INVALID));
        var overridden = Configuration.CONFIG_YML.path().toString().equals(file)
                ? configuration.nodeSettings().overrides().sharedKeys()
                : List.<String>of();
        var settings = SettingsCatalogue.fields(file).stream()
                .map(field -> setting(field, tree, overridden.contains(field.key())))
                .toList();
        return new Payloads.AdminSettingsFile(
                file,
                status.latest(),
                nodeData.nodeUid(),
                status.applied(),
                status.edited(),
                status.behind(),
                settings,
                editable);
    }

    private Payloads.AdminSetting setting(SettingsCatalogue.Field field, JsonNode tree, boolean overridden) {
        var value = field.at(tree);
        if (field.secret()) {
            return new Payloads.AdminSetting(
                    field.section(),
                    field.key(),
                    field.type().name(),
                    field.choices(),
                    field.restart(),
                    null,
                    secret(value),
                    overridden,
                    field.min(),
                    field.max());
        }
        return new Payloads.AdminSetting(
                field.section(),
                field.key(),
                field.type().name(),
                field.choices(),
                field.restart(),
                value.isMissingNode() ? null : value,
                null,
                overridden,
                field.min(),
                field.max());
    }

    private Payloads.AdminSecret secret(JsonNode stored) {
        if (sources.configuration().discordSettings().urlFromEnvironment()) {
            return new Payloads.AdminSecret(true, "environment");
        }
        var set = stored.isString() && !stored.asString().isBlank();
        return new Payloads.AdminSecret(set, set ? "file" : "none");
    }

    private List<Payloads.AdminSettingsChange> history(String file, JsonNode args) {
        var page = Math.clamp(args.path("page").asInt(1), 1, Integer.MAX_VALUE / PAGE_SIZE);
        return sources.nodeData().get().settingsHistory(file, (page - 1) * PAGE_SIZE, PAGE_SIZE).stream()
                .map(this::change)
                .toList();
    }

    private Payloads.AdminSettingsChange change(SettingsChange change) {
        return new Payloads.AdminSettingsChange(
                change.revision(),
                change.actor(),
                change.node(),
                sources.instant().apply(change.changed()));
    }

    private ActionResult revision(ActionRequest request, String file, JsonNode args) {
        var number = args.path("revision").asInt(0);
        var nodeData = sources.nodeData().get();
        var stored = nodeData.settingsAt(file, number);
        var change = nodeData.settingsChange(file, number);
        if (stored.isEmpty() || change.isEmpty()) return answers.refused(request, UNKNOWN_REVISION);
        var after = tree(stored.get().content());
        var before = nodeData.settingsAt(file, number - 1).map(previous -> tree(previous.content()));
        var entry = change(change.get());
        return answers.data(
                request,
                new Payloads.AdminSettingsRevision(
                        file,
                        number,
                        entry.actor(),
                        entry.node(),
                        entry.changed(),
                        before.isEmpty(),
                        before.map(tree -> diff(file, tree, after)).orElse(List.of())));
    }

    /**
     * @param file   the file
     * @param before the content before
     * @param after  the content after
     * @return the settings that changed, each once, as the catalogue names them
     */
    static List<Payloads.AdminSettingDiff> diff(String file, JsonNode before, JsonNode after) {
        var diffs = new ArrayList<Payloads.AdminSettingDiff>();
        for (var key : SettingsCatalogue.changed(file, before, after)) {
            var field = SettingsCatalogue.field(file, key);
            if (field.isEmpty()) {
                diffs.add(new Payloads.AdminSettingDiff(key, null, null, false, true));
            } else if (field.get().secret()) {
                diffs.add(new Payloads.AdminSettingDiff(key, null, null, true, false));
            } else {
                diffs.add(new Payloads.AdminSettingDiff(
                        key,
                        present(field.get().at(before)),
                        present(field.get().at(after)),
                        false,
                        false));
            }
        }
        return diffs;
    }

    private static @Nullable JsonNode present(JsonNode node) {
        return node.isMissingNode() ? null : node;
    }

    private static JsonNode tree(String content) {
        try {
            return YAML.readTree(content);
        } catch (JacksonException e) {
            return MissingNode.getInstance();
        }
    }

    private static String file(JsonNode args) {
        var file = args.path("file").asString("").strip();
        return Configuration.SHARED.stream()
                .map(key -> key.path().toString())
                .filter(file::equals)
                .findFirst()
                .orElseThrow(() -> CommandFailure.of(INVALID));
    }

    /**
     * What the settings are read from.
     *
     * @param configuration the configuration of this node
     * @param nodeData      the node repository holding the revisions, once it exists
     * @param applyChanges  applies the settings changed on this node a few seconds later on the main thread
     * @param log           the admin log
     * @param logger        where a failed apply is reported
     * @param instant       turns a stored time into an instant
     * @param render        renders a refusal as plain text
     */
    public record Sources(
            Configuration configuration,
            Supplier<ANodeData> nodeData,
            Runnable applyChanges,
            AdminLog log,
            Logger logger,
            Function<LocalDateTime, Instant> instant,
            Function<CommandFailure, String> render) {}
}
