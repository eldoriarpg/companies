/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Map;

class DeliveryServiceTest {
    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    private static DeliveryPart part(int line, Material material, int amount) {
        return new DeliveryPart(line, new ComponentStack(material), amount);
    }

    @Test
    void surplusKeepsTheItemOfItsOwnPart() {
        var stone = part(0, Material.STONE, 10);
        var cobble = part(0, Material.COBBLESTONE, 5);
        var dirt = part(1, Material.DIRT, 4);

        var surplus = DeliveryService.surplus(List.of(stone, cobble, dirt), List.of(10, 2, 0));

        Assertions.assertEquals(2, surplus.size());
        Assertions.assertSame(cobble.stack(), surplus.get(0).stack());
        Assertions.assertEquals(3, surplus.get(0).amount());
        Assertions.assertSame(dirt.stack(), surplus.get(1).stack());
        Assertions.assertEquals(4, surplus.get(1).amount());
    }

    @Test
    void unansweredPartsGoBackCompletely() {
        var parts = List.of(part(0, Material.STONE, 10), part(1, Material.DIRT, 4));

        Assertions.assertEquals(parts, DeliveryService.surplus(parts, null));
        Assertions.assertEquals(List.of(parts.get(1)), DeliveryService.surplus(parts, List.of(10)));
    }

    @Test
    void recordedAmountsAreSummedPerLine() {
        var parts = List.of(part(0, Material.STONE, 10), part(1, Material.DIRT, 4), part(0, Material.STONE, 5));

        Assertions.assertEquals(Map.of(0, 13, 1, 4), DeliveryService.byLine(parts, List.of(10, 4, 3)));
        Assertions.assertEquals(Map.of(0, 10), DeliveryService.byLine(parts, List.of(10, 0)));
    }
}
