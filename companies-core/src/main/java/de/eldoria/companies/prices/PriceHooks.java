/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.prices;

import de.eldoria.companies.components.price.PriceSource;

import java.util.Map;
import java.util.logging.Level;

// TODO: register a source again when its shop plugin is enabled again after Companies started
/**
 * Registers Companies' own price sources for the shop plugins it ships hooks for: EconomyShopGUI (free and Premium),
 * ShopGUI+, zShop, ChestShop and Shopkeepers. A source class is compiled against its plugin, so it is only loaded by
 * name and only when that plugin is enabled; a missing plugin can not cause a {@link NoClassDefFoundError}. A source
 * that fails to load, for example because the plugin's API changed, is skipped with a warning. A source is registered
 * in the name of its plugin, so it is dropped when that plugin is disabled, and is switched off with {@code sources}
 * in {@code prices.yml}. A source class has a constructor taking the {@link PriceHookContext}.
 */
public final class PriceHooks {
    static final Map<String, String> KNOWN = Map.of(
            "EconomyShopGUI", "de.eldoria.companies.hooks.economyshopgui.EconomyShopGuiPrices",
            "EconomyShopGUI-Premium", "de.eldoria.companies.hooks.economyshopgui.EconomyShopGuiPrices",
            "ShopGUIPlus", "de.eldoria.companies.hooks.shopguiplus.ShopGuiPlusPrices",
            "zShop", "de.eldoria.companies.hooks.zshop.ZShopPrices",
            "ChestShop", "de.eldoria.companies.hooks.chestshop.ChestShopPrices",
            "Shopkeepers", "de.eldoria.companies.hooks.shopkeepers.ShopkeepersPrices");

    private PriceHooks() {}

    /**
     * Registers the sources of the enabled shop plugins.
     *
     * @param context what the sources get: the plugin, whose class loader holds the hooks, and the settings
     * @param sources the registry
     */
    public static void load(PriceHookContext context, PriceSources sources) {
        load(context, sources, KNOWN);
    }

    /**
     * @param context what the sources get
     * @param sources the registry
     * @param known   the class name of the source of each shop plugin, by the plugin's name
     */
    static void load(PriceHookContext context, PriceSources sources, Map<String, String> known) {
        var plugin = context.plugin();
        var plugins = plugin.getServer().getPluginManager();
        known.forEach((name, className) -> {
            if (!plugins.isPluginEnabled(name)) return;
            try {
                var hook = Class.forName(className, true, PriceHooks.class.getClassLoader())
                        .getDeclaredConstructor(PriceHookContext.class)
                        .newInstance(context);
                var owner = plugins.getPlugin(name);
                sources.register(owner == null ? plugin : owner, (PriceSource) hook);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Could not hook into " + name + " for shop prices.", e);
            }
        });
    }
}
