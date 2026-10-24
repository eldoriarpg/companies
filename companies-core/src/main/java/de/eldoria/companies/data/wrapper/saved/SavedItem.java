/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.saved;

import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An item a player saved to order it again, as the template an order line requests.
 *
 * @param id      id of the saved item
 * @param owner   player who saved it
 * @param name    name the player gave it, unique among their saved items
 * @param stack   the template, with an amount of one
 * @param created when it was saved, in UTC
 */
public record SavedItem(int id, UUID owner, String name, ItemStack stack, LocalDateTime created) {}
