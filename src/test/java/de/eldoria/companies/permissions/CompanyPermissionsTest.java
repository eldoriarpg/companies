package de.eldoria.companies.permissions;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CompanyPermissionsTest {

    public static final long OWNER = 1L;
    public static final long INVITE = 2L;
    public static final long ACCEPT = 4L;

    @Test
    void hasPermission() {
        Assertions.assertTrue(CompanyPermissions.OWNER.hasPermission(OWNER));
        Assertions.assertTrue(CompanyPermissions.INVITE.hasPermission(INVITE));
        Assertions.assertTrue(CompanyPermissions.ACCEPT_ORDER.hasPermission(ACCEPT));

        Assertions.assertFalse(CompanyPermissions.OWNER.hasPermission(0L));
        Assertions.assertFalse(CompanyPermissions.OWNER.hasPermission(INVITE));
        Assertions.assertFalse(CompanyPermissions.OWNER.hasPermission(ACCEPT));

        Assertions.assertFalse(CompanyPermissions.INVITE.hasPermission(0L));
        Assertions.assertFalse(CompanyPermissions.INVITE.hasPermission(OWNER));
        Assertions.assertFalse(CompanyPermissions.INVITE.hasPermission(ACCEPT));

        Assertions.assertFalse(CompanyPermissions.ACCEPT_ORDER.hasPermission(0L));
        Assertions.assertFalse(CompanyPermissions.ACCEPT_ORDER.hasPermission(OWNER));
        Assertions.assertFalse(CompanyPermissions.ACCEPT_ORDER.hasPermission(INVITE));

        Assertions.assertTrue(CompanyPermissions.ACCEPT_ORDER.hasPermission(ACCEPT + INVITE));
        Assertions.assertTrue(CompanyPermissions.INVITE.hasPermission(ACCEPT + INVITE));

        Assertions.assertTrue(CompanyPermissions.INVITE.hasPermission(OWNER + INVITE + ACCEPT));
        Assertions.assertTrue(CompanyPermissions.ACCEPT_ORDER.hasPermission(OWNER + INVITE + ACCEPT));
        Assertions.assertTrue(CompanyPermissions.OWNER.hasPermission(OWNER + INVITE + ACCEPT));
    }

    @Test
    void composePermissions() {
        Assertions.assertEquals(ACCEPT + INVITE, CompanyPermissions.composePermissions(CompanyPermissions.INVITE, CompanyPermissions.ACCEPT_ORDER));
    }

    @Test
    void getPermissionMask() {
        Assertions.assertEquals(1L, CompanyPermissions.OWNER.mask());
        Assertions.assertEquals(2L, CompanyPermissions.INVITE.mask());
        Assertions.assertEquals(4L, CompanyPermissions.ACCEPT_ORDER.mask());
    }

    @Test
    void getPermissions() {
        var permissions = CompanyPermissions.getPermissions(OWNER + INVITE + ACCEPT);
        Assertions.assertTrue(permissions.contains(CompanyPermissions.OWNER));
        Assertions.assertTrue(permissions.contains(CompanyPermissions.ACCEPT_ORDER));
        Assertions.assertTrue(permissions.contains(CompanyPermissions.INVITE));

        permissions = CompanyPermissions.getPermissions(1L);
        Assertions.assertTrue(permissions.contains(CompanyPermissions.OWNER));

        Assertions.assertTrue(CompanyPermissions.getPermissions(0L).isEmpty());
    }
}
