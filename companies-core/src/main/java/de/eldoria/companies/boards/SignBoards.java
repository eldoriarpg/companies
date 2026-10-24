/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards;

import de.eldoria.companies.boards.SignBoardRefresher.WriteResult;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.BoardSettings;
import de.eldoria.companies.events.order.OrderAcceptEvent;
import de.eldoria.companies.events.order.OrderCanceledEvent;
import de.eldoria.companies.events.order.OrderCreatedEvent;
import de.eldoria.companies.events.order.OrderExpiredEvent;
import de.eldoria.companies.events.order.OrderRemovedEvent;
import de.eldoria.companies.events.order.SoloOrderAbortEvent;
import de.eldoria.companies.events.order.SoloOrderAcceptEvent;
import de.eldoria.companies.events.order.SoloOrderExpiredEvent;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.util.Threading;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Board signs: a sign whose first line is the configured header becomes a board when an admin writes it, and opens
 * the board for whoever right-clicks it.
 * <p>
 * The second line names a preset or materials; empty, the board shows every open order. A player without
 * {@link Permission.Admin#BOARDS} gets the header cleared, and so does an unknown board. A created board is stored on
 * the sign, the sign is waxed and both sides show the header, the label, the open order count and a hint.
 * <p>
 * Board signs of loaded chunks are kept in the {@link SignBoardIndex}, and their counts are refreshed by the
 * {@link SignBoardRefresher}: regularly, and a few seconds after an order on this server was created, accepted,
 * released or removed.
 */
public final class SignBoards implements Listener {
    private static final Duration EVENT_REFRESH_GAP = Duration.ofSeconds(5);
    private static final long TICKS_PER_SECOND = 20L;
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Plugin plugin;
    private final Boards boards;
    private final Supplier<BoardSettings> settings;
    private final BoardSignData data;
    private final SignBoardIndex index;
    private final SignBoardLines lines;
    private final SignBoardRefresher refresher;
    private final BiConsumer<CommandSender, CommandFailure> report;

    /**
     * @param plugin   the plugin
     * @param boards   resolves and opens boards
     * @param settings the current board settings
     * @param localize localizes a locale key in the server's language
     * @param report   shows a failure to a player
     * @param clock    clock of the refresh schedule
     */
    public SignBoards(
            Plugin plugin,
            Boards boards,
            Supplier<BoardSettings> settings,
            Function<String, String> localize,
            BiConsumer<CommandSender, CommandFailure> report,
            Clock clock) {
        this.plugin = plugin;
        this.boards = boards;
        this.settings = settings;
        this.report = report;
        this.data = new BoardSignData(plugin);
        this.index = new SignBoardIndex(data);
        this.lines = new SignBoardLines(localize, () -> settings.get().signHeader());
        this.refresher = new SignBoardRefresher(
                index,
                boards::resolve,
                boards::openCount,
                this::write,
                new RefreshSchedule(clock, () -> settings.get().refresh(), EVENT_REFRESH_GAP),
                Threading.mainThread(plugin),
                plugin.getLogger());
    }

    /**
     * Indexes the board signs of the loaded chunks, registers the listeners and starts the refresh.
     */
    public void start() {
        for (var world : plugin.getServer().getWorlds()) {
            for (var chunk : world.getLoadedChunks()) {
                index.load(chunk.getTileEntities(BoardSignData::isSign, false));
            }
        }
        var pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(this, plugin);
        pluginManager.registerEvents(new SignBoardProtection(data, index, refresher::forget, report), plugin);
        boards.onPresetsChanged(this::rerender);
        plugin.getServer().getScheduler().runTaskTimer(plugin, refresher::tick, TICKS_PER_SECOND, TICKS_PER_SECOND);
    }

    /**
     * Writes every board sign again, for changed presets.
     */
    public void rerender() {
        refresher.rerender();
    }

    /**
     * @return the board signs in loaded chunks
     */
    public SignBoardIndex index() {
        return index;
    }

    /**
     * @return reads and writes the board stored on a sign
     */
    public BoardSignData data() {
        return data;
    }

    /**
     * @return refreshes the counts on the signs
     */
    public SignBoardRefresher refresher() {
        return refresher;
    }

    /**
     * Turns a sign with the header into a board, for admins only.
     *
     * @param event the sign change
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!plain(event.line(0)).equalsIgnoreCase(settings.get().signHeader())) return;
        var player = event.getPlayer();
        if (!player.hasPermission(Permission.Admin.BOARDS)) {
            event.line(0, Component.empty());
            report.accept(player, CommandFailure.of("boards.error.create"));
            return;
        }
        var text = plain(event.line(1));
        var target = boards.resolve(text);
        if (target.isEmpty()) {
            event.line(0, Component.empty());
            report.accept(player, CommandFailure.of("boards.error.unknown", Placeholder.unparsed("board", text)));
            return;
        }
        var shown = lines.lines(target.get(), null);
        for (var line = 0; line < shown.size(); line++) {
            event.line(line, shown.get(line));
        }
        var block = event.getBlock();
        plugin.getServer().getScheduler().runTask(plugin, () -> establish(block, target.get()));
    }

    /**
     * Stores the board on the sign once the server wrote the edited lines, waxes it and writes both sides.
     *
     * @param block  the sign block
     * @param target what the board shows
     */
    void establish(Block block, BoardTarget target) {
        if (!(block.getState() instanceof Sign sign)) return;
        data.write(sign, target.spec());
        sign.setWaxed(true);
        SignBoardLines.write(sign, lines.lines(target, null));
        sign.update(true, false);
        var location = BoardLocation.of(block);
        index.put(location, target.spec());
        refresher.forget(location);
        refresher.refreshSoon();
    }

    /**
     * Opens the board of a right-clicked board sign.
     *
     * @param event the interaction
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        var block = event.getClickedBlock();
        if (block == null) return;
        var spec = data.read(block);
        if (spec.isEmpty()) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        var player = event.getPlayer();
        try {
            boards.open(player, boards.require(spec.get()));
        } catch (CommandFailure failure) {
            report.accept(player, failure);
        }
    }

    /**
     * @param event a loaded chunk
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        index.load(event.getChunk().getTileEntities(BoardSignData::isSign, false));
    }

    /**
     * @param event an unloaded chunk
     */
    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        index.unload(event.getChunk());
    }

    /**
     * @param event a created order
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderCreated(OrderCreatedEvent event) {
        refresher.markDirty();
    }

    /**
     * @param event an accepted order
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderAccepted(OrderAcceptEvent event) {
        refresher.markDirty();
    }

    /**
     * @param event an order a company gave up
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderCanceled(OrderCanceledEvent event) {
        refresher.markDirty();
    }

    /**
     * @param event an order that ran out of time
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderExpired(OrderExpiredEvent event) {
        refresher.markDirty();
    }

    /**
     * @param event a removed order
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderRemoved(OrderRemovedEvent event) {
        refresher.markDirty();
    }

    /**
     * @param event an order a player claimed alone
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloOrderAccepted(SoloOrderAcceptEvent event) {
        refresher.markDirty();
    }

    /**
     * @param event an order a player working alone gave up
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloOrderAborted(SoloOrderAbortEvent event) {
        refresher.markDirty();
    }

    /**
     * @param event an order a player working alone did not deliver in time
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloOrderExpired(SoloOrderExpiredEvent event) {
        refresher.markDirty();
    }

    private WriteResult write(BoardLocation location, BoardTarget target, int count) {
        var world = plugin.getServer().getWorld(location.world());
        if (world == null || !world.isChunkLoaded(location.chunkX(), location.chunkZ())) return WriteResult.UNLOADED;
        var state = world.getBlockAt(location.x(), location.y(), location.z()).getState();
        if (!(state instanceof Sign sign) || data.stored(sign).isEmpty()) return WriteResult.GONE;
        SignBoardLines.write(sign, lines.lines(target, count));
        sign.update(true, false);
        return WriteResult.WRITTEN;
    }

    private static String plain(@Nullable Component line) {
        return line == null ? "" : PLAIN.serialize(line).strip();
    }
}
