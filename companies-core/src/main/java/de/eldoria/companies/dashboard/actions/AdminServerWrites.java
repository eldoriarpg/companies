/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.boards.BoardTarget;
import de.eldoria.companies.boards.Boards;
import de.eldoria.companies.boards.npc.NpcBoards;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.configuration.elements.boardbindings.NpcBinding;
import de.eldoria.companies.dashboard.views.Payloads;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.nodes.Node;
import de.eldoria.companies.util.AdminLog;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The dashboard's tools for the servers of a network, as {@code /companyadmin board}, {@code node} and
 * {@code syncconfiguration} offer them: the board presets with their open orders, board signs written again, the NPCs
 * bound to boards, bound and unbound; the nodes in the database, an old one removed; and the configuration synced.
 * <p>
 * Board signs and NPC bindings belong to the node, so the gateway sends those actions to the node the admin picked.
 * Boards need the permission of {@code /companyadmin board}, nodes the one of {@code node}, syncing the general admin
 * permission, as the commands do. An NPC is named as in its plugin: a Citizens id or a FancyNpcs name; {@code selected}
 * is refused, since the console's selection is not the admin's. A node to remove is named by {@code uid}. Syncing is
 * refused on a secondary, which would pull instead of push. The methods block and are called off the main thread.
 */
public final class AdminServerWrites {
    /**
     * The actions performed here.
     */
    public static final Set<String> ACTIONS = Set.of(
            Actions.ADMIN_BOARDS_PRESETS,
            Actions.ADMIN_BOARDS_RELOAD,
            Actions.ADMIN_BOARDS_NPC_LIST,
            Actions.ADMIN_BOARDS_NPC_LINK,
            Actions.ADMIN_BOARDS_NPC_UNLINK,
            Actions.ADMIN_NODES_LIST,
            Actions.ADMIN_NODES_REMOVE,
            Actions.ADMIN_CONFIG_SYNC);

    private static final Map<String, String> PERMISSIONS = Map.of(
            Actions.ADMIN_BOARDS_PRESETS, Permission.Admin.BOARDS,
            Actions.ADMIN_BOARDS_RELOAD, Permission.Admin.BOARDS,
            Actions.ADMIN_BOARDS_NPC_LIST, Permission.Admin.BOARDS,
            Actions.ADMIN_BOARDS_NPC_LINK, Permission.Admin.BOARDS,
            Actions.ADMIN_BOARDS_NPC_UNLINK, Permission.Admin.BOARDS,
            Actions.ADMIN_NODES_LIST, Permission.Admin.NODES,
            Actions.ADMIN_NODES_REMOVE, Permission.Admin.NODES,
            Actions.ADMIN_CONFIG_SYNC, Permission.Admin.ADMIN);

    private static final String INVALID = "dashboard.invalid";
    private static final String SELECTED = "selected";

    private final Sources sources;
    private final Answers answers;

    /**
     * @param sources what boards and nodes are read and changed with
     */
    public AdminServerWrites(Sources sources) {
        this.sources = sources;
        answers = new Answers(sources.render());
    }

    /**
     * Performs an action on the boards, the nodes or the configuration.
     *
     * @param request the action, one of {@link #ACTIONS}
     * @param session the admin's session
     * @param args    the arguments
     * @return the result
     */
    ActionResult perform(ActionRequest request, StoredSession session, JsonNode args) {
        if (!session.permissions().contains(PERMISSIONS.get(request.name()))) {
            return answers.refused(request, "companyadmin.error.permission");
        }
        var admin = session.playerName() + "@dashboard";
        try {
            return switch (request.name()) {
                case Actions.ADMIN_BOARDS_PRESETS -> presets(request);
                case Actions.ADMIN_BOARDS_RELOAD -> reload(request, admin);
                case Actions.ADMIN_BOARDS_NPC_LIST -> npcs(request);
                case Actions.ADMIN_BOARDS_NPC_LINK -> link(request, admin, args);
                case Actions.ADMIN_BOARDS_NPC_UNLINK -> unlink(request, admin, args);
                case Actions.ADMIN_NODES_LIST -> nodes(request);
                case Actions.ADMIN_NODES_REMOVE -> remove(request, admin, args);
                case Actions.ADMIN_CONFIG_SYNC -> sync(request, admin);
                default -> answers.refused(request, "dashboard.unsupported");
            };
        } catch (CommandFailure failure) {
            return answers.refused(request, failure);
        }
    }

    private ActionResult presets(ActionRequest request) {
        var boards = sources.boards();
        var targets = new ArrayList<BoardTarget>();
        for (var name : boards.presetNames()) boards.resolve(name).ifPresent(targets::add);
        var counts = targets.stream()
                .map(target -> boards.openCount(target.filter()))
                .toList();
        CompletableFuture.allOf(counts.toArray(CompletableFuture[]::new)).join();
        var presets = new ArrayList<Payloads.AdminBoardPreset>();
        for (var i = 0; i < targets.size(); i++) {
            var target = targets.get(i);
            presets.add(new Payloads.AdminBoardPreset(
                    target.spec(), target.label(), counts.get(i).join()));
        }
        return answers.data(request, Map.of("presets", presets));
    }

