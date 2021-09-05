package de.eldoria.companies.services.messages;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.eldoria.companies.util.RotatingCache;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

public class MessageBlockerService extends PacketAdapter implements Listener, IMessageBlockerService {
    private final Set<UUID> blocked = new HashSet<>();
    private final Map<UUID, RotatingCache<PacketContainer>> messageCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> announcements = new HashMap<>();
    private final ExecutorService executorService;
    private final ProtocolManager manager;
    private final Set<String> whitelisted = new HashSet<>();

    public MessageBlockerService(Plugin plugin, ExecutorService executorService, ProtocolManager manager) {
        super(plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.CHAT);
        this.executorService = executorService;
        this.manager = manager;
        whitelisted.add("[C]");
    }

    public static IMessageBlockerService create(Plugin plugin, ExecutorService executorService, ProtocolManager manager) {
        var adapter = new MessageBlockerService(plugin, executorService, manager);
        manager.addPacketListener(adapter);
        plugin.getServer().getPluginManager().registerEvents(adapter, plugin);
        return adapter;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (!blocked.contains(event.getPlayer().getUniqueId())) {
            getPlayerCache(event.getPlayer()).add(event.getPacket());
            return;
        }

        var message = AdventureComponentAdapter.rawMessage(event.getPacket());
        var announceKey = announcements.get(event.getPlayer().getUniqueId());
        if ((announceKey != null && message.contains(announceKey)) || isWhitelisted(message)) {
            plugin.getLogger().config("Found announce key in message.");
            announcements.remove(event.getPlayer().getUniqueId());
            return;
        }

        plugin.getLogger().config("Blocked message for " + event.getPlayer().getName() + ": " + message);

        getPlayerCache(event.getPlayer()).add(event.getPacket());
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        messageCache.remove(event.getPlayer().getUniqueId());
        blocked.remove(event.getPlayer().getUniqueId());
        announcements.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void blockPlayer(Player player) {
        blocked.add(player.getUniqueId());
        plugin.getLogger().config("Blocking chat for player " + player.getName());
    }

    @Override
    public CompletableFuture<Void> unblockPlayer(Player player) {
        blocked.remove(player.getUniqueId());
        plugin.getLogger().config("Unblocking chat for player " + player.getName());
        var packets = getPlayerCache(player);
        if (packets.isEmpty()) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            for (var blockedPacket : packets.flush()) {
                try {
                    manager.sendServerPacket(player, blockedPacket);
                } catch (InvocationTargetException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not send packet to player", e);
                }
            }
        }, executorService);
    }

    @Override
    public void announce(Player player, String key) {
        announcements.put(player.getUniqueId(), key);
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public boolean isBlocked(Player player) {
        return blocked.contains(player.getUniqueId());
    }

    @Override
    public boolean isWhitelisted(String value) {
        for (var key : whitelisted) {
            if (value.contains(key)) return true;
        }
        return false;
    }

    @NotNull
    public RotatingCache<PacketContainer> getPlayerCache(Player player) {
        return messageCache.computeIfAbsent(player.getUniqueId(), k -> new RotatingCache<>(100));
    }
}
