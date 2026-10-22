/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Orders a company had to give up, together with the workers who got their delivered items returned.
 *
 * @param orders  orders which are open again
 * @param workers workers with returned items
 */
public record ReleasedOrders(List<SimpleOrder> orders, Set<UUID> workers) {}
