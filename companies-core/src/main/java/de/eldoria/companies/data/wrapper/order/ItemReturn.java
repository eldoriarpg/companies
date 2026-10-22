/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

import org.bukkit.inventory.ItemStack;

/**
 * Items a worker delivered to an order that was released before completion and that wait to be handed back.
 *
 * @param id     id of the stored return
 * @param stack  the delivered item
 * @param amount amount of items waiting
 */
public record ItemReturn(int id, ItemStack stack, int amount) {}
