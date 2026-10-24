/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.data.wrapper.nodes.Node;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.nodes.NodeBus;
import de.eldoria.companies.util.Confirmations;
import de.eldoria.companies.util.Permission.Admin;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.localization.ILocalizer;
import net.kyori.adventure.text.event.ClickEvent;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * {@code /companyadmin node}: the servers registered in the database.
 * <p>
 * Every server registers a node with the {@code nodeUid} of its node.yml. A server that gets a new uid leaves its old
 * node behind, and an old primary node keeps the plugin from starting. These commands list the nodes and remove the
 * ones that no longer run. They need only the database, so they work from the console and while the plugin is in its
 * failsafe mode:
 * <ul>
 *     <li>{@code list} shows each node with its role, its version, when it was last seen and how it exchanges
 *     messages with the other nodes: over Redis, in low latency mode through the database, or not at all (database
 *     polling). For this server that is its own bus and its state; for another node it is the bus named in the
 *     heartbeat this server heard lately, and {@code -} when it heard none;</li>
 *     <li>{@code remove <uid>} asks for a confirmation, given within 30 seconds with {@code remove confirm}. The node of
 *     this server is never removed.</li>
 * </ul>
 */
@Subcommand("node")
@Permission(Admin.NODES)
public final class NodeCommand {
    private static final DateTimeFormatter SEEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final UUID CONSOLE = new UUID(0, 0);

    private final CommandDependencies deps;
    private final Confirmations<UUID> removals = new Confirmations<>();

    /**
     * @param deps what the commands need
     */
    public NodeCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Lists the nodes.
     *
     * @param sender sender of the command
     */
    @Executes("list")
    public void list(CommandSender sender) {
        var nodeData = nodeData();
        run(sender, nodeData::nodes, nodes -> {
            var text = MenuText.create().text("<heading>").locale("companyadmin.node.title");
            for (var node : nodes) {
                text.newLine().append(entry(nodeData, node));
            }
            deps.messages().sendMessage(sender, text);
        });
    }

    /**
     * Asks to remove a node.
     *
     * @param sender sender of the command
     * @param uid    uid of the node
     */
    @Executes("remove")
    public void remove(CommandSender sender, String uid) {
        var nodeData = nodeData();
        var parsed = parse(uid);
        if (parsed.equals(nodeData.nodeUid())) throw CommandFailure.of("companyadmin.node.error.current");
        run(sender, nodeData::nodes, nodes -> {
            var node =
                    nodes.stream().filter(entry -> entry.uid().equals(parsed)).findFirst();
            if (node.isEmpty()) {
                deps.messages().sendError(sender, "companyadmin.node.error.unknown", Values.text("uid", uid));
                return;
            }
            removals.request(senderId(sender), parsed);
            deps.messages()
                    .sendMessage(
                            sender,
                            MenuText.create()
                                    .locale(
                                            "companyadmin.node.removeConfirm",
                                            Values.text("uid", parsed.toString()),
                                            Values.text(
                                                    "role", node.get().type().name()),
                                            Values.text("last_seen", lastSeen(node.get()))));
        });
    }

    /**
     * Removes the node the sender was asked about.
     *
     * @param sender sender of the command
     */
    @Executes("remove confirm")
    public void confirm(CommandSender sender) {
        var nodeData = nodeData();
        var uid = removals.confirm(senderId(sender));
        if (uid.isEmpty()) throw CommandFailure.of("error.confirmationExpired");
        run(sender, () -> nodeData.removeNode(uid.get()), removal -> {
            var value = Values.text("uid", uid.get().toString());
            switch (removal) {
                case REMOVED -> {
                    deps.adminLog()
                            .entry(sender, "node remove")
                            .with("uid", uid.get())
                            .write();
                    deps.messages().sendMessage(sender, "companyadmin.node.removed", value);
                }
                case UNKNOWN -> deps.messages().sendError(sender, "companyadmin.node.error.unknown", value);
                case CURRENT -> deps.messages().sendError(sender, "companyadmin.node.error.current");
            }
        });
    }

    private ANodeData nodeData() {
        var nodeData = deps.nodeData();
        if (nodeData == null) throw CommandFailure.of("error.notReady");
        return nodeData;
    }

    private MenuText entry(ANodeData nodeData, Node node) {
        var uid = node.uid().toString();
        var text = MenuText.create()
                .locale(
                        "companyadmin.node.entry",
                        Values.text("uid", uid),
                        Values.text("role", node.type().name()),
                        Values.text("version", node.version()),
                        Values.text("last_seen", lastSeen(node)),
                        Values.text("bus", bus(nodeData, node)));
        if (node.uid().equals(nodeData.nodeUid())) {
            return text.space().text("<neutral>").locale("companyadmin.node.current");
        }
        return text.text(
                " <act_remove><remove>[<i18n:words.remove>]</act_remove>",
                Values.click("act_remove", ClickEvent.suggestCommand("/companyadmin node remove " + uid)));
    }

    private String bus(ANodeData nodeData, Node node) {
        var bus = deps.bus();
        if (node.uid().equals(nodeData.nodeUid())) return localize("companyadmin.node.bus." + key(bus));
        if (!bus.realtime()) return "-";
        return deps.presence()
                .transport(node.uid())
                .map(transport -> localize("companyadmin.node.bus." + transport.id() + ".connected"))
                .orElse("-");
    }

    /**
     * The locale key of the bus of this node, below {@code companyadmin.node.bus}.
     *
     * @param bus the bus of this node
     * @return the key, for example {@code lowLatency.connected}
     */
    static String key(NodeBus bus) {
        if (bus.state() == NodeBus.State.OFF) return NodeBus.Transport.DATABASE.id();
        return bus.transport().id() + (bus.realtime() ? ".connected" : ".disconnected");
    }

    private String localize(String key) {
        return ILocalizer.getPluginLocalizer(deps.plugin()).localize(key);
    }

    private static String lastSeen(Node node) {
        return node.seen().map(SEEN::format).orElse("-");
    }

    private static UUID parse(String uid) {
        try {
            return UUID.fromString(uid);
        } catch (IllegalArgumentException e) {
            throw CommandFailure.of("companyadmin.node.error.invalidUid", Values.text("uid", uid));
        }
    }

    private static UUID senderId(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : CONSOLE;
    }

    private <T> void run(CommandSender sender, Supplier<T> query, Consumer<T> answer) {
        CompletableFuture.supplyAsync(query, Threading.VIRTUAL)
                .thenAccept(answer)
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not read or change the nodes", err);
                    deps.messages().sendError(sender, "companyadmin.node.error.failed");
                    return null;
                });
    }
}
