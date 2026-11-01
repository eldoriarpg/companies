/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.components.job.JobState;
import de.eldoria.companies.configuration.elements.jobs.JobSettings;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobExtension;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.data.wrapper.job.JobReview;
import de.eldoria.companies.data.wrapper.job.JobTarget;
import de.eldoria.companies.jobs.Blueprint;
import de.eldoria.companies.jobs.DigCheck;
import de.eldoria.companies.jobs.JobService;
import de.eldoria.companies.jobs.JobServices;
import de.eldoria.companies.jobs.TerraformCheck;
import de.eldoria.companies.jobs.hooks.JobHooks;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.util.Texts;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.DoubleArg;
import net.strokkur.commands.arguments.IntArg;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * {@code /order job}: the player's jobs, work in an area of their land that a company takes like an order.
 * <ul>
 *     <li>{@code /order job} lists the player's jobs;</li>
 *     <li>{@code info <id>} shows a job, and on the server of its area how far the work is;</li>
 *     <li>{@code dig <price> <hours> <title>} posts a dig job on the area selected with WorldEdit;</li>
 *     <li>{@code dig plot <price> <hours> <depth> <title>} posts one on the whole plot the player stands on, from the
 *     block below their feet down {@code depth} blocks;</li>
 *     <li>{@code terraform <price> <hours> <y> <title>} posts a terraform job on the area selected with WorldEdit:
 *     every column solid up to {@code y} and empty above it; {@code terraform surface <block> ...} also asks for the
 *     top layer to be made of {@code block};</li>
 *     <li>{@code harvest <unit price> <budget> <hours> <title>} posts a harvest job on the field selected with
 *     WorldEdit; {@code stock <id>} lists what was harvested, {@code collect <id>} hands it over, {@code end <id>}
 *     ends the harvest early;</li>
 *     <li>{@code schematics} lists the schematics of the plugin's {@code schematics} folder; {@code blueprint <schematic>
 *     <rotation>} outlines one placed where the player stands; {@code build <schematic> <rotation> <price> <hours>
 *     <title>} posts a build job of it there;</li>
 *     <li>{@code contract <price> <hours> <title>} posts a contract: work described in words, at the place the
 *     player stands;</li>
 *     <li>{@code describe <id> <text>} describes a job while it waits for a company;</li>
 *     <li>{@code approve <id>} approves a contract the company says is done, or any job a company works on as it
 *     stands, and pays the company;</li>
 *     <li>{@code reject <id> <reason>} sends a contract the company says is done back to work;</li>
 *     <li>{@code dispute <id> <reason>} opens a dispute an admin settles, {@code withdraw <id>} withdraws it;</li>
 *     <li>{@code cancel <id>} cancels a job nobody works on; the price goes back, the fee is kept.</li>
 * </ul>
 * The owner pays the price and the order fee when posting. The area must lie on the owner's plot or region, or on
 * unowned land in a world where the server allows it.
 */
@Subcommand("job")
@PlayerOnly
@net.strokkur.commands.permission.Permission(Permission.Orders.JOBS)
public final class JobCommand {
    static final String PREFIX = "order.job.";
    private static final int LISTED = 20;
    private static final int MAX_DEPTH = 384;

    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public JobCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Lists the player's jobs.
     *
     * @param sender sender of the command
     */
    @Executes
    public void list(CommandSender sender) {
        var player = Senders.player(sender);
        run(player, () -> jobs().data().ofOwner(player.getUniqueId(), LISTED), found -> {
            if (found.isEmpty()) {
                deps.messages().sendMessage(player, PREFIX + "none");
                return;
            }
            deps.messages()
                    .sendMessage(player, MenuText.create().text("<heading>").locale(PREFIX + "title"));
            for (var job : found) deps.messages().sendMessage(player, entry(player, job));
        });
    }

    /**
     * Shows a job of the player.
     *
     * @param sender sender of the command
     * @param id     id of the job
     */
    @Executes("info")
    public void info(CommandSender sender, @IntArg(min = 0) int id) {
        var player = Senders.player(sender);
        run(player, () -> own(player, id), job -> show(player, job, progress(job)));
    }

    /**
     * Posts a dig job on the area the player selected with WorldEdit.
     *
     * @param sender sender of the command
     * @param price  what the company is paid
     * @param hours  how long a company has to finish
     * @param title  title shown to companies
     */
    @Executes("dig")
    public void dig(
            CommandSender sender,
            @DoubleArg(min = 0, max = JobService.MAX_PRICE) double price,
            @IntArg(min = 1) int hours,
            @StringArg(StringArgType.GREEDY) String title) {
        var player = Senders.player(sender);
        var hooks = jobs().hooks();
        if (!hooks.selects()) {
            deps.messages().sendError(player, PREFIX + "error.noWorldEdit");
            return;
        }
        var area = hooks.selection(player);
        if (area.isEmpty()) {
            deps.messages().sendError(player, PREFIX + "error.noSelection");
            return;
        }
        post(player, JobKind.DIG, area.get(), price, hours, title, JobService.Details.NONE);
    }

    /**
     * Posts a terraform job on the area the player selected with WorldEdit: every column solid up to a height and empty
     * above it.
     *
     * @param sender sender of the command
     * @param price  what the company is paid
     * @param hours  how long a company has to finish
     * @param y      the height of the top solid block of every column
     * @param title  title shown to companies
     */
    @Executes("terraform")
    public void terraform(
            CommandSender sender,
            @DoubleArg(min = 0, max = JobService.MAX_PRICE) double price,
            @IntArg(min = 1) int hours,
            @IntArg int y,
            @StringArg(StringArgType.GREEDY) String title) {
        shape(Senders.player(sender), price, hours, new JobTarget(y, null), title);
    }

