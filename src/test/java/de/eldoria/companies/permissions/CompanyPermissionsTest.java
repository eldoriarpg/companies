package de.eldoria.companies.permissions;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CompanyPermissionsTest {

    public static final long OWNER = 1L;
    public static final long INVITE = 2L;
    public static final long ACCEPT = 4L;

    @Test
    void hasPermission() {
        Assertions.assertTrue(CompanyPermission.OWNER.hasPermission(OWNER));
        Assertions.assertTrue(CompanyPermission.INVITE.hasPermission(INVITE));
        Assertions.assertTrue(CompanyPermission.ACCEPT_ORDER.hasPermission(ACCEPT));

        Assertions.assertFalse(CompanyPermission.OWNER.hasPermission(0L));
        Assertions.assertFalse(CompanyPermission.OWNER.hasPermission(INVITE));
        Assertions.assertFalse(CompanyPermission.OWNER.hasPermission(ACCEPT));

        Assertions.assertFalse(CompanyPermission.INVITE.hasPermission(0L));
        Assertions.assertFalse(CompanyPermission.INVITE.hasPermission(OWNER));
        Assertions.assertFalse(CompanyPermission.INVITE.hasPermission(ACCEPT));

        Assertions.assertFalse(CompanyPermission.ACCEPT_ORDER.hasPermission(0L));
        Assertions.assertFalse(CompanyPermission.ACCEPT_ORDER.hasPermission(OWNER));
        Assertions.assertFalse(CompanyPermission.ACCEPT_ORDER.hasPermission(INVITE));

        Assertions.assertTrue(CompanyPermission.ACCEPT_ORDER.hasPermission(ACCEPT + INVITE));
        Assertions.assertTrue(CompanyPermission.INVITE.hasPermission(ACCEPT + INVITE));

        Assertions.assertTrue(CompanyPermission.INVITE.hasPermission(OWNER + INVITE + ACCEPT));
        Assertions.assertTrue(CompanyPermission.ACCEPT_ORDER.hasPermission(OWNER + INVITE + ACCEPT));
        Assertions.assertTrue(CompanyPermission.OWNER.hasPermission(OWNER + INVITE + ACCEPT));
    }

    @Test
    void composePermissions() {
        Assertions.assertEquals(ACCEPT + INVITE, CompanyPermission.composePermissions(CompanyPermission.INVITE, CompanyPermission.ACCEPT_ORDER));
    }

    @Test
    void getPermissionMask() {
        Assertions.assertEquals(1L, CompanyPermission.OWNER.mask());
        Assertions.assertEquals(2L, CompanyPermission.INVITE.mask());
        Assertions.assertEquals(4L, CompanyPermission.ACCEPT_ORDER.mask());
    }

    @Test
    void getPermissions() {
        var permissions = CompanyPermission.getPermissions(OWNER + INVITE + ACCEPT);
        Assertions.assertTrue(permissions.contains(CompanyPermission.OWNER));
        Assertions.assertTrue(permissions.contains(CompanyPermission.ACCEPT_ORDER));
        Assertions.assertTrue(permissions.contains(CompanyPermission.INVITE));

        permissions = CompanyPermission.getPermissions(1L);
        Assertions.assertTrue(permissions.contains(CompanyPermission.OWNER));

        Assertions.assertTrue(CompanyPermission.getPermissions(0L).isEmpty());
    }
}
