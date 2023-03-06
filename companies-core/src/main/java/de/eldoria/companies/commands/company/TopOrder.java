/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import net.kyori.adventure.translation.Translatable;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum TopOrder implements Translatable {
    ORDERS("order_count DESC"),
    EARNED("price DESC"),
    MEMBER("member_count DESC"),
    ITEMS_DELIVERED("amount DESC");

    private final String orderColumn;

    TopOrder(String orderColumn) {
        this.orderColumn = orderColumn;
    }

    public String orderColumn() {
        return orderColumn;
    }

    @Override
    public @NotNull String translationKey() {
        return "enums.topOrder" + name().toLowerCase(Locale.ROOT);
    }
}