    /**
     * Posts a terraform job whose top layer must be made of one block.
     *
     * @param sender  sender of the command
     * @param block   the block of the top layer, such as {@code grass_block}
     * @param price   what the company is paid
     * @param hours   how long a company has to finish
     * @param y       the height of the top solid block of every column
     * @param title   title shown to companies
     */
    @Executes("terraform surface")
    public void terraformSurface(
            CommandSender sender,
            @StringArg(StringArgType.STRING) String block,
            @DoubleArg(min = 0, max = JobService.MAX_PRICE) double price,
            @IntArg(min = 1) int hours,
            @IntArg int y,
            @StringArg(StringArgType.GREEDY) String title) {
        var player = Senders.player(sender);
        var material = TerraformCheck.block(block);
        if (material == null) {
            deps.messages().sendError(player, PREFIX + "error.block", Values.text("block", block));
            return;
        }
        shape(player, price, hours, new JobTarget(y, material.name()), title);
    }

    private void shape(Player player, double price, int hours, JobTarget target, String title) {
        selected(player)
                .ifPresent(area ->
                        post(player, JobKind.TERRAFORM, area, price, hours, title, JobService.Details.shape(target)));
    }

    /**
     * Posts a harvest job on the field the player selected with WorldEdit: the company harvests it for a time and is
     * paid for each item it puts into the stock, out of the budget.
     *
     * @param sender    sender of the command
     * @param unitPrice what the company gets for each item
     * @param budget    what the company may be paid in all
     * @param hours     how long the company harvests once it took the job
     * @param title     title shown to companies
     */
    @Executes("harvest")
    public void harvest(
            CommandSender sender,
            @DoubleArg(min = 0, max = JobService.MAX_PRICE) double unitPrice,
            @DoubleArg(min = 0, max = JobService.MAX_PRICE) double budget,
            @IntArg(min = 1) int hours,
            @StringArg(StringArgType.GREEDY) String title) {
        var player = Senders.player(sender);
        selected(player)
                .ifPresent(area -> post(
                        player, JobKind.HARVEST, area, budget, hours, title, JobService.Details.harvest(unitPrice)));
    }

    /**
     * Lists the schematics build jobs may use.
     *
     * @param sender sender of the command
     */
    @Executes("schematics")
    public void schematics(CommandSender sender) {
        var player = Senders.player(sender);
        run(player, this::schematicNames, names -> {
            if (names.isEmpty()) {
                deps.messages().sendMessage(player, PREFIX + "schematics.none");
                return;
            }
            deps.messages()
                    .sendMessage(player, PREFIX + "schematics.list", Values.text("names", String.join(", ", names)));
        });
    }

    /**
     * Shows the outline a schematic covers when placed where the player stands, turned, for a few seconds.
     *
     * @param sender    sender of the command
     * @param schematic name of the schematic
     * @param rotation  degrees to turn it clockwise: 0, 90, 180 or 270
     */
    @Executes("blueprint")
    public void blueprint(
            CommandSender sender, @StringArg(StringArgType.STRING) String schematic, @IntArg int rotation) {
        var player = Senders.player(sender);
        var at = player.getLocation().getBlock();
        blueprintOf(player, schematic, rotation, blueprint -> {
            var area = blueprint.area(at.getWorld().getName(), at.getX(), at.getY(), at.getZ());
            outline(player, area);
            deps.messages()
                    .sendMessage(
                            player,
                            PREFIX + "blueprint",
                            Values.text("schematic", schematic),
                            Values.number("width", area.maxX() - area.minX() + 1),
                            Values.number("height", area.maxY() - area.minY() + 1),
                            Values.number("length", area.maxZ() - area.minZ() + 1),
                            Values.number("blocks", blueprint.blocks()));
        });
    }

    /**
     * Posts a build job: a schematic placed where the player stands, turned, to be built by a company.
     *
     * @param sender    sender of the command
     * @param schematic name of the schematic
     * @param rotation  degrees to turn it clockwise: 0, 90, 180 or 270
     * @param price     what the company is paid
     * @param hours     how long a company has to finish
     * @param title     title shown to companies
     */
    @Executes("build")
    public void build(
            CommandSender sender,
            @StringArg(StringArgType.STRING) String schematic,
            @IntArg int rotation,
            @DoubleArg(min = 0, max = JobService.MAX_PRICE) double price,
            @IntArg(min = 1) int hours,
            @StringArg(StringArgType.GREEDY) String title) {
        var player = Senders.player(sender);
        var at = player.getLocation().getBlock();
        blueprintOf(player, schematic, rotation, blueprint -> {
            var area = blueprint.area(at.getWorld().getName(), at.getX(), at.getY(), at.getZ());
            post(player, JobKind.BUILD, area, price, hours, title, JobService.Details.build(blueprint));
        });
    }

