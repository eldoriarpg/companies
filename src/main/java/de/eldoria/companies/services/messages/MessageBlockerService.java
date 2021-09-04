package de.eldoria.companies.services.messages;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MessageBlockerService extends PacketAdapter implements Listener, IMessageBlockerService {
    private final Map<UUID, List<PacketContainer>> blockedPlayers = new HashMap<>();
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

    private List<PacketContainer> getBlockedPackets(Player player) {
        return blockedPlayers.computeIfAbsent(player.getUniqueId(), k -> new LinkedList<>());
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (!blockedPlayers.containsKey(event.getPlayer().getUniqueId())) return;

        var message = AdventureComponentAdapter.rawMessage(event.getPacket());
        var announceKey = announcements.get(event.getPlayer().getUniqueId());
        if ((announceKey != null && message.contains(announceKey)) || isWhitelisted(message)) {
            plugin.getLogger().config("Found announce key in message.");
            announcements.remove(event.getPlayer().getUniqueId());
            return;
        }

        plugin.getLogger().config("Blocked message for " + event.getPlayer().getName() + ": " + message);

        getBlockedPackets(event.getPlayer()).add(event.getPacket());
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        blockedPlayers.remove(event.getPlayer().getUniqueId());
        announcements.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void blockPlayer(Player player) {
        blockedPlayers.put(player.getUniqueId(), new LinkedList<>());
        plugin.getLogger().config("Blocking chat for player " + player.getName());
    }

    @Override
    public void unblockPlayer(Player player) {
        plugin.getLogger().config("Unblocking chat for player " + player.getName());
        var blockedPackets = blockedPlayers.remove(player.getUniqueId());
        var clear = " \n".repeat(60);
        player.sendMessage(clear);
        if (blockedPackets == null || blockedPackets.isEmpty()) return;

        executorService.submit(() -> {
            for (var blockedPacket : blockedPackets) {
                try {
                    manager.sendServerPacket(player, blockedPacket);
                } catch (InvocationTargetException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not send packet to player", e);
                }
            }
        });
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
        return blockedPlayers.containsKey(player.getUniqueId());
    }

    @Override
    public boolean isWhitelisted(String value) {
        for (var key : whitelisted) {
            if (value.contains(key)) return true;
        }
        return false;
    }
}
