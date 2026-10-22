/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.wrapper.company.CompanyMember;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One member editing the company permissions of another member.
 * <p>
 * The rules: OWNER is never edited, the editor needs MANAGE_PERMISSIONS, may only grant or revoke permissions they hold
 * themselves, and the permissions of the owner are left as they are.
 *
 * @param editor member changing the permissions
 * @param target member whose permissions are changed
 */
public record PermissionEdit(CompanyMember editor, CompanyMember target) {
    /**
     * The permissions an editor sees, every one except OWNER.
     *
     * @return permissions in declaration order
     */
    public static List<CompanyPermission> shown() {
        return Arrays.stream(CompanyPermission.values())
                .filter(permission -> permission != CompanyPermission.OWNER)
                .toList();
    }

    /**
     * Whether the editor may grant or revoke a permission of the target.
     *
     * @param permission permission to change
     * @return true when the change is allowed
     */
    public boolean editable(CompanyPermission permission) {
        return permission != CompanyPermission.OWNER
                && !target.isOwner()
                && editor.hasPermissions(CompanyPermission.MANAGE_PERMISSIONS, permission);
    }

    /**
     * Whether the target holds a permission.
     *
     * @param permission permission to check
     * @return true when held
     */
    public boolean granted(CompanyPermission permission) {
        return target.hasPermission(permission);
    }

    /**
     * Applies the wanted state of each permission to the target. Permissions that are not {@link #editable} are
     * skipped.
     *
     * @param wanted whether each permission should be held
     * @return the permissions whose state changed
     */
    public Set<CompanyPermission> apply(Map<CompanyPermission, Boolean> wanted) {
        Set<CompanyPermission> changed = EnumSet.noneOf(CompanyPermission.class);
        wanted.forEach((permission, grant) -> {
            if (!editable(permission) || granted(permission) == grant) return;
            if (grant) {
                target.addPermission(permission);
            } else {
                target.removePermission(permission);
            }
            changed.add(permission);
        });
        return changed;
    }
}