    private ActionResult reload(ActionRequest request, String admin) {
        onMainThread(() -> {
            sources.boards().presetsChanged();
            return null;
        });
        sources.log().entry(admin, "board reload").write();
        return answers.data(request, answers.empty());
    }

    private ActionResult npcs(ActionRequest request) {
        return answers.data(
                request,
                new Payloads.AdminNpcBoards(
                        sources.npcBoards().hookNames(),
                        sources.npcBoards().bindings().stream()
                                .map(AdminServerWrites::binding)
                                .toList()));
    }

    private ActionResult link(ActionRequest request, String admin, JsonNode args) {
        var hook = text(args, "hook");
        var npc = npc(args);
        var board = args.path("board").asString("").strip();
        var binding = onMainThread(() -> sources.npcBoards().link(sources.console(), hook, npc, board));
        sources.log()
                .entry(admin, "board npc link")
                .with("hook", binding.hook())
                .with("npc", binding.npc())
                .with("board", binding.board())
                .write();
        return answers.data(request, binding(binding));
    }

    private ActionResult unlink(ActionRequest request, String admin, JsonNode args) {
        var hook = text(args, "hook");
        var npc = npc(args);
        var binding = onMainThread(() -> sources.npcBoards().unlink(sources.console(), hook, npc))
                .orElseThrow(() -> CommandFailure.of(
                        "boards.error.notLinked",
                        Placeholder.unparsed("hook", hook),
                        Placeholder.unparsed("npc", npc)));
        sources.log()
                .entry(admin, "board npc unlink")
                .with("hook", binding.hook())
                .with("npc", binding.npc())
                .write();
        return answers.data(request, binding(binding));
    }

    private ActionResult nodes(ActionRequest request) {
        var nodeData = sources.nodeData().get();
        var current = nodeData.nodeUid();
        var nodes = nodeData.nodes().stream()
                .map(node -> new Payloads.AdminNode(
                        node.uid(),
                        node.type().name(),
                        node.version(),
                        node.seen().map(this::instant).orElse(null),
                        sources.bus().apply(node),
                        node.uid().equals(current)))
                .toList();
        return answers.data(request, Map.of("nodes", nodes));
    }

    private ActionResult remove(ActionRequest request, String admin, JsonNode args) {
        UUID uid;
        try {
            uid = UUID.fromString(text(args, "uid"));
        } catch (IllegalArgumentException e) {
            return answers.refused(request, INVALID);
        }
        return switch (sources.nodeData().get().removeNode(uid)) {
            case REMOVED -> {
                sources.log().entry(admin, "node remove").with("uid", uid).write();
                yield nodes(request);
            }
            case UNKNOWN -> answers.refused(request, "companyadmin.node.error.unknown");
            case CURRENT -> answers.refused(request, "companyadmin.node.error.current");
        };
    }

    private ActionResult sync(ActionRequest request, String admin) {
        if (sources.nodeData().get().nodeType() != NodeType.PRIMARY) {
            return answers.refused(request, "error.secondarynode");
        }
        try {
            sources.syncConfiguration().accept(admin);
        } catch (RuntimeException e) {
            sources.logger().log(Level.SEVERE, "Could not sync the configuration from the dashboard.", e);
            return answers.refused(request, "companyadmin.syncconfiguration.failed");
        }
        sources.log().entry(admin, "syncconfiguration").write();
        return answers.data(request, answers.empty());
    }

    private Instant instant(LocalDateTime time) {
        return sources.instant().apply(time);
    }

    private static Payloads.AdminNpcBinding binding(NpcBinding binding) {
        return new Payloads.AdminNpcBinding(binding.hook(), binding.npc(), binding.board());
    }

    private static String npc(JsonNode args) {
        var npc = text(args, "npc");
        if (npc.equalsIgnoreCase(SELECTED)) throw CommandFailure.of(INVALID);
        return npc;
    }

    private static String text(JsonNode args, String field) {
        var value = args.path(field).asString("").strip();
        if (value.isEmpty()) throw CommandFailure.of(INVALID);
        return value;
    }

    private <T> T onMainThread(Supplier<T> action) {
        try {
            return CompletableFuture.supplyAsync(action, sources.mainThread()).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof CommandFailure failure) throw failure;
            throw e;
        }
    }

    /**
     * What boards and nodes are read and changed with.
     *
     * @param boards            the order boards of this node
     * @param npcBoards         the NPCs bound to boards on this node
     * @param console           the sender NPC plugins resolve a typed NPC for
     * @param nodeData          the node repository, once it exists
     * @param bus               how a node exchanges messages, as the admin reads it, {@code -} when unknown
     * @param syncConfiguration does what {@code /companyadmin syncconfiguration} does for the named admin; blocks until
     *                          the files are synced and applies them a moment later
     * @param instant           turns a stored time into an instant
     * @param mainThread        runs tasks on the main thread
     * @param log               the admin log
     * @param logger            where a failed sync is reported
     * @param render            renders a refusal as plain text
     */
    public record Sources(
            Boards boards,
            NpcBoards npcBoards,
            CommandSender console,
            Supplier<ANodeData> nodeData,
            Function<Node, String> bus,
            Consumer<String> syncConfiguration,
            Function<LocalDateTime, Instant> instant,
            Executor mainThread,
            AdminLog log,
            Logger logger,
            Function<CommandFailure, String> render) {}
}
