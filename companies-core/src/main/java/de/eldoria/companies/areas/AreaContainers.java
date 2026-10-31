/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.areas;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.util.Permission;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.BlockInventoryHolder;

import java.util.Set;
import java.util.function.Consumer;

/**
 * The container rule of company areas: inside an area of a company, its members open chests, barrels, furnaces,
 * hoppers, shulker boxes and every other block inventory, and use lecterns, chiseled bookshelves and decorated pots,
 * only with {@code CONTAINERS}. The owner of the company, the owners of the area (such as the member who linked it)
 * and players with {@code companies.admin.areas.bypass} always may. Players outside the company are left to the area's
 * plugin, which keeps strangers out; the rule only narrows what members may. It runs after the area plugins, so it
 * never allows what they forbid. Runs on the main thread, reading what the last {@link AreaSync} check found.
 */
public final class AreaContainers implements Listener {
    private static final Set<Material> USED = Set.of(Material.CHISELED_BOOKSHELF, Material.DECORATED_POT);

    private final AreaProviders providers;
    private final AreaSync sync;
    private final Consumer<Player> refuse;

    /**
     * @param providers the plot and claim plugins
     * @param sync      knows which company an area belongs to and its members
     * @param refuse    tells a player they may not open containers here
     */
    public AreaContainers(AreaProviders providers, AreaSync sync, Consumer<Player> refuse) {
        this.providers = providers;
        this.sync = sync;
        this.refuse = refuse;
    }

    /**
     * Keeps members without {@code CONTAINERS} out of the block inventories of their company's areas.
     *
     * @param event the opening
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        var holder = event.getInventory().getHolder(false);
        if (!(holder instanceof BlockInventoryHolder) && !(holder instanceof DoubleChest)) return;
        var location = event.getInventory().getLocation();
        if (location == null || allowed(player, location)) return;
        event.setCancelled(true);
        refuse.accept(player);
    }

    /**
     * Keeps members without {@code CONTAINERS} from taking books out of chiseled bookshelves and items out of
     * decorated pots in their company's areas.
     *
     * @param event the use
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        var block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || !USED.contains(block.getType())) return;
        if (event.useInteractedBlock() == Event.Result.DENY) return;
        if (allowed(event.getPlayer(), block.getLocation())) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        refuse.accept(event.getPlayer());
    }

    /**
     * @param player   a player
     * @param location the container
     * @return whether the rule lets the player use it
     */
    boolean allowed(Player player, Location location) {
        if (player.hasPermission(Permission.Admin.AREAS_BYPASS)) return true;
        var uuid = player.getUniqueId();
        for (var area : providers.areasAt(location)) {
            var company = sync.company(area);
            if (company.isEmpty()) continue;
            var member = sync.member(company.get(), uuid);
            if (member.isEmpty()) continue;
            if (member.get().hasPermission(CompanyPermission.CONTAINERS)) continue;
            if (providers.call(area.provider(), provider -> provider.isOwner(uuid, area.ref()), false)) continue;
            return false;
        }
        return true;
    }
}
