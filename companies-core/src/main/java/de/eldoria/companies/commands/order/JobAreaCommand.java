/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.AreaNames;
import de.eldoria.companies.commands.suggest.JobProviders;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.StoredArea;
import de.eldoria.companies.jobs.JobServices;
import de.eldoria.companies.jobs.StoredAreas;
import de.eldoria.companies.menus.Values;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.IntArg;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * {@code /order job area}: the areas a player marks and stores for area jobs, without WorldEdit.
 * <ul>
 *     <li>{@code corner <1|2>} marks a corner at the block the player stands in; {@code tool} gives the area tool, whose
 *     left and right clicks on a block mark the corners. The dig, terraform and harvest commands use the marked area
 *     before a WorldEdit selection;</li>
 *     <li>{@code save <name>} stores the marked area, or else the WorldEdit selection; {@code plot <name>} stores the
 *     plot the player stands on and owns;</li>
 *     <li>{@code use <name>} marks a stored area again, {@code show <name>} outlines it, {@code delete <name>} removes
 *     it, and {@code /order job area} lists them.</li>
 * </ul>
 * Stored areas are also offered on the dashboard, where jobs are posted on them.
 */
@Subcommand("area")
@PlayerOnly
public final class JobAreaCommand {
    private static final String AREA = JobCommand.PREFIX + "area.";

    private final CommandDependencies deps;
    private final JobCommand jobs;

    /**
     * @param deps what the commands need
     * @param jobs the job commands, which read and outline areas
     */
    JobAreaCommand(CommandDependencies deps, JobCommand jobs) {
        this.deps = deps;
        this.jobs = jobs;
    }

    /**
     * Lists the player's stored areas.
     *
     * @param sender sender of the command
     */
    @Executes
    public void list(CommandSender sender) {
        var player = Senders.player(sender);
        jobs.run(player, () -> services().areas().areas(player.getUniqueId()), areas -> list(player, areas));
    }

    private void list(Player player, List<StoredArea> areas) {
        if (areas.isEmpty()) {
            deps.messages().sendMessage(player, AREA + "none");
            return;
        }
        deps.messages()
                .sendMessage(
                        player,
                        AREA + "list",
                        Values.number("count", areas.size()),
                        Values.number("max", services().areas().max()));
        for (var stored : areas) {
            var area = stored.area();
            deps.messages()
                    .sendMessage(
                            player,
                            AREA + (stored.node().equals(services().node()) ? "entry" : "entryElsewhere"),
                            Values.text("area", stored.name()),
                            Values.text("world", area.world()),
                            Values.number("width", area.maxX() - area.minX() + 1),
                            Values.number("height", area.maxY() - area.minY() + 1),
                            Values.number("length", area.maxZ() - area.minZ() + 1),
                            Values.text("center", area.center()));
        }
    }

    /**
     * Marks a corner of the player's area at the block they stand in.
     *
     * @param sender sender of the command
     * @param corner 1 or 2
     */
    @Executes("corner")
    public void corner(CommandSender sender, @IntArg(min = 1, max = 2) int corner) {
        var player = Senders.player(sender);
        services().tool().mark(player, corner == 2, player.getLocation().getBlock());
    }

    /**
     * Gives the player the area tool, unless they carry one already.
     *
     * @param sender sender of the command
     */
    @Executes("tool")
    public void tool(CommandSender sender) {
        var player = Senders.player(sender);
        var tool = services().tool();
        if (!tool.carries(player)) {
            player.getInventory().addItem(tool.create(player)).values().forEach(left -> player.getWorld()
                    .dropItem(player.getLocation(), left));
        }
        deps.messages().sendMessage(player, AREA + "tool");
    }

    /**
     * Stores the marked area, or else the area selected with WorldEdit, under a name.
     *
     * @param sender sender of the command
     * @param name   the name to store it under
     */
    @Executes("save")
    public void save(CommandSender sender, @StringArg(StringArgType.STRING) String name) {
        var player = Senders.player(sender);
        var area = services().areas().marked(player.getUniqueId());
        if (area.isEmpty() && services().hooks().selects())
            area = services().hooks().selection(player);
        if (area.isEmpty()) {
            deps.messages().sendError(player, AREA + "error.notMarked");
            return;
        }
        save(player, name, area.get());
    }