    /**
     * Reads a schematic off the main thread and hands its blueprint on to the main thread.
     *
     * @param player    the player asking
     * @param schematic name of the schematic
     * @param rotation  degrees to turn it clockwise
     * @param then      gets the blueprint on the main thread
     */
    private void blueprintOf(Player player, String schematic, int rotation, Consumer<Blueprint> then) {
        if (!jobs().hooks().selects()) {
            deps.messages().sendError(player, PREFIX + "error.noWorldEdit");
            return;
        }
        if (rotation % 90 != 0 || rotation < 0 || rotation >= 360) {
            deps.messages().sendError(player, PREFIX + "error.rotation");
            return;
        }
        var maxVolume = deps.configuration().jobSettings().buildMaxVolume();
        run(
                player,
                () -> schematicFile(schematic)
                        .flatMap(file -> jobs().hooks().schematic(file, rotation, maxVolume))
                        .orElseThrow(() -> CommandFailure.of(
                                PREFIX + "error.schematic",
                                Values.text("schematic", schematic),
                                Values.number("max", maxVolume))),
                blueprint -> Threading.mainThread(deps.plugin()).execute(() -> then.accept(blueprint)));
    }

    /**
     * @return the names of the schematics in the plugin's {@code schematics} folder, without their file endings
     */
    private List<String> schematicNames() {
        return schematicFiles().stream()
                .map(file -> stripEnding(String.valueOf(file.getFileName())))
                .sorted()
                .toList();
    }

    /**
     * @param name a schematic name as the player typed it
     * @return the file of the schematic with that name in the plugin's {@code schematics} folder, ignoring case; only
     *     files listed in the folder are found, so no name reaches outside it
     */
    private Optional<Path> schematicFile(String name) {
        return schematicFiles().stream()
                .filter(file -> stripEnding(String.valueOf(file.getFileName())).equalsIgnoreCase(name))
                .findFirst();
    }

    private List<Path> schematicFiles() {
        var folder = deps.plugin().getDataFolder().toPath().resolve("schematics");
        try {
            Files.createDirectories(folder);
            try (var files = Files.list(folder)) {
                return files.filter(Files::isRegularFile).toList();
            }
        } catch (IOException e) {
            deps.plugin().getLogger().log(Level.WARNING, "Could not read the schematics folder " + folder, e);
            return List.of();
        }
    }

