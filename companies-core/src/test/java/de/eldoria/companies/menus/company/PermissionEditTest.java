/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static de.eldoria.companies.components.company.CompanyPermission.INVITE;
import static de.eldoria.companies.components.company.CompanyPermission.KICK;
import static de.eldoria.companies.components.company.CompanyPermission.MANAGE_ORDERS;
import static de.eldoria.companies.components.company.CompanyPermission.MANAGE_PERMISSIONS;
import static de.eldoria.companies.components.company.CompanyPermission.OWNER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionEditTest {

    private static CompanyMember member(CompanyPermission... permissions) {
        return CompanyMember.of(1, UUID.randomUUID(), CompanyPermission.composePermissions(permissions));
    }

    @Test
    void ownerIsNeverShown() {
        assertEquals(List.of(INVITE, KICK, MANAGE_ORDERS, MANAGE_PERMISSIONS), PermissionEdit.shown());
    }

    @Test
    void editorMayOnlyChangePermissionsTheyHold() {
        var edit = new PermissionEdit(member(MANAGE_PERMISSIONS, KICK), member());

        assertTrue(edit.editable(KICK));
        assertTrue(edit.editable(MANAGE_PERMISSIONS));
        assertFalse(edit.editable(INVITE));
        assertFalse(edit.editable(MANAGE_ORDERS));
    }

    @Test
    void nothingIsEditableWithoutManagePermissions() {
        var edit = new PermissionEdit(member(INVITE, KICK, MANAGE_ORDERS), member());

        assertTrue(PermissionEdit.shown().stream().noneMatch(edit::editable));
    }

    @Test
    void ownerMayChangeEverythingButOwner() {
        var edit = new PermissionEdit(member(OWNER), member());

        assertTrue(PermissionEdit.shown().stream().allMatch(edit::editable));
        assertFalse(edit.editable(OWNER));
    }

    @Test
    void permissionsOfTheOwnerAreProtected() {
        var edit = new PermissionEdit(member(OWNER), member(OWNER));

        assertTrue(PermissionEdit.shown().stream().noneMatch(edit::editable));
    }

    @Test
    void applyGrantsAndRevokesTheDifferences() {
        var target = member(INVITE);
        var edit = new PermissionEdit(member(OWNER), target);

        var changed = edit.apply(Map.of(INVITE, false, KICK, true, MANAGE_ORDERS, false));

        assertEquals(Set.of(INVITE, KICK), changed);
        assertEquals(CompanyPermission.composePermissions(KICK), target.permission());
    }

    @Test
    void applySkipsWhatTheEditorMayNotChange() {
        var target = member(MANAGE_ORDERS);
        var edit = new PermissionEdit(member(MANAGE_PERMISSIONS, KICK), target);

        var changed = edit.apply(Map.of(KICK, true, INVITE, true, MANAGE_ORDERS, false, OWNER, true));

        assertEquals(Set.of(KICK), changed);
        assertEquals(CompanyPermission.composePermissions(KICK, MANAGE_ORDERS), target.permission());
    }

    @Test
    void applyLeavesTheOwnerUntouched() {
        var target = member(OWNER);
        var edit = new PermissionEdit(member(OWNER), target);

        assertTrue(edit.apply(Map.of(KICK, false, INVITE, true)).isEmpty());
        assertEquals(OWNER.mask(), target.permission());
    }
}
