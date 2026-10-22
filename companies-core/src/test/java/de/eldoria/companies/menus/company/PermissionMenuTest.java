/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import io.papermc.paper.dialog.DialogResponseView;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static de.eldoria.companies.components.company.CompanyPermission.INVITE;
import static de.eldoria.companies.components.company.CompanyPermission.KICK;
import static de.eldoria.companies.components.company.CompanyPermission.MANAGE_ORDERS;
import static de.eldoria.companies.components.company.CompanyPermission.MANAGE_PERMISSIONS;
import static de.eldoria.companies.components.company.CompanyPermission.OWNER;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PermissionMenuTest {

    private static CompanyMember member(CompanyPermission... permissions) {
        return CompanyMember.of(1, UUID.randomUUID(), CompanyPermission.composePermissions(permissions));
    }

    @Test
    void inputKeysAreThePermissionNames() {
        assertEquals("manage_orders", PermissionMenu.inputKey(MANAGE_ORDERS));
    }

    @Test
    void wantedReadsOnlyTheEditableToggles() {
        var edit = new PermissionEdit(member(MANAGE_PERMISSIONS, KICK, INVITE), member());
        var response = new Toggles(Map.of(
                "kick", true,
                "invite", false,
                "manage_orders", true,
                "owner", true));

        assertEquals(Map.of(KICK, true, INVITE, false), PermissionMenu.wanted(edit, response));
    }

    @Test
    void savingTheTogglesAppliesTheDifferences() {
        var target = member(INVITE, MANAGE_ORDERS);
        var edit = new PermissionEdit(member(OWNER), target);
        var response = new Toggles(Map.of(
                "invite", true,
                "kick", true,
                "manage_orders", false,
                "manage_permissions", false));

        edit.apply(PermissionMenu.wanted(edit, response));

        assertEquals(CompanyPermission.composePermissions(INVITE, KICK), target.permission());
    }

    private record Toggles(Map<String, Boolean> values) implements DialogResponseView {
        @Override
        public BinaryTagHolder payload() {
            return BinaryTagHolder.binaryTagHolder("{}");
        }

        @Override
        public String getText(String key) {
            return null;
        }

        @Override
        public Boolean getBoolean(String key) {
            return values.get(key);
        }

        @Override
        public Float getFloat(String key) {
            return null;
        }
    }
}
