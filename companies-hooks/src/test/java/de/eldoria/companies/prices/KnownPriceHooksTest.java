/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.prices;

import de.eldoria.companies.components.price.PriceSource;
import de.eldoria.companies.configuration.elements.prices.PriceSettings;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnownPriceHooksTest {
    @Test
    void everyKnownPriceSourceIsCreatedWithoutItsPlugin() throws ReflectiveOperationException {
        var context = new PriceHookContext(null, PriceSettings::new);
        var ids = new HashSet<String>();
        for (var className : PriceHooks.KNOWN.values()) {
            var source = (PriceSource) Class.forName(className)
                    .getDeclaredConstructor(PriceHookContext.class)
                    .newInstance(context);
            ids.add(source.id());
        }

        assertEquals(Set.of("economyshopgui", "shopguiplus", "zshop", "chestshop", "shopkeepers"), ids);
        assertEquals(
                Set.of("EconomyShopGUI", "EconomyShopGUI-Premium", "ShopGUIPlus", "zShop", "ChestShop", "Shopkeepers"),
                PriceHooks.KNOWN.keySet().stream().collect(Collectors.toSet()));
    }
}
