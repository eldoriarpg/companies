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
import org.bukkit.Tag;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The container rule of company areas: inside an area of a company, its members open chests, barrels, furnaces,
 * hoppers, shulker boxes and every other block inventory, chest and hopper minecarts, chest boats and the chests of
 * donkeys, mules and llamas, and use lecterns, chiseled bookshelves, shelves and decorated pots, only with
 * {@code CONTAINERS}. The owner of the company, the owners of the area (such as the member who linked it) and players
 * with {@code companies.admin.areas.bypass} always may. Players outside the company are left to the area's plugin,
 * which keeps strangers out; the rule only narrows what members may. It guards opening and using, not breaking, which
 * stays with the area plugin's build rights. It runs after the area plugins, so it never allows what they forbid. Runs
 * on the main thread, reading what the last {@link AreaSync} check found.
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
     * Keeps members without {@code CONTAINERS} out of the block and entity inventories of their company's areas.
     *
     * @param event the opening
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        var inventory = event.getInventory();
        var location = guarded(inventory.getHolder(false), inventory.getLocation());
        if (location.isEmpty() || allowed(player, location.get())) return;
        event.setCancelled(true);
        refuse.accept(player);
    }

    /**
     * @param holder   the holder of an inventory
     * @param location where the inventory is, as the server reports it
     * @return where the container stands when the rule guards it: a block inventory, a chest or hopper minecart, a
     *     chest boat, or a donkey, mule or llama carrying a chest; empty for any other holder, such as a horse without a
     *     chest
     */
    static Optional<Location> guarded(@Nullable InventoryHolder holder, @Nullable Location location) {
        return switch (holder) {
            case BlockInventoryHolder _, DoubleChest _ -> Optional.ofNullable(location);
            case StorageMinecart _, HopperMinecart _, ChestBoat _ -> Optional.of(((Entity) holder).getLocation());
            case ChestedHorse animal when animal.isCarryingChest() -> Optional.of(animal.getLocation());
            case null, default -> Optional.empty();
        };
    }

    /**
     * Keeps members without {@code CONTAINERS} from taking books out of chiseled bookshelves and items off shelves
     * and out of decorated pots in their company's areas.
     *
     * @param event the use
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        var block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || !used(block.getType())) return;
        if (event.useInteractedBlock() == Event.Result.DENY) return;
        if (allowed(event.getPlayer(), block.getLocation())) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        refuse.accept(event.getPlayer());
    }

    private static boolean used(Material type) {
        return USED.contains(type) || Tag.WOODEN_SHELVES.isTagged(type);
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
