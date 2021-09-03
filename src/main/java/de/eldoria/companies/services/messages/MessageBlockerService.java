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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

public class MessageBlockerService extends PacketAdapter implements Listener, IMessageBlockerService {
    private final Map<UUID, List<PacketContainer>> blockedPlayers = new HashMap<>();
    private final ExecutorService executorService;
    private final ProtocolManager manager;

    public static IMessageBlockerService create(Plugin plugin, ExecutorService executorService, ProtocolManager manager) {
        var adapter = new MessageBlockerService(plugin, executorService, manager);
        manager.addPacketListener(adapter);
        plugin.getServer().getPluginManager().registerEvents(adapter, plugin);
        return adapter;
    }

    public MessageBlockerService(Plugin plugin, ExecutorService executorService, ProtocolManager manager) {
        super(plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.CHAT);
        this.executorService = executorService;
        this.manager = manager;
    }

    private List<PacketContainer> getBlockedPackets(Player player) {
        return blockedPlayers.computeIfAbsent(player.getUniqueId(), k -> new LinkedList<>());
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (!blockedPlayers.containsKey(event.getPlayer().getUniqueId())) return;

        var message = AdventureComponentAdapter.rawMessage(event.getPacket());

        getBlockedPackets(event.getPlayer()).add(event.getPacket());
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        blockedPlayers.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void blockPlayer(Player player) {
        blockedPlayers.put(player.getUniqueId(), new LinkedList<>());
    }

    @Override
    public void unblockPlayer(Player player) {
        var blockedPackets = blockedPlayers.remove(player.getUniqueId());
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
    public boolean isActive() {
        return true;
    }
}