    /**
     * Stores the plot the player stands on and owns under a name.
     *
     * @param sender sender of the command
     * @param name   the name to store it under
     */
    @Executes("plot")
    public void plot(CommandSender sender, @StringArg(StringArgType.STRING) String name) {
        var player = Senders.player(sender);
        var plot = services().hooks().ownArea(player);
        if (plot.isEmpty()) {
            deps.messages().sendError(player, JobCommand.PREFIX + "error.noPlot");
            return;
        }
        save(player, name, plot.get());
    }

    private void save(Player player, String name, JobArea area) {
        var owner = player.getUniqueId();
        var node = services().node();
        jobs.run(player, () -> services().areas().save(owner, name, node, area), stored -> {
            switch (stored) {
                case StoredAreas.Stored.Done done -> {
                    JobProviders.areasChanged(owner);
                    deps.messages()
                            .sendMessage(
                                    player,
                                    AREA + "saved",
                                    Values.text("area", done.area().name()),
                                    Values.number("width", area.maxX() - area.minX() + 1),
                                    Values.number("height", area.maxY() - area.minY() + 1),
                                    Values.number("length", area.maxZ() - area.minZ() + 1));
                    jobs.outline(player, area);
                }
                case StoredAreas.Stored.Refused refused -> {
                    if (refused.refusal() == StoredAreas.Refusal.NAME) {
                        deps.messages().sendError(player, AREA + "error.name");
                    } else {
                        deps.messages()
                                .sendError(
                                        player,
                                        AREA + "error.limit",
                                        Values.number("max", services().areas().max()));
                    }
                }
            }
        });
    }

    /**
     * Marks a stored area, so the dig, terraform and harvest commands use it.
     *
     * @param sender sender of the command
     * @param name   the name of the area
     */
    @Executes("use")
    public void use(CommandSender sender, @AreaNames @StringArg(StringArgType.STRING) String name) {
        var player = Senders.player(sender);
        var world = player.getWorld().getName();
        here(player, name, stored -> {
            services().areas().mark(player.getUniqueId(), stored.area());
            deps.messages().sendMessage(player, AREA + "used", Values.text("area", stored.name()));
            if (inWorld(player, world, stored)) jobs.outline(player, stored.area());
        });
    }

    /**
     * Outlines a stored area for a short while.
     *
     * @param sender sender of the command
     * @param name   the name of the area
     */
    @Executes("show")
    public void show(CommandSender sender, @AreaNames @StringArg(StringArgType.STRING) String name) {
        var player = Senders.player(sender);
        var world = player.getWorld().getName();
        here(player, name, stored -> {
            deps.messages()
                    .sendMessage(
                            player,
                            AREA + "shown",
                            Values.text("area", stored.name()),
                            Values.text("world", stored.area().world()),
                            Values.text("center", stored.area().center()));
            if (inWorld(player, world, stored)) jobs.outline(player, stored.area());
        });
    }

    /**
     * @return whether the player is in the area's world, after telling them to go there when not
     */
    private boolean inWorld(Player player, String world, StoredArea stored) {
        if (world.equals(stored.area().world())) return true;
        deps.messages()
                .sendMessage(
                        player,
                        AREA + "otherWorld",
                        Values.text("area", stored.name()),
                        Values.text("world", stored.area().world()));
        return false;
    }

    /**
     * Deletes a stored area.
     *
     * @param sender sender of the command
     * @param name   the name of the area
     */
    @Executes("delete")
    public void delete(CommandSender sender, @AreaNames @StringArg(StringArgType.STRING) String name) {
        var player = Senders.player(sender);
        jobs.run(player, () -> services().areas().delete(player.getUniqueId(), name), deleted -> {
            if (deleted) {
                JobProviders.areasChanged(player.getUniqueId());
                deps.messages().sendMessage(player, AREA + "deleted", Values.text("area", name));
            } else {
                deps.messages().sendError(player, AREA + "error.unknown", Values.text("area", name));
            }
        });
    }

    /**
     * Reads a stored area of the player that lies on this server and hands it on.
     */
    private void here(Player player, String name, Consumer<StoredArea> then) {
        var owner = player.getUniqueId();
        jobs.run(player, () -> services().areas().area(owner, name), (Optional<StoredArea> found) -> {
            if (found.isEmpty()) {
                deps.messages().sendError(player, AREA + "error.unknown", Values.text("area", name));
            } else if (!found.get().node().equals(services().node())) {
                deps.messages().sendError(player, AREA + "error.otherServer", Values.text("area", name));
            } else {
                then.accept(found.get());
            }
        });
    }

    private JobServices services() {
        return deps.services().jobs();
    }
}
