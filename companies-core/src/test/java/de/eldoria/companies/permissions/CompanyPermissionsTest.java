package de.eldoria.companies.permissions;

import de.eldoria.companies.components.company.CompanyPermission;
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
        Assertions.assertTrue(CompanyPermission.MANAGE_ORDERS.hasPermission(ACCEPT));

        Assertions.assertFalse(CompanyPermission.OWNER.hasPermission(0L));
        Assertions.assertFalse(CompanyPermission.OWNER.hasPermission(INVITE));
        Assertions.assertFalse(CompanyPermission.OWNER.hasPermission(ACCEPT));

        Assertions.assertFalse(CompanyPermission.INVITE.hasPermission(0L));
        Assertions.assertFalse(CompanyPermission.INVITE.hasPermission(OWNER));
        Assertions.assertFalse(CompanyPermission.INVITE.hasPermission(ACCEPT));

        Assertions.assertFalse(CompanyPermission.MANAGE_ORDERS.hasPermission(0L));
        Assertions.assertFalse(CompanyPermission.MANAGE_ORDERS.hasPermission(OWNER));
        Assertions.assertFalse(CompanyPermission.MANAGE_ORDERS.hasPermission(INVITE));

        Assertions.assertTrue(CompanyPermission.MANAGE_ORDERS.hasPermission(ACCEPT + INVITE));
        Assertions.assertTrue(CompanyPermission.INVITE.hasPermission(ACCEPT + INVITE));

        Assertions.assertTrue(CompanyPermission.INVITE.hasPermission(OWNER + INVITE + ACCEPT));
        Assertions.assertTrue(CompanyPermission.MANAGE_ORDERS.hasPermission(OWNER + INVITE + ACCEPT));
        Assertions.assertTrue(CompanyPermission.OWNER.hasPermission(OWNER + INVITE + ACCEPT));
    }

    @Test
    void composePermissions() {
        Assertions.assertEquals(ACCEPT + INVITE, CompanyPermission.composePermissions(CompanyPermission.INVITE, CompanyPermission.MANAGE_ORDERS));
    }

    @Test
    void getPermissionMask() {
        Assertions.assertEquals(1L, CompanyPermission.OWNER.mask());
        Assertions.assertEquals(2L, CompanyPermission.INVITE.mask());
        Assertions.assertEquals(4L, CompanyPermission.MANAGE_ORDERS.mask());
    }

    @Test
    void getPermissions() {
        var permissions = CompanyPermission.getPermissions(OWNER + INVITE + ACCEPT);
        Assertions.assertTrue(permissions.contains(CompanyPermission.OWNER));
        Assertions.assertTrue(permissions.contains(CompanyPermission.MANAGE_ORDERS));
        Assertions.assertTrue(permissions.contains(CompanyPermission.INVITE));

        permissions = CompanyPermission.getPermissions(1L);
        Assertions.assertTrue(permissions.contains(CompanyPermission.OWNER));

        Assertions.assertTrue(CompanyPermission.getPermissions(0L).isEmpty());
    }
}
