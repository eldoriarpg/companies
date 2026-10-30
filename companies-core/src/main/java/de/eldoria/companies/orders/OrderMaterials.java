/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The materials of the lines of an order, as the item scope of server events sees them.
 * <p>
 * Whether an event applies to an order, for its tax and fee reductions, depends on every material a line accepts: the
 * material of a plain or exact line, and every material a criteria line accepts, its tags resolved, as
 * {@code order_line_materials} stores them. What a multiplier pays on top depends on what a worker really delivered:
 * the gross of a worker on a line is split by the items of each material the worker delivered to it.
 */
public final class OrderMaterials {
    private OrderMaterials() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @param order an order
     * @return every material its lines accept
     */
    public static Set<String> accepted(FullOrder order) {
        Set<String> materials = new LinkedHashSet<>();
        for (var content : order.contents()) materials.addAll(accepted(content));
        return materials;
    }

    /**
     * @param content a line of an order
     * @return every material the line accepts
     */
    public static Set<String> accepted(OrderContent content) {
        var criteria = content.criteria();
        if (criteria.isEmpty()) return Set.of(content.material().name());
        return names(criteria.get().materials());
    }

    /**
     * @param lines the lines of a draft
     * @return every material the lines accept
     */
    public static Set<String> accepted(List<OrderBuilder.DraftLine> lines) {
        Set<String> materials = new LinkedHashSet<>();
        for (var line : lines) {
            var criteria = line.criteria();
            if (criteria == null) materials.add(line.template().getType().name());
            else materials.addAll(names(criteria.materials()));
        }
        return materials;
    }

    /**
     * What a worker earns on each material they delivered to an order: the gross of each line split by the items of
     * each material the worker delivered to it.
     *
     * @param order  the order
     * @param worker the worker
     * @return the gross per line and material, by material
     */
    public static List<Delivered> delivered(FullOrder order, UUID worker) {
        List<Delivered> parts = new ArrayList<>();
        for (var content : order.contents()) {
            var gross = content.payments().getOrDefault(worker, 0.0);
            if (!(gross > 0)) continue;
            Map<String, Integer> items = new LinkedHashMap<>();
            for (var delivery : content.deliveries()) {
                if (!delivery.worker().equals(worker) || delivery.amount() <= 0) continue;
                items.merge(delivery.stack().getType().name(), delivery.amount(), Integer::sum);
            }
            var total = items.values().stream().mapToInt(Integer::intValue).sum();
            if (total <= 0) {
                parts.add(new Delivered(content.material().name(), gross));
                continue;
            }
            for (var entry : items.entrySet()) {
                parts.add(new Delivered(entry.getKey(), gross * entry.getValue() / total));
            }
        }
        return parts;
    }

    private static Set<String> names(Collection<Material> materials) {
        Set<String> names = new LinkedHashSet<>();
        for (var material : materials) names.add(material.name());
        return names;
    }

    /**
     * What a worker earns on one material of one line.
     *
     * @param material the material delivered
     * @param gross    its part of the worker's gross on the line
     */
    public record Delivered(String material, double gross) {}
}
