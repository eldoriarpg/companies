/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public interface IOrderContent {
    int amount();

    void amount(int amount);

    List<? extends IContentPart> parts();

    double price();

    default String prettyType() {
        return stack().getType()
                      .name()
                      .replace("_", " ");
    }

    ItemStack stack();

    int delivered();

    double percent();

    String materialString();

    Material material();

    int missing();
}