    private static String stripEnding(String fileName) {
        var dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /**
     * Draws the edges of an area with particles only the player sees, once a second for ten seconds.
     *
     * @param player the player
     * @param area   the area
     */
    private void outline(Player player, JobArea area) {
        var rounds = new int[] {0};
        deps.plugin()
                .getServer()
                .getScheduler()
                .runTaskTimer(
                        deps.plugin(),
                        task -> {
                            if (!player.isOnline() || rounds[0]++ >= 10) {
                                task.cancel();
                                return;
                            }
                            double minX = area.minX(), minY = area.minY(), minZ = area.minZ();
                            double maxX = area.maxX() + 1, maxY = area.maxY() + 1, maxZ = area.maxZ() + 1;
                            double[][] corners = {{minX, minZ}, {maxX, minZ}, {maxX, maxZ}, {minX, maxZ}};
                            for (var i = 0; i < 4; i++) {
                                var from = corners[i];
                                var to = corners[(i + 1) % 4];
                                for (var y : new double[] {minY, maxY})
                                    edge(player, from[0], y, from[1], to[0], y, to[1]);
                                edge(player, from[0], minY, from[1], from[0], maxY, from[1]);
                            }
                        },
                        0,
                        20);
    }

    private static void edge(Player player, double x1, double y1, double z1, double x2, double y2, double z2) {
        var length = Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1)));
        var steps = (int) Math.min(256, Math.ceil(length * 2));
        for (var i = 0; i <= steps; i++) {
            var t = steps == 0 ? 0 : (double) i / steps;
            player.spawnParticle(
                    Particle.END_ROD, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, z1 + (z2 - z1) * t, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Lists the stock of a harvest job of the player.
     *
     * @param sender sender of the command
     * @param id     id of the job
     */
    @Executes("stock")
    public void stock(CommandSender sender, @IntArg(min = 0) int id) {
        var player = Senders.player(sender);
        run(player, () -> jobs().data().stock(own(player, id).id()), stock -> {
            if (stock.isEmpty()) {
                deps.messages().sendMessage(player, PREFIX + "stock.empty", Values.number("id", id));
                return;
            }
            deps.messages().sendMessage(player, PREFIX + "stock.title", Values.number("id", id));
            stock.forEach((material, amount) -> deps.messages()
                    .sendMessage(
                            player,
                            PREFIX + "stock.entry",
                            Values.text("item", material.toLowerCase(Locale.ROOT)),
                            Values.number("amount", amount)));
            deps.messages()
                    .sendMessage(
                            player,
                            MenuText.create()
                                    .text(
                                            "<act_collect><add>[<i18n:words.collect>]</act_collect>",
                                            Values.click(
                                                    "act_collect", ClickEvent.runCommand("/order job collect " + id))));
        });
    }

    /**
     * Gives the player the stock of their harvest job, as much as their inventory holds; the rest stays. The items are
     * taken from the stock first and handed over on the main thread; what does not fit goes back.
     *
     * @param sender sender of the command
     * @param id     id of the job
     */
    @Executes("collect")
    public void collect(CommandSender sender, @IntArg(min = 0) int id) {
        var player = Senders.player(sender);
        run(
                player,
                () -> {
                    var job = own(player, id);
                    Map<String, Integer> taken = new LinkedHashMap<>();
                    jobs().data().stock(job.id()).forEach((material, amount) -> {
                        if (jobs().data().takeStock(job.id(), material, amount)) taken.put(material, amount);
                    });
                    return taken;
                },
                taken -> {
                    if (taken.isEmpty()) {
                        deps.messages().sendMessage(player, PREFIX + "stock.empty", Values.number("id", id));
                        return;
                    }
                    try {
                        Threading.mainThread(deps.plugin()).execute(() -> handOver(player, id, taken));
                    } catch (RuntimeException e) {
                        putBack(id, taken);
                    }
                });
    }

    /**
     * Gives a player the items taken out of a job's stock, on the main thread. What does not fit, and everything when
     * the player left meanwhile, goes back into the stock.
     *
     * @param player the owner
     * @param id     id of the job
     * @param taken  the items taken out of the stock, as amounts by material name
     */
    private void handOver(Player player, int id, Map<String, Integer> taken) {
        if (!player.isOnline()) {
            putBack(id, taken);
            return;
        }
        Map<String, Integer> left = new LinkedHashMap<>();
        var given = 0;
        for (var entry : taken.entrySet()) {
            var material = Material.matchMaterial(entry.getKey());
            if (material == null) {
                left.put(entry.getKey(), entry.getValue());
                continue;
            }
            var rest = give(player, material, entry.getValue());
            given += entry.getValue() - rest;
            if (rest > 0) left.put(entry.getKey(), rest);
        }
        var kept = left.values().stream().mapToInt(Integer::intValue).sum();
        if (!left.isEmpty()) putBack(id, left);
        deps.messages()
                .sendMessage(
                        player,
                        PREFIX + "collected",
                        Values.number("id", id),
                        Values.number("items", given),
                        Values.number("left", kept));
    }

    /**
     * @param id    id of a job
     * @param items items to put back into its stock, as amounts by material name
     */
    private void putBack(int id, Map<String, Integer> items) {
        CompletableFuture.runAsync(() -> jobs().data().stock(id, items), Threading.VIRTUAL)
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not put " + items + " back into job " + id, err);
                    return null;
                });
    }

    /**
     * @param player the player
     * @param type   an item
     * @param amount how many
     * @return how many did not fit into the player's inventory
     */
    private static int give(Player player, Material type, int amount) {
        var rest = 0;
        var left = amount;
        while (left > 0) {
            var stack = Math.min(left, type.getMaxStackSize());
            left -= stack;
            for (var over :
                    player.getInventory().addItem(new ItemStack(type, stack)).values()) {
                rest += over.getAmount();
            }
        }
        return rest;
    }

    /**
     * Ends a harvest job of the player a company works on before its time runs out.
     *
     * @param sender sender of the command
     * @param id     id of the job
     */
    @Executes("end")
    public void end(CommandSender sender, @IntArg(min = 0) int id) {
        var player = Senders.player(sender);
        run(
                player,
                () -> {
                    jobs().access().flush(id);
                    return new Ended(
                            charged(id), jobs().service().endAsOwner(player.getUniqueId(), player.getName(), id));
                },
                ended -> {
                    if (ended.change() == JobService.Change.DONE) {
                        jobs().access().checkSoon();
                    }
                    answer(player, id, ended.change(), ended.charged() ? "ended" : "endedUncharged");
                });
    }

    /**
     * @param player a player
     * @return the area the player selected with WorldEdit, empty after telling them why there is none
     */
    private Optional<JobArea> selected(Player player) {
        var hooks = jobs().hooks();
        if (!hooks.selects()) {
            deps.messages().sendError(player, PREFIX + "error.noWorldEdit");
            return Optional.empty();
        }
        var area = hooks.selection(player);
        if (area.isEmpty()) deps.messages().sendError(player, PREFIX + "error.noSelection");
        return area;
    }

    /**
     * Posts a dig job on the whole plot the player stands on, from the block below their feet down.
     *
     * @param sender sender of the command
     * @param price  what the company is paid
     * @param hours  how long a company has to finish
     * @param depth  how many layers to dig, starting with the one below the player's feet
     * @param title  title shown to companies
     */
    @Executes("dig plot")
    public void digPlot(
            CommandSender sender,
            @DoubleArg(min = 0, max = JobService.MAX_PRICE) double price,
            @IntArg(min = 1) int hours,
            @IntArg(min = 1, max = MAX_DEPTH) int depth,
            @StringArg(StringArgType.GREEDY) String title) {
        var player = Senders.player(sender);
        var plot = jobs().hooks().ownArea(player);
        if (plot.isEmpty()) {
            deps.messages().sendError(player, PREFIX + "error.noPlot");
            return;
        }
        var top = player.getLocation().getBlockY() - 1;
        var found = plot.get();
        var area = new JobArea(
                found.world(), found.minX(), top - depth + 1, found.minZ(), found.maxX(), top, found.maxZ());
        post(player, JobKind.DIG, area, price, hours, title, JobService.Details.NONE);
    }

    /**
     * Posts a contract at the place the player stands.
     *
     * @param sender sender of the command
     * @param price  what the company is paid
     * @param hours  how long a company has to finish
     * @param title  title shown to companies
     */
    @Executes("contract")
    public void contract(
            CommandSender sender,
            @DoubleArg(min = 0, max = JobService.MAX_PRICE) double price,
            @IntArg(min = 1) int hours,
            @StringArg(StringArgType.GREEDY) String title) {
        var player = Senders.player(sender);
        var at = player.getLocation();
        var area = new JobArea(
                player.getWorld().getName(),
                at.getBlockX(),
                at.getBlockY(),
                at.getBlockZ(),
                at.getBlockX(),
                at.getBlockY(),
                at.getBlockZ());
        var owner = player.getUniqueId();
        var node = jobs().node();
        run(
                player,
                () -> jobs().service()
                        .post(owner, JobKind.CONTRACT, title, price, hours, node, area, JobLand.NONE, null),
                posted -> posted(player, JobKind.CONTRACT, posted));
    }

    /**
     * Describes a job of the player while it waits for a company.
     *
     * @param sender sender of the command
     * @param id     id of the job
     * @param text   the description
     */
    @Executes("describe")
    public void describe(CommandSender sender, @IntArg(min = 0) int id, @StringArg(StringArgType.GREEDY) String text) {
        var player = Senders.player(sender);
        run(
                player,
                () -> jobs().service().describe(player.getUniqueId(), id, text),
                change -> answer(player, id, change, "described"));
    }

    /**
     * Approves a job of the player and pays the company.
     *
     * @param sender sender of the command
     * @param id     id of the job
     */
    @Executes("approve")
    public void approve(CommandSender sender, @IntArg(min = 0) int id) {
        var player = Senders.player(sender);
        run(
                player,
                () -> {
                    jobs().access().flush(id);
                    return jobs().service().approve(player.getUniqueId(), player.getName(), id);
                },
                change -> {
                    if (change == JobService.Change.DONE) jobs().access().checkSoon();
                    answer(player, id, change, "approved");
                });
    }

    /**
     * Sends a contract of the player the company says is done back to work.
     *
     * @param sender sender of the command
     * @param id     id of the job
     * @param reason why the work is not done
     */
    @Executes("reject")
    public void reject(CommandSender sender, @IntArg(min = 0) int id, @StringArg(StringArgType.GREEDY) String reason) {
        var player = Senders.player(sender);
        run(
                player,
                () -> jobs().service().reject(player.getUniqueId(), id, reason),
                change -> answer(player, id, change, "rejected"));
    }

    /**
     * Gives the company holding a job of the player more time.
     *
     * @param sender sender of the command
     * @param id     id of the job
     * @param hours  the hours to add
     */
    @Executes("extend")
    public void extend(CommandSender sender, @IntArg(min = 0) int id, @IntArg(min = 1, max = 8760) int hours) {
        var player = Senders.player(sender);
        run(
                player,
                () -> new Extension(jobs().service().extend(player.getUniqueId(), id, hours), hoursLeft(id)),
                result -> extension(player, id, result, "extended", hours));
    }

    /**
     * Grants the request of the company holding a job of the player for more time.
     *
     * @param sender sender of the command
     * @param id     id of the job
     */
    @Executes("extension grant")
    public void grantExtension(CommandSender sender, @IntArg(min = 0) int id) {
        var player = Senders.player(sender);
        run(
                player,
                () -> new Extension(jobs().service().grantExtension(player.getUniqueId(), id), hoursLeft(id)),
                result -> extension(player, id, result, "extensionGranted", 0));
    }

    /**
     * Refuses the request of the company holding a job of the player for more time.
     *
     * @param sender sender of the command
     * @param id     id of the job
     */
    @Executes("extension refuse")
    public void refuseExtension(CommandSender sender, @IntArg(min = 0) int id) {
        refuseExtension(sender, id, "");
    }

    /**
     * Refuses the request of the company holding a job of the player for more time, saying why.
     *
     * @param sender sender of the command
     * @param id     id of the job
     * @param reason why
     */
    @Executes("extension refuse")
    public void refuseExtension(
            CommandSender sender, @IntArg(min = 0) int id, @StringArg(StringArgType.GREEDY) String reason) {
        var player = Senders.player(sender);
        run(
                player,
                () -> new Extension(jobs().service().refuseExtension(player.getUniqueId(), id, reason), 0),
                result -> extension(player, id, result, "extensionRefused", 0));
    }

    /**
     * What extending a job came to, with the hours it may still get.
     *
     * @param change what came of it
     * @param left   the hours the job may still get
     */
    private record Extension(JobService.Change change, int left) {}

    private int hoursLeft(int id) {
        return jobs().data().job(id).map(job -> jobs().service().hoursLeft(job)).orElse(0);
    }

    private void extension(Player player, int id, Extension result, String done, int hours) {
        var values = new TagResolver[] {
            Values.number("id", id),
            Values.number("hours", hours),
            Values.number("left", result.left()),
            Values.number("max", JobExtension.MAX_REASON)
        };
        var key =
                switch (result.change()) {
                    case DONE -> done;
                    case UNKNOWN, NOT_YOURS -> "error.unknown";
                    case DISABLED -> "error.extensionsOff";
                    case HOURS -> "error.extensionHours";
                    case NO_REQUEST -> "error.noExtensionRequest";
                    case REASON -> "error.extensionReason";
                    default -> "error.extensionClosed";
                };
        if (result.change() == JobService.Change.DONE) {
            deps.messages().sendMessage(player, PREFIX + key, values);
        } else {
            deps.messages().sendError(player, PREFIX + key, values);
        }
    }

    /**
     * Opens a dispute about a job of the player a company holds.
     *
     * @param sender sender of the command
     * @param id     id of the job
     * @param reason why
     */
    @Executes("dispute")
    public void dispute(CommandSender sender, @IntArg(min = 0) int id, @StringArg(StringArgType.GREEDY) String reason) {
        var player = Senders.player(sender);
        run(player, () -> jobs().service().disputeAsOwner(player.getUniqueId(), id, reason), change -> {
            if (change == JobService.Change.DONE) jobs().access().checkSoon();
            answer(player, id, change, "disputed");
        });
    }

    /**
     * Withdraws a dispute the player opened about their job.
     *
     * @param sender sender of the command
     * @param id     id of the job
     */
    @Executes("withdraw")
    public void withdraw(CommandSender sender, @IntArg(min = 0) int id) {
        var player = Senders.player(sender);
        run(player, () -> jobs().service().withdrawAsOwner(player.getUniqueId(), id), change -> {
            if (change == JobService.Change.DONE) jobs().access().checkSoon();
            answer(player, id, change, "withdrawn");
        });
    }

    private void answer(Player player, int id, JobService.Change change, String done) {
        var values = Values.number("id", id);
        var key =
                switch (change) {
                    case DONE -> done;
                    case UNKNOWN -> "error.unknown";
                    case NOT_YOURS -> done.equals("withdrawn") ? "error.notYourDispute" : "error.unknown";
                    case TAKEN -> "error.taken";
                    case DISPUTED -> "error.disputed";
                    case REASON -> "error.reason";
                    case TOO_LONG -> "error.description";
                    case NOT_DONE -> "error.notDone";
                    case HARVEST -> "error.harvest";
                    default -> "error.closed";
                };
        if (change == JobService.Change.DONE) {
            deps.messages().sendMessage(player, PREFIX + key, values);
        } else {
            deps.messages()
                    .sendError(
                            player,
                            PREFIX + key,
                            values,
                            Values.number(
                                    "max",
                                    change == JobService.Change.TOO_LONG ? Job.MAX_DESCRIPTION : JobReview.MAX_REASON));
        }
    }

    /**
     * Cancels a job of the player that nobody works on.
     *
     * @param sender sender of the command
     * @param id     id of the job
     */
    @Executes("cancel")
    public void cancel(CommandSender sender, @IntArg(min = 0) int id) {
        var player = Senders.player(sender);
        run(
                player,
                () -> new Ended(charged(id), jobs().service().cancel(player.getUniqueId(), player.getName(), id)),
                ended -> {
                    var change = ended.change();
                    var values = Values.number("id", id);
                    switch (change) {
                        case DONE -> {
                            deps.messages()
                                    .sendMessage(
                                            player,
                                            PREFIX + (ended.charged() ? "cancelled" : "cancelledUncharged"),
                                            values);
                            jobs().access().checkSoon();
                        }
                        case UNKNOWN, NOT_YOURS -> deps.messages().sendError(player, PREFIX + "error.unknown", values);
                        case TAKEN -> deps.messages().sendError(player, PREFIX + "error.taken", values);
                        default -> deps.messages().sendError(player, PREFIX + "error.closed", values);
                    }
                });
    }

    private void post(
            Player player,
            JobKind kind,
            JobArea area,
            double price,
            int hours,
            String title,
            JobService.Details details) {
        var world = deps.plugin().getServer().getWorld(area.world());
        if (world == null || area.minY() < world.getMinHeight() || area.maxY() >= world.getMaxHeight()) {
            deps.messages()
                    .sendError(
                            player,
                            PREFIX + "error.height",
                            Values.number("min", world == null ? 0 : world.getMinHeight()),
                            Values.number("max", world == null ? 0 : world.getMaxHeight() - 1));
            return;
        }
        var settings = deps.configuration().jobSettings();
        var decision = jobs().hooks().decide(player, area, settings.unownedAllowed(area.world()));
        switch (decision) {
            case JobHooks.Decision.Refused refused -> {
                deps.messages()
                        .sendError(
                                player,
                                PREFIX + "error.foreign",
                                Values.text("land", Objects.requireNonNullElse(refused.ref(), "?")));
                return;
            }
            case JobHooks.Decision.Unowned unowned -> {
                deps.messages().sendError(player, PREFIX + "error.unowned");
                return;
            }
            case JobHooks.Decision.Land land -> {
                var owner = player.getUniqueId();
                var node = jobs().node();
                run(
                        player,
                        () -> jobs().service()
                                .post(owner, kind, title, price, hours, node, area, land.land(), land.ref(), details),
                        posted -> posted(player, kind, posted));
            }
        }
    }

    private void posted(Player player, JobKind kind, JobService.Posted posted) {
        switch (posted) {
            case JobService.Posted.Done done -> {
                var job = done.job();
                var key =
                        switch (job.kind()) {
                            case CONTRACT -> "postedContract";
                            case TERRAFORM -> "postedTerraform";
                            case HARVEST -> "postedHarvest";
                            case BUILD -> "postedBuild";
                            default -> "posted";
                        };
                deps.messages()
                        .sendMessage(
                                player,
                                PREFIX + key,
                                Values.number("id", job.id()),
                                Values.text("job_title", job.title()),
                                Values.money("price", deps.services().economy(), job.price()),
                                Values.money("fee", deps.services().economy(), job.fee()),
                                Values.number("blocks", job.area().volume()));
            }
            case JobService.Posted.Refused refused -> {
                var settings = deps.configuration().jobSettings();
                deps.messages()
                        .sendError(
                                player,
                                PREFIX + "error." + key(refused.refusal()),
                                Values.number("max", maxOf(refused.refusal(), kind, settings)));
            }
        }
    }

    private static Number maxOf(JobService.Refusal refusal, JobKind kind, JobSettings settings) {
        return switch (refusal) {
            case VOLUME -> settings.maxVolume(kind);
            case DESCRIPTION -> Job.MAX_DESCRIPTION;
            case UNIT_PRICE, BLUEPRINT, STRANDED -> 0;
            case CHUNKS -> settings.maxChunks();
            case TOO_MANY -> settings.maxOpenPerOwner();
            case DUE -> settings.maxDueHours();
            case TITLE -> Job.MAX_TITLE;
            default -> 0;
        };
    }

    private boolean charged(int id) {
        return jobs().data().job(id).map(Job::charged).orElse(true);
    }

    /**
     * What an owner's end of a job came to.
     *
     * @param charged whether the job was charged, so money went back
     * @param change  what came of it
     */
    private record Ended(boolean charged, JobService.Change change) {}

    private Job own(Player player, int id) {
        return jobs().data()
                .job(id)
                .filter(job -> job.owner().equals(player.getUniqueId()))
                .orElseThrow(() -> CommandFailure.of(PREFIX + "error.unknown", Values.number("id", id)));
    }

    /**
     * Reads how far a job is on the server of its area, kept for a few seconds.
     *
     * @param job the job
     * @return the progress, empty for a closed job, one of another server or a world not loaded
     */
    private Optional<DigCheck.Progress> progress(Job job) {
        if (job.state() != JobState.TAKEN || !job.kind().area()) return Optional.empty();
        return jobs().progress().of(job, false).join();
    }

    private void show(Player player, Job job, Optional<DigCheck.Progress> progress) {
        var economy = deps.services().economy();
        var messages = deps.messages();
        messages.sendMessage(
                player,
                PREFIX + "info.head",
                Values.number("id", job.id()),
                Values.text("job_title", job.title()),
                Values.component("state", texts(player).locale(stateKey(job.state()))));
        var description = job.description();
        if (description != null) {
            messages.sendMessage(player, PREFIX + "info.description", Values.text("description", description));
        }
        if (job.kind().area()) {
            messages.sendMessage(
                    player,
                    PREFIX + "info.area",
                    Values.text("world", job.area().world()),
                    Values.text("center", job.area().center()),
                    Values.number("blocks", job.area().volume()),
                    Values.component("land", land(player, job)));
        } else if (!job.area().world().isEmpty()) {
            messages.sendMessage(
                    player,
                    PREFIX + "info.place",
                    Values.text("world", job.area().world()),
                    Values.text("center", job.area().center()));
        }
        messages.sendMessage(
                player,
                PREFIX + "info.money",
                Values.money("price", economy, job.price()),
                Values.money("fee", economy, job.fee()));
        var harvest = job.harvest();
        if (harvest != null) {
            messages.sendMessage(
                    player,
                    PREFIX + "info.harvest",
                    Values.money("unit", deps.services().economy(), harvest.unitPrice()),
                    Values.money("paid", deps.services().economy(), harvest.paid()),
                    Values.money("price", deps.services().economy(), job.price()));
        }
        var target = job.target();
        if (target != null) {
            var surface = target.surface();
            if (surface == null) {
                messages.sendMessage(player, PREFIX + "info.target", Values.number("y", target.y()));
            } else {
                messages.sendMessage(
                        player,
                        PREFIX + "info.targetSurface",
                        Values.number("y", target.y()),
                        Values.text("block", surface.toLowerCase(Locale.ROOT)));
            }
        }
        if (job.state() == JobState.OPEN) {
            messages.sendMessage(player, PREFIX + "info.open", Values.text("time", left(job.expires())));
        }
        var due = job.due();
        if (job.state() == JobState.TAKEN && due != null) {
            messages.sendMessage(player, PREFIX + "info.taken", Values.text("time", left(due)));
        }
        var extension = job.extension();
        if (job.state() == JobState.TAKEN && extension.extendedHours() > 0) {
            messages.sendMessage(player, PREFIX + "info.extended", Values.number("hours", extension.extendedHours()));
        }
        var asked = extension.askedHours();
        if (job.state() == JobState.TAKEN && asked != null) {
            messages.sendMessage(
                    player,
                    PREFIX + "info.extensionAsked",
                    Values.number("hours", asked),
                    Values.text("reason", Objects.requireNonNullElse(extension.reason(), "")));
        }
        var review = job.review();
        if (job.state() == JobState.DONE) {
            var approveAt = Objects.requireNonNullElse(review.doneAt(), Times.now())
                    .plusHours(deps.configuration().jobSettings().approveHours());
            messages.sendMessage(player, PREFIX + "info.waiting", Values.text("time", left(approveAt)));
        }
        if (job.state() == JobState.DISPUTED) {
            messages.sendMessage(
                    player,
                    PREFIX + "info.disputed",
                    Values.text("reason", Objects.requireNonNullElse(review.dispute(), "")));
        }
        var settled = review.settled();
        if (job.state() == JobState.SETTLED && settled != null) {
            messages.sendMessage(player, PREFIX + "info.settled", Values.number("percent", settled));
        }
        progress.ifPresent(found -> messages.sendMessage(
                player,
                PREFIX + "info.progress",
                Values.number("percent", found.percent()),
                Values.number("cleared", found.cleared()),
                Values.number("total", found.total())));
        messages.sendMessage(player, buttons(job));
    }

    private void extensionButtons(MenuText buttons, Job job) {
        if (!deps.configuration().jobSettings().extensionsEnabled()) return;
        var id = job.id();
        if (job.extension().asked()) {
            buttons.text(
                            "<act_grant><add>[<i18n:" + PREFIX + "grantButton>]</act_grant> ",
                            Values.click("act_grant", ClickEvent.runCommand("/order job extension grant " + id)))
                    .text(
                            "<act_refuse><remove>[<i18n:" + PREFIX + "refuseButton>]</act_refuse> ",
                            Values.click(
                                    "act_refuse",
                                    ClickEvent.suggestCommand("/order job extension refuse " + id + " ")));
        }
        if (jobs().service().hoursLeft(job) <= 0) return;
        buttons.text(
                "<act_extend><show>[<i18n:" + PREFIX + "extendButton>]</act_extend>",
                Values.click("act_extend", ClickEvent.suggestCommand("/order job extend " + id + " ")));
    }

    private MenuText buttons(Job job) {
        var id = job.id();
        var buttons = MenuText.create();
        switch (job.state()) {
            case OPEN ->
                buttons.text(
                        "<act_cancel><remove>[<i18n:words.cancel>]</act_cancel>",
                        Values.click("act_cancel", ClickEvent.suggestCommand("/order job cancel " + id)));
            case DONE ->
                buttons.text(
                                "<act_approve><add>[<i18n:words.accept>]</act_approve> ",
                                Values.click("act_approve", ClickEvent.suggestCommand("/order job approve " + id)))
                        .text(
                                "<act_reject><remove>[<i18n:words.deny>]</act_reject> ",
                                Values.click("act_reject", ClickEvent.suggestCommand("/order job reject " + id + " ")))
                        .text(
                                "<act_dispute><remove>[<i18n:" + PREFIX + "disputeButton>]</act_dispute>",
                                Values.click(
                                        "act_dispute", ClickEvent.suggestCommand("/order job dispute " + id + " ")));
            case DISPUTED -> {
                buttons.text(
                        "<act_approve><add>[<i18n:" + PREFIX + "approveEarlyButton>]</act_approve> ",
                        Values.click("act_approve", ClickEvent.suggestCommand("/order job approve " + id)));
                if (job.owner().equals(job.review().disputeBy())) {
                    buttons.text(
                            "<act_withdraw><show>[<i18n:" + PREFIX + "withdrawButton>]</act_withdraw>",
                            Values.click("act_withdraw", ClickEvent.suggestCommand("/order job withdraw " + id)));
                }
            }
            case TAKEN -> {
                if (job.kind() == JobKind.HARVEST) {
                    buttons.text(
                                    "<act_stock><show>[<i18n:" + PREFIX + "stockButton>]</act_stock> ",
                                    Values.click("act_stock", ClickEvent.runCommand("/order job stock " + id)))
                            .text(
                                    "<act_end><remove>[<i18n:" + PREFIX + "endButton>]</act_end> ",
                                    Values.click("act_end", ClickEvent.suggestCommand("/order job end " + id)));
                    extensionButtons(buttons, job);
                    return buttons;
                }
                buttons.text(
                                "<act_approve><add>[<i18n:" + PREFIX + "approveEarlyButton>]</act_approve> ",
                                Values.click("act_approve", ClickEvent.suggestCommand("/order job approve " + id)))
                        .text(
                                "<act_dispute><remove>[<i18n:" + PREFIX + "disputeButton>]</act_dispute> ",
                                Values.click(
                                        "act_dispute", ClickEvent.suggestCommand("/order job dispute " + id + " ")));
                extensionButtons(buttons, job);
            }
            default -> {
                if (job.kind() == JobKind.HARVEST) {
                    buttons.text(
                            "<act_stock><show>[<i18n:" + PREFIX + "stockButton>]</act_stock>",
                            Values.click("act_stock", ClickEvent.runCommand("/order job stock " + id)));
                }
            }
        }
        return buttons;
    }

    private MenuText entry(Player player, Job job) {
        var economy = deps.services().economy();
        var id = job.id();
        return MenuText.create()
                .locale(
                        PREFIX + "entry",
                        Values.number("id", id),
                        Values.text("job_title", job.title()),
                        Values.component("state", texts(player).locale(stateKey(job.state()))),
                        Values.money("price", economy, job.price()))
                .text(
                        " <act_info><show>[<i18n:words.info>]</act_info>",
                        Values.click("act_info", ClickEvent.runCommand("/order job info " + id)));
    }

    /**
     * @param state the state of a job
     * @return the locale key naming it
     */
    static String stateKey(JobState state) {
        return "jobs.state." + state.name().toLowerCase(Locale.ROOT);
    }

    private Component land(Player player, Job job) {
        var text = MenuText.create().locale("jobs.land." + job.land().name().toLowerCase(Locale.ROOT));
        var ref = job.landRef();
        if (ref != null) text.text(" <ref>", Values.text("ref", ref));
        return texts(player).of(text);
    }

    private MenuComponents texts(Player player) {
        return new MenuComponents(deps.messages(), player);
    }

    /**
     * @param until a time
     * @return the time from now until then, never below 0
     */
    static String left(@Nullable LocalDateTime until) {
        if (until == null) return Texts.prettyDuration(Duration.ZERO);
        var duration = Duration.between(Times.now(), until);
        return Texts.prettyDuration(duration.isNegative() ? Duration.ZERO : duration);
    }

    private JobServices jobs() {
        return deps.services().jobs();
    }

    private <T> void run(Player player, Supplier<T> query, Consumer<T> answer) {
        deps.requireReady();
        CompletableFuture.supplyAsync(query, Threading.VIRTUAL)
                .thenAccept(answer)
                .exceptionally(err -> {
                    if (err.getCause() instanceof CommandFailure failure) {
                        failure.sendTo(player, deps.messages());
                        return null;
                    }
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not read or change a job", err);
                    deps.messages().sendErrorActionBar(player, PREFIX + "error.failed");
                    return null;
                });
    }

    /**
     * @param refusal why a job was refused
     * @return the refusal's message key below {@code error.}, in camel case
     */
    private static String key(JobService.Refusal refusal) {
        var parts = refusal.name().toLowerCase(Locale.ROOT).split("_");
        var key = new StringBuilder(parts[0]);
        for (var i = 1; i < parts.length; i++) {
            key.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return key.toString();
    }
}
