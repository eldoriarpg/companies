/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import com.google.common.cache.CacheBuilder;
import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.SurfaceBlocks;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.components.job.JobState;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobExtension;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.data.wrapper.job.JobReview;
import de.eldoria.companies.data.wrapper.job.JobReviewEntry;
import de.eldoria.companies.data.wrapper.job.JobTarget;
import de.eldoria.companies.jobs.AreaChecks;
import de.eldoria.companies.jobs.Blueprint;
import de.eldoria.companies.jobs.BlueprintItems;
import de.eldoria.companies.jobs.BuildCheck;
import de.eldoria.companies.jobs.DigCheck;
import de.eldoria.companies.jobs.HandIns;
import de.eldoria.companies.jobs.JobService;
import de.eldoria.companies.jobs.JobServices;
import de.eldoria.companies.jobs.MaterialCosts;
import de.eldoria.companies.jobs.StockReturns;
import de.eldoria.companies.jobs.TerraformCheck;
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuMode;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.menus.job.JobListMenu;
import de.eldoria.companies.menus.job.JobMenu;
import de.eldoria.companies.menus.job.JobMenus;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.util.Texts;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.DoubleArg;
import net.strokkur.commands.arguments.IntArg;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * {@code /order job}: the player's jobs, work in an area of their land that a company takes like an order.
 * <ul>
 *     <li>{@code /order job} lists the player's jobs;</li>
 *     <li>{@code info <id>} shows a job, and on the server of its area how far the work is;</li>
 *     <li>{@code dig <price> <hours> <title>} posts a dig job on the area the player marked with
 *     {@code area} or selected with WorldEdit;</li>
 *     <li>{@code dig plot <price> <hours> <depth> <title>} posts one on the whole plot the player stands on, from the
 *     block below their feet down {@code depth} blocks;</li>
 *     <li>{@code terraform plot [surface <block>] <price> <hours> <y> <title>} posts a terraform job on the whole plot,
 *     dug out down to {@code y} when that lies below the ground; {@code harvest plot <unit price> <budget> <hours>
 *     <height> <title>} posts a harvest job on it from the block below the player's feet up;</li>
 *     <li>{@code terraform <price> <hours> <y> <title>} posts a terraform job on the marked or selected area:
 *     every column solid up to {@code y} and empty above it; {@code terraform surface <block> ...} also asks for the
 *     top layer to be made of {@code block};</li>
 *     <li>{@code harvest <unit price> <budget> <hours> <title>} posts a harvest job on the marked or selected
 *     field; {@code stock <id>} lists what was harvested, {@code collect <id>} hands it over, {@code end <id>}
 *     ends the harvest early;</li>
 *     <li>{@code schematics} lists the schematics of the plugin's {@code schematics} folder; {@code blueprint <schematic>
 *     <rotation>} previews one placed where the player stands, with what its blocks cost; {@code build <schematic>
 *     <rotation> <price> <hours> <title>} previews it there and asks to confirm, warning when the price is below the
 *     cost of its blocks, and {@code build confirm} or the confirm button posts the build job;</li>
 *     <li>{@code contract <price> <hours> <title>} posts a contract: work described in words, at the place the
 *     player stands;</li>
 *     <li>{@code describe <id> <text>} describes a job while it waits for a company;</li>
 *     <li>{@code approve <id>} approves a contract the company says is done, or any job a company works on as it
 *     stands, and pays the company;</li>
 *     <li>{@code reject <id> <reason>} sends a contract the company says is done back to work;</li>
 *     <li>{@code dispute <id> <reason>} opens a dispute an admin settles, {@code withdraw <id>} withdraws it;</li>
 *     <li>{@code cancel <id>} cancels a job nobody works on; the price goes back, the fee is kept;</li>
 *     <li>{@code area ...} marks and stores areas, see {@link JobAreaCommand}.</li>
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
    private static final long LOOK_TICKS = 60;
    private static final long ASKED_SECONDS = 60;

    private final CommandDependencies deps;
    private final ConfirmFlow<PendingBuild> builds;
    private final JobListMenu listMenu;
    private final HandIns handIns;
    private final JobMenu jobMenu;

    @Subcommand
    public final JobAreaCommand area;

    private final Map<UUID, PendingBuild> asked = CacheBuilder.newBuilder()
            .expireAfterWrite(ASKED_SECONDS, TimeUnit.SECONDS)
            .<UUID, PendingBuild>build()
            .asMap();

    /**
     * @param deps what the commands need
     */
    public JobCommand(CommandDependencies deps) {
        this.deps = deps;
        this.area = new JobAreaCommand(deps, this);
        this.builds = ConfirmFlow.create(deps.plugin(), this::buildConfirmed);
        this.listMenu = new JobListMenu(deps.messages());
        this.handIns = new HandIns(
                deps.plugin(),
                () -> jobs().supplies(),
                () -> deps.services().itemReturnService(),
                deps.messages(),
                this::handed);
        deps.plugin().getServer().getPluginManager().registerEvents(handIns, deps.plugin());
        this.jobMenu = new JobMenu(
                deps.messages(),
                () -> deps.services().menus(),
                ConfirmFlow.create(deps.plugin(), Player::performCommand));
    }

    /**
     * Lists the player's jobs.
     *
     * @param sender sender of the command
     */
    @Executes
    public void list(CommandSender sender) {
        var player = Senders.player(sender);
        run(player, () -> jobs().data().ofOwner(player.getUniqueId(), LISTED), found -> deps.services()
                .menus()
                .show(
                        player,
                        listMenu,
                        new JobListMenu.Model(
                                MenuText.create().locale(PREFIX + "title"),
                                MenuText.create().locale(PREFIX + "none"),
                                found.stream()
                                        .map(job -> new JobListMenu.Entry(
                                                JobMenus.name(job), entry(player, job), "order job info " + job.id()))
                                        .toList())));
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
        run(
                player,
                () -> {
                    var job = own(player, id);
                    return new Info(job, cost(job), jobs().data().reviews(job.id()));
                },
                info -> show(player, info.job(), progress(info.job()), info.cost(), info.reviews()));
    }

    /**
     * Posts a dig job on the area the player marked or selected with WorldEdit.
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
        selected(player)
                .ifPresent(area -> post(player, JobKind.DIG, area, price, hours, title, JobService.Details.NONE));
    }

    /**
     * Posts a terraform job on the area the player marked or selected with WorldEdit: every column solid up to a
     * height and empty above it.
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
            @SurfaceBlocks @StringArg(StringArgType.STRING) String block,
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
     * Posts a harvest job on the field the player marked or selected with WorldEdit: the company harvests it for a
     * time and is paid for each item it puts into the stock, out of the budget.
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
     * Lists the schematics build jobs may use: the server's and the ones the player uploaded.
     *
     * @param sender sender of the command
     */
    @Executes("schematics")
    public void schematics(CommandSender sender) {
        var player = Senders.player(sender);
        run(player, () -> jobs().schematics().names(player.getUniqueId()), found -> {
            if (found.server().isEmpty() && found.own().isEmpty()) {
                deps.messages().sendMessage(player, PREFIX + "schematics.none");
                return;
            }
            if (!found.server().isEmpty()) {
                deps.messages()
                        .sendMessage(
                                player,
                                PREFIX + "schematics.list",
                                Values.text("names", String.join(", ", found.server())));
            }
            if (!found.own().isEmpty()) {
                deps.messages()
                        .sendMessage(
                                player,
                                PREFIX + "schematics.own",
                                Values.text("names", String.join(", ", found.own())));
            }
        });
    }

    /**
     * Previews a schematic placed where the player stands, turned: its outline and its blocks for a few seconds, and
     * what its blocks cost.
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
        blueprintOf(player, schematic, rotation, (read, cost) -> {
            var blueprint = placed(player, schematic, read, at);
            if (blueprint == null) return;
            var area = blueprint.area(at.getWorld().getName(), at.getX(), at.getY(), at.getZ());
            outline(player, area);
            preview(player, area, blueprint);
            deps.messages()
                    .sendMessage(
                            player,
                            PREFIX + "blueprint",
                            Values.text("schematic", schematic),
                            Values.number("width", area.maxX() - area.minX() + 1),
                            Values.number("height", area.maxY() - area.minY() + 1),
                            Values.number("length", area.maxZ() - area.minZ() + 1),
                            Values.number("blocks", blueprint.blocks()));
            if (blueprint.digs() > 0) {
                deps.messages().sendMessage(player, PREFIX + "blueprintDigs", Values.number("digs", blueprint.digs()));
            }
            sendCost(player, cost);
        });
    }

    /**
     * Asks to post a build job: a schematic placed where the player stands, turned, to be built by a company. The
     * blueprint is previewed there and the player confirms, warned when the price is below what its blocks cost; a
     * dialog covering the screen is opened only after a few seconds, so the preview can be looked at first. Only the
     * latest build the player asked for can be confirmed.
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
        blueprintOf(player, schematic, rotation, (read, cost) -> {
            var blueprint = placed(player, schematic, read, at);
            if (blueprint == null) return;
            var area = blueprint.area(at.getWorld().getName(), at.getX(), at.getY(), at.getZ());
            outline(player, area);
            preview(player, area, blueprint);
            sendUnmakeable(player, blueprint);
            var economy = deps.services().economy();
            var question = MenuText.create()
                    .locale(
                            PREFIX + (cost.above(price) ? "confirmBuildBelow" : "confirmBuild"),
                            Values.text("job_title", title),
                            Values.money("price", economy, price),
                            Values.money("cost", economy, cost.total()),
                            Values.number("unpriced", cost.unpriced().size()));
            var pending = new PendingBuild(area, price, hours, title, blueprint);
            asked.put(player.getUniqueId(), pending);
            var menus = deps.services().menus();
            var look = menus.mode(player, true) == MenuMode.DIALOG ? LOOK_TICKS : 0;
            deps.plugin()
                    .getServer()
                    .getScheduler()
                    .runTaskLater(
                            deps.plugin(),
                            () -> {
                                if (player.isOnline()) builds.ask(menus, player, pending, question);
                            },
                            look);
        });
    }

    /**
     * @param player    the player placing it, told when it asks for nothing there
     * @param schematic name of the schematic
     * @param blueprint a blueprint as read from its schematic
     * @param at        the block the player stands on, where it is placed
     * @return the blueprint placed there, with the ground it reaches into to be dug out; null when it asks for nothing
     *     there
     */
    private @Nullable Blueprint placed(Player player, String schematic, Blueprint blueprint, Block at) {
        var world = at.getWorld();
        try {
            return blueprint.dug(
                    world.getName(),
                    at.getX(),
                    at.getY(),
                    at.getZ(),
                    BuildCheck.of(world),
                    (x, z) -> world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES));
        } catch (IllegalArgumentException e) {
            deps.messages()
                    .sendError(
                            player,
                            PREFIX + "error.schematic",
                            Values.text("schematic", schematic),
                            Values.number(
                                    "max", deps.configuration().jobSettings().buildMaxVolume()));
            return null;
        }
    }

    /**
     * Posts the build job the sender was asked to confirm.
     *
     * @param sender sender of the command
     */
    @Executes("build confirm")
    public void confirmBuild(CommandSender sender) {
        builds.confirm(Senders.player(sender));
    }

    private void buildConfirmed(Player player, PendingBuild pending) {
        if (!asked.remove(player.getUniqueId(), pending)) {
            deps.messages().sendErrorActionBar(player, "error.confirmationExpired");
            return;
        }
        jobs().ghosts().hidePreview(player.getUniqueId());
        post(
                player,
                JobKind.BUILD,
                pending.area(),
                pending.price(),
                pending.hours(),
                pending.title(),
                JobService.Details.build(pending.blueprint()));
    }

    /**
     * Saves the build job the sender was asked to confirm as a draft instead of posting it.
     *
     * @param sender sender of the command
     */
    @Executes("build draft")
    public void draftBuild(CommandSender sender) {
        var player = Senders.player(sender);
        var pending = asked.remove(player.getUniqueId());
        if (pending == null) {
            deps.messages().sendErrorActionBar(player, "error.confirmationExpired");
            return;
        }
        jobs().ghosts().hidePreview(player.getUniqueId());
        var owner = player.getUniqueId();
        var node = jobs().node();
        run(
                player,
                () -> jobs().service()
                        .saveDraft(
                                owner,
                                JobKind.BUILD,
                                pending.title(),
                                pending.price(),
                                pending.hours(),
                                node,
                                pending.area(),
                                JobService.Details.build(pending.blueprint())),
                drafted -> drafted(player, JobKind.BUILD, drafted));
    }

    /**
     * Saves the build job the sender was asked to confirm as a draft whose materials the sender supplies, and opens
     * its hand-in.
     *
     * @param sender sender of the command
     */
    @Executes("build supplied")
    public void supplyBuild(CommandSender sender) {
        var player = Senders.player(sender);
        var pending = asked.remove(player.getUniqueId());
        if (pending == null) {
            deps.messages().sendErrorActionBar(player, "error.confirmationExpired");
            return;
        }
        jobs().ghosts().hidePreview(player.getUniqueId());
        var owner = player.getUniqueId();
        var node = jobs().node();
        run(
                player,
                () -> jobs().service()
                        .saveDraft(
                                owner,
                                JobKind.BUILD,
                                pending.title(),
                                pending.price(),
                                pending.hours(),
                                node,
                                pending.area(),
                                JobService.Details.build(pending.blueprint()),
                                true),
                drafted -> {
                    drafted(player, JobKind.BUILD, drafted);
                    if (drafted instanceof JobService.Posted.Done done) {
                        Threading.mainThread(deps.plugin())
                                .execute(() -> handIns.open(player, done.job().id()));
                    }
                });
    }

    /**
     * Opens the hand-in of one of the sender's drafts whose materials they supply.
     *
     * @param sender sender of the command
     * @param id     id of the draft
     */
    @Executes("draft")
    public void supply(CommandSender sender, @IntArg(min = 1) int id) {
        var player = Senders.player(sender);
        var owner = player.getUniqueId();
        run(player, () -> draftOf(owner, id), draft -> {
            if (draft.isEmpty()) {
                deps.messages().sendError(player, PREFIX + "error.noDraft");
                return;
            }
            if (!draft.get().supplied()) {
                deps.messages().sendError(player, PREFIX + "error.notSupplied", Values.number("id", id));
                return;
            }
            Threading.mainThread(deps.plugin()).execute(() -> handIns.open(player, id));
        });
    }

    private void handed(Player player, int job, @Nullable Map<Material, Integer> missing) {
        if (missing == null) {
            deps.messages().sendMessage(player, PREFIX + "handIn.closed", Values.number("id", job));
            return;
        }
        if (missing.isEmpty()) {
            deps.messages().sendMessage(player, PREFIX + "handIn.complete");
            return;
        }
        var total = missing.values().stream().mapToInt(Integer::intValue).sum();
        deps.messages()
                .sendMessage(
                        player,
                        PREFIX + "handIn.left",
                        Values.number("types", missing.size()),
                        Values.number("amount", total));
    }

    /**
     * Posts one of the sender's drafts: its land is checked again where it lies now, and price and fee are charged.
     *
     * @param sender sender of the command
     * @param id     id of the draft
     */
    @Executes("post")
    public void postDraft(CommandSender sender, @IntArg(min = 1) int id) {
        var player = Senders.player(sender);
        var owner = player.getUniqueId();
        run(player, () -> draftOf(owner, id), draft -> {
            if (draft.isEmpty()) {
                deps.messages().sendError(player, PREFIX + "error.noDraft");
                return;
            }
            var job = draft.get();
            if (!job.kind().area()) {
                run(
                        player,
                        () -> jobs().service().publish(owner, id, JobLand.NONE, null),
                        posted -> posted(player, job.kind(), posted));
                return;
            }
            if (!job.node().equals(jobs().node())) {
                deps.messages().sendError(player, PREFIX + "error.otherServer", Values.number("id", id));
                return;
            }
            Threading.mainThread(deps.plugin()).execute(() -> {
                if (!player.isOnline()) return;
                checked(
                        player,
                        job.area(),
                        land -> run(
                                player,
                                () -> jobs().service().publish(owner, id, land.land(), land.ref()),
                                posted -> posted(player, job.kind(), posted)));
            });
        });
    }

    /**
     * Deletes one of the sender's drafts; its stock goes to the sender's item returns.
     *
     * @param sender sender of the command
     * @param id     id of the draft
     */
    @Executes("draft delete")
    public void deleteDraft(CommandSender sender, @IntArg(min = 1) int id) {
        var player = Senders.player(sender);
        var owner = player.getUniqueId();
        var returns = StockReturns.of(
                deps.services().itemReturnService(), deps.plugin().getLogger());
        run(
                player,
                () -> draftOf(owner, id).map(draft -> jobs().service().deleteDraft(owner, id, returns)),
                deleted -> {
                    if (deleted.isEmpty()) {
                        deps.messages().sendError(player, PREFIX + "error.noDraft");
                    } else if (deleted.get()) {
                        deps.messages().sendMessage(player, PREFIX + "draftDeleted", Values.number("id", id));
                    } else {
                        deps.messages().sendError(player, PREFIX + "error.draftKept", Values.number("id", id));
                    }
                });
    }

    private Optional<Job> draftOf(UUID owner, int id) {
        return jobs().data().job(id).filter(job -> job.owner().equals(owner) && job.state() == JobState.DRAFT);
    }

    private void drafted(Player player, JobKind kind, JobService.Posted drafted) {
        switch (drafted) {
            case JobService.Posted.Done done ->
                deps.messages()
                        .sendMessage(
                                player,
                                PREFIX + "drafted",
                                Values.number("id", done.job().id()),
                                Values.text("job_title", done.job().title()));
            case JobService.Posted.Refused refused -> posted(player, kind, refused);
        }
    }

    /**
     * Shows a player the blocks of a blueprint where it would stand, or how long to wait for the next preview.
     *
     * @param player    the player
     * @param area      the area the blueprint covers
     * @param blueprint the blueprint
     */
    private void preview(Player player, JobArea area, Blueprint blueprint) {
        var ghosts = jobs().ghosts();
        if (ghosts.previewing(player, area, blueprint)) return;
        var wait = ghosts.cooldown(player);
        if (wait.isPresent()) {
            deps.messages()
                    .sendErrorActionBar(
                            player, PREFIX + "error.previewCooldown", Values.number("seconds", wait.getAsLong()));
            return;
        }
        ghosts.preview(player, area, blueprint).ifPresent(shown -> {
            if (shown < blueprint.blocks() + blueprint.digs()) {
                deps.messages()
                        .sendMessage(
                                player,
                                PREFIX + "previewPartial",
                                Values.number("shown", shown),
                                Values.number("blocks", blueprint.blocks() + blueprint.digs()));
            }
        });
    }

    private void sendUnmakeable(Player player, Blueprint blueprint) {
        var unmakeable = BlueprintItems.of(blueprint).unmakeable();
        if (unmakeable.isEmpty()) return;
        var names =
                unmakeable.stream().map(type -> type.getKey().getKey()).sorted().collect(Collectors.joining(", "));
        deps.messages().sendMessage(player, PREFIX + "unmakeable", Values.text("types", names));
    }

    private void sendCost(Player player, MaterialCosts.Cost cost) {
        deps.messages().sendMessage(player, costLine(cost));
    }

    private MenuText costLine(MaterialCosts.Cost cost) {
        var economy = deps.services().economy();
        if (cost.unpriced().isEmpty()) {
            return MenuText.create().locale(PREFIX + "cost", Values.money("cost", economy, cost.total()));
        }
        return MenuText.create()
                .locale(
                        PREFIX + "costUnpriced",
                        Values.money("cost", economy, cost.total()),
                        Values.number("unpriced", cost.unpriced().size()));
    }

    /**
     * A build job the player is asked to confirm.
     *
     * @param area      its area
     * @param price     what the company is paid
     * @param hours     how long a company has to finish
     * @param title     title shown to companies
     * @param blueprint its blueprint
     */
    private record PendingBuild(JobArea area, double price, int hours, String title, Blueprint blueprint) {}

    /**
     * Reads a schematic and what its blocks cost off the main thread and hands both on to the main thread. A schematic
     * the player uploaded comes before one of the server with the same name.
     *
     * @param player    the player asking
     * @param schematic name of the schematic
     * @param rotation  degrees to turn it clockwise
     * @param then      gets the blueprint and its cost on the main thread
     */
    private void blueprintOf(
            Player player, String schematic, int rotation, BiConsumer<Blueprint, MaterialCosts.Cost> then) {
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
                () -> jobs().schematics()
                        .blueprint(player.getUniqueId(), schematic, rotation, maxVolume)
                        .map(blueprint -> new Priced(blueprint, jobs().costs().of(blueprint)))
                        .orElseThrow(() -> CommandFailure.of(
                                PREFIX + "error.schematic",
                                Values.text("schematic", schematic),
                                Values.number("max", maxVolume))),
                priced -> Threading.mainThread(deps.plugin())
                        .execute(() -> then.accept(priced.blueprint(), priced.cost())));
    }

    private record Priced(Blueprint blueprint, MaterialCosts.Cost cost) {}

    /**
     * Draws the edges of an area with particles only the player sees, once a second for ten seconds.
     *
     * @param player the player
     * @param area   the area
     */
    void outline(Player player, JobArea area) {
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
            List<MenuText> lines = new ArrayList<>();
            stock.forEach((material, amount) -> lines.add(MenuText.create()
                    .locale(
                            PREFIX + "stock.entry",
                            Values.text("item", material.toLowerCase(Locale.ROOT)),
                            Values.number("amount", amount))));
            deps.services()
                    .menus()
                    .show(
                            player,
                            jobMenu,
                            new JobMenu.Model(
                                    MenuText.create().locale(PREFIX + "stock.title", Values.number("id", id)),
                                    MenuText.create().text("#<id>", Values.number("id", id)),
                                    lines,
                                    List.of(new JobMenu.Action(
                                            "words.collect", "add", "order job collect " + id, JobMenu.Kind.RUN))));
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
     * @return the area the player marked with {@code /order job area}, or else selected with WorldEdit; empty after
     *     telling them how to mark one
     */
    private Optional<JobArea> selected(Player player) {
        var area = jobs().areas().marked(player.getUniqueId());
        var hooks = jobs().hooks();
        if (area.isEmpty() && hooks.selects()) area = hooks.selection(player);
        if (area.isEmpty()) deps.messages().sendError(player, PREFIX + "error.noSelection");
        return area;
    }

    /**
     * Posts a terraform job on the whole plot the player stands on: everything above {@code y} goes, up to the highest
     * block of the plot, and every column is made solid up to {@code y} from its lowest ground. The target may lie below
     * the ground, so the plot is dug out to it; the plot's own bounds limit how deep. The chunk limit is checked before
     * the plot's columns are read.
     *
     * @param sender sender of the command
     * @param price  what the company is paid
     * @param hours  how long a company has to finish
     * @param y      the target height
     * @param title  title shown to companies
     */
    @Executes("terraform plot")
    public void terraformPlot(
            CommandSender sender,
            @DoubleArg(min = 0, max = JobService.MAX_PRICE) double price,
            @IntArg(min = 1) int hours,
            @IntArg int y,
            @StringArg(StringArgType.GREEDY) String title) {
        shapePlot(Senders.player(sender), price, hours, new JobTarget(y, null), title);
    }

    /**
     * Posts a terraform job on the whole plot the player stands on with a surface block at the target height.
     *
     * @param sender sender of the command
     * @param block  the block the top layer is made of
     * @param price  what the company is paid
     * @param hours  how long a company has to finish
     * @param y      the target height
     * @param title  title shown to companies
     */
    @Executes("terraform plot surface")
    public void terraformPlotSurface(
            CommandSender sender,
            @SurfaceBlocks @StringArg(StringArgType.STRING) String block,
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
        shapePlot(player, price, hours, new JobTarget(y, material.name()), title);
    }

    private void shapePlot(Player player, double price, int hours, JobTarget target, String title) {
        var plot = jobs().hooks().ownArea(player);
        if (plot.isEmpty()) {
            deps.messages().sendError(player, PREFIX + "error.noPlot");
            return;
        }
        var found = plot.get();
        if (target.y() < found.minY() || target.y() > found.maxY()) {
            deps.messages()
                    .sendError(
                            player,
                            PREFIX + "error.plotDepth",
                            Values.number("min", found.minY()),
                            Values.number("max", found.maxY()));
            return;
        }
        var settings = deps.configuration().jobSettings();
        if (found.chunks() > settings.maxChunks()) {
            deps.messages().sendError(player, PREFIX + "error.chunks", Values.number("max", settings.maxChunks()));
            return;
        }
        var world = player.getWorld();
        var top = target.y();
        var bottom = target.y();
        for (var x = found.minX(); x <= found.maxX(); x++) {
            for (var z = found.minZ(); z <= found.maxZ(); z++) {
                var ground = Math.clamp(world.getHighestBlockYAt(x, z), found.minY(), found.maxY());
                top = Math.max(top, ground);
                bottom = Math.min(bottom, ground);
            }
        }
        var area = new JobArea(found.world(), found.minX(), bottom, found.minZ(), found.maxX(), top, found.maxZ());
        post(player, JobKind.TERRAFORM, area, price, hours, title, JobService.Details.shape(target));
    }

    /**
     * Posts a harvest job on the whole plot the player stands on: the field reaches from the block below the player's
     * feet up {@code height} blocks.
     *
     * @param sender    sender of the command
     * @param unitPrice what one harvested item pays
     * @param budget    the most the job pays in all
     * @param hours     how long the harvest runs
     * @param height    how many layers the field has, the ground below the crops included
     * @param title     title shown to companies
     */
    @Executes("harvest plot")
    public void harvestPlot(
            CommandSender sender,
            @DoubleArg(min = 0, max = JobService.MAX_PRICE) double unitPrice,
            @DoubleArg(min = 0, max = JobService.MAX_PRICE) double budget,
            @IntArg(min = 1) int hours,
            @IntArg(min = 2, max = MAX_DEPTH) int height,
            @StringArg(StringArgType.GREEDY) String title) {
        var player = Senders.player(sender);
        var plot = jobs().hooks().ownArea(player);
        if (plot.isEmpty()) {
            deps.messages().sendError(player, PREFIX + "error.noPlot");
            return;
        }
        var ground = player.getLocation().getBlockY() - 1;
        var found = plot.get();
        var area = new JobArea(
                found.world(), found.minX(), ground, found.minZ(), found.maxX(), ground + height - 1, found.maxZ());
        post(player, JobKind.HARVEST, area, budget, hours, title, JobService.Details.harvest(unitPrice));
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
                    case DRAFT -> "error.draft";
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
     * Cancels a job of the player that nobody works on, or asks the company working on it to let it be cancelled.
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
                        case CANCEL_ASKED -> deps.messages().sendMessage(player, PREFIX + "cancelAsked", values);
                        case ASKED -> deps.messages().sendError(player, PREFIX + "error.cancelAsked", values);
                        case HARVEST -> deps.messages().sendError(player, PREFIX + "error.harvest", values);
                        case UNKNOWN, NOT_YOURS -> deps.messages().sendError(player, PREFIX + "error.unknown", values);
                        case TAKEN -> deps.messages().sendError(player, PREFIX + "error.taken", values);
                        case DRAFT -> deps.messages().sendError(player, PREFIX + "error.draft", values);
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
        checked(player, area, land -> {
            var owner = player.getUniqueId();
            var node = jobs().node();
            run(
                    player,
                    () -> jobs().service()
                            .post(owner, kind, title, price, hours, node, area, land.land(), land.ref(), details),
                    posted -> {
                        if (posted instanceof JobService.Posted.Done)
                            jobs().areas().used(owner, area);
                        posted(player, kind, posted);
                    });
        });
    }

    /**
     * Checks on the main thread that an area may hold a job of the player and hands on the land it lies on, or tells
     * the player why not.
     */
    private void checked(Player player, JobArea area, Consumer<AreaChecks.Checked.Land> then) {
        switch (jobs().checks().check(player, area)) {
            case AreaChecks.Checked.NoWorld noWorld ->
                deps.messages().sendError(player, PREFIX + "error.noWorld", Values.text("world", noWorld.world()));
            case AreaChecks.Checked.Height height ->
                deps.messages()
                        .sendError(
                                player,
                                PREFIX + "error.height",
                                Values.number("min", height.min()),
                                Values.number("max", height.max()));
            case AreaChecks.Checked.Foreign foreign ->
                deps.messages()
                        .sendError(
                                player,
                                PREFIX + "error.foreign",
                                Values.text("land", Objects.requireNonNullElse(foreign.ref(), "?")));
            case AreaChecks.Checked.Unowned unowned -> deps.messages().sendError(player, PREFIX + "error.unowned");
            case AreaChecks.Checked.Land land -> then.accept(land);
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
                                Values.number("max", JobService.limit(refused.refusal(), kind, settings)));
            }
        }
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

    /**
     * @param job a job
     * @return what the blocks of a build job's blueprint cost, empty for other kinds; blocks
     */
    private Optional<MaterialCosts.Cost> cost(Job job) {
        return job.kind() == JobKind.BUILD && !job.supplied() ? jobs().costs().of(job.id()) : Optional.empty();
    }

    private record Info(Job job, Optional<MaterialCosts.Cost> cost, List<JobReviewEntry> reviews) {}

    private void show(
            Player player,
            Job job,
            Optional<DigCheck.Progress> progress,
            Optional<MaterialCosts.Cost> cost,
            List<JobReviewEntry> reviews) {
        var economy = deps.services().economy();
        List<MenuText> lines = new ArrayList<>();
        var description = job.description();
        if (description != null) {
            lines.add(MenuText.create().locale(PREFIX + "info.description", Values.text("description", description)));
        }
        if (job.kind().area()) {
            lines.add(MenuText.create()
                    .locale(
                            PREFIX + "info.area",
                            Values.text("world", job.area().world()),
                            Values.text("center", job.area().center()),
                            Values.number("blocks", job.area().volume()),
                            Values.component("land", land(player, job))));
        } else if (!job.area().world().isEmpty()) {
            lines.add(MenuText.create()
                    .locale(
                            PREFIX + "info.place",
                            Values.text("world", job.area().world()),
                            Values.text("center", job.area().center())));
        }
        lines.add(MenuText.create()
                .locale(
                        PREFIX + "info.money",
                        Values.money("price", economy, job.price()),
                        Values.money("fee", economy, job.fee())));
        cost.ifPresent(found -> lines.add(costLine(found)));
        var harvest = job.harvest();
        if (harvest != null) {
            lines.add(MenuText.create()
                    .locale(
                            PREFIX + "info.harvest",
                            Values.money("unit", deps.services().economy(), harvest.unitPrice()),
                            Values.money("paid", deps.services().economy(), harvest.paid()),
                            Values.money("price", deps.services().economy(), job.price())));
        }
        var target = job.target();
        if (target != null) {
            var surface = target.surface();
            if (surface == null) {
                lines.add(MenuText.create().locale(PREFIX + "info.target", Values.number("y", target.y())));
            } else {
                lines.add(MenuText.create()
                        .locale(
                                PREFIX + "info.targetSurface",
                                Values.number("y", target.y()),
                                Values.text("block", surface.toLowerCase(Locale.ROOT))));
            }
        }
        if (job.state() == JobState.OPEN) {
            lines.add(MenuText.create().locale(PREFIX + "info.open", Values.text("time", left(job.expires()))));
        }
        var due = job.due();
        if (job.state() == JobState.TAKEN && due != null) {
            lines.add(MenuText.create().locale(PREFIX + "info.taken", Values.text("time", left(due))));
        }
        var extension = job.extension();
        if (job.state() == JobState.TAKEN && extension.extendedHours() > 0) {
            lines.add(MenuText.create()
                    .locale(PREFIX + "info.extended", Values.number("hours", extension.extendedHours())));
        }
        var asked = extension.askedHours();
        if (job.state() == JobState.TAKEN && asked != null) {
            lines.add(MenuText.create()
                    .locale(
                            PREFIX + "info.extensionAsked",
                            Values.number("hours", asked),
                            Values.text("reason", Objects.requireNonNullElse(extension.reason(), ""))));
        }
        var review = job.review();
        lines.addAll(JobMenus.reviews(PREFIX, reviews));
        if (job.state() == JobState.TAKEN && review.cancelAsked() != null) {
            lines.add(MenuText.create().locale(PREFIX + "info.cancelAsked"));
        }
        if (job.state() == JobState.DONE) {
            var approveAt = Objects.requireNonNullElse(review.doneAt(), Times.now())
                    .plusHours(deps.configuration().jobSettings().approveHours());
            lines.add(MenuText.create().locale(PREFIX + "info.waiting", Values.text("time", left(approveAt))));
        }
        if (job.state() == JobState.DISPUTED) {
            lines.add(MenuText.create()
                    .locale(
                            PREFIX + "info.disputed",
                            Values.text("reason", Objects.requireNonNullElse(review.dispute(), ""))));
        }
        var settled = review.settled();
        if (job.state() == JobState.SETTLED && settled != null) {
            lines.add(MenuText.create().locale(PREFIX + "info.settled", Values.number("percent", settled)));
        }
        progress.ifPresent(found -> lines.add(MenuText.create()
                .locale(
                        PREFIX + "info.progress",
                        Values.number("percent", found.percent()),
                        Values.number("cleared", found.cleared()),
                        Values.number("total", found.total()))));
        deps.services()
                .menus()
                .show(player, jobMenu, new JobMenu.Model(title(player, job), JobMenus.name(job), lines, actions(job)));
    }

    private MenuText title(Player player, Job job) {
        return MenuText.create()
                .locale(
                        PREFIX + "info.head",
                        Values.number("id", job.id()),
                        Values.text("job_title", job.title()),
                        Values.component("state", texts(player).locale(stateKey(job.state()))));
    }

    private void extensionActions(List<JobMenu.Action> actions, Job job) {
        if (!deps.configuration().jobSettings().extensionsEnabled()) return;
        var id = job.id();
        if (job.extension().asked()) {
            actions.add(new JobMenu.Action(
                    PREFIX + "grantButton", "add", "order job extension grant " + id, JobMenu.Kind.CHECK));
            actions.add(JobMenu.Action.text(
                    PREFIX + "refuseButton",
                    "remove",
                    "order job extension refuse " + id,
                    "jobs.menu.reason",
                    JobReview.MAX_REASON));
        }
        if (jobs().service().hoursLeft(job) <= 0) return;
        actions.add(JobMenu.Action.hours(PREFIX + "extendButton", "show", "order job extend " + id, null, 0));
    }

    /**
     * @param job a job of the player
     * @return what the player may do with it now
     */
    private List<JobMenu.Action> actions(Job job) {
        var id = job.id();
        List<JobMenu.Action> actions = new ArrayList<>();
        switch (job.state()) {
            case OPEN -> {
                actions.add(JobMenu.Action.text(
                        PREFIX + "describeButton",
                        "show",
                        "order job describe " + id,
                        "jobs.menu.description",
                        Job.MAX_DESCRIPTION));
                actions.add(new JobMenu.Action("words.cancel", "remove", "order job cancel " + id, JobMenu.Kind.CHECK));
            }
            case DONE -> {
                actions.add(new JobMenu.Action("words.accept", "add", "order job approve " + id, JobMenu.Kind.CHECK));
                actions.add(JobMenu.Action.text(
                        "words.deny", "remove", "order job reject " + id, "jobs.menu.reason", JobReview.MAX_REASON));
                actions.add(dispute(id));
            }
            case DISPUTED -> {
                actions.add(new JobMenu.Action(
                        PREFIX + "approveEarlyButton", "add", "order job approve " + id, JobMenu.Kind.CHECK));
                if (job.owner().equals(job.review().disputeBy())) {
                    actions.add(new JobMenu.Action(
                            PREFIX + "withdrawButton", "show", "order job withdraw " + id, JobMenu.Kind.CHECK));
                }
            }
            case TAKEN -> {
                if (job.kind() == JobKind.HARVEST) {
                    actions.add(stockAction(id));
                    actions.add(new JobMenu.Action(
                            PREFIX + "endButton", "remove", "order job end " + id, JobMenu.Kind.CHECK));
                } else {
                    actions.add(new JobMenu.Action(
                            PREFIX + "approveEarlyButton", "add", "order job approve " + id, JobMenu.Kind.CHECK));
                    actions.add(dispute(id));
                }
                extensionActions(actions, job);
                if (job.kind() != JobKind.HARVEST && job.review().cancelAsked() == null) {
                    actions.add(new JobMenu.Action(
                            PREFIX + "askCancelButton", "remove", "order job cancel " + id, JobMenu.Kind.CHECK));
                }
            }
            case DRAFT -> {
                if (job.supplied()) {
                    actions.add(new JobMenu.Action(
                            PREFIX + "handInButton", "show", "order job draft " + id, JobMenu.Kind.RUN));
                }
                actions.add(
                        new JobMenu.Action(PREFIX + "postButton", "add", "order job post " + id, JobMenu.Kind.CHECK));
                actions.add(new JobMenu.Action(
                        PREFIX + "deleteDraftButton", "remove", "order job draft delete " + id, JobMenu.Kind.CHECK));
            }
            default -> {
                if (job.kind() == JobKind.HARVEST) actions.add(stockAction(id));
            }
        }
        return actions;
    }

    private static JobMenu.Action dispute(int id) {
        return JobMenu.Action.text(
                PREFIX + "disputeButton",
                "remove",
                "order job dispute " + id,
                "jobs.menu.reason",
                JobReview.MAX_REASON);
    }

    private static JobMenu.Action stockAction(int id) {
        return new JobMenu.Action(PREFIX + "stockButton", "show", "order job stock " + id, JobMenu.Kind.RUN);
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
                        Values.money("price", economy, job.price()));
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

    <T> void run(Player player, Supplier<T> query, Consumer<T> answer) {
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
