/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DemoGroupsTest {
    @Test
    void aLeadingMinusDeniesAPermission() {
        assertEquals(
                new DemoGroups.Permission("companies.admin.delete", false),
                DemoGroups.Permission.of("-companies.admin.delete"));
        assertEquals(
                new DemoGroups.Permission("companies.admin.orders", true),
                DemoGroups.Permission.of("companies.admin.orders"));
    }
}
