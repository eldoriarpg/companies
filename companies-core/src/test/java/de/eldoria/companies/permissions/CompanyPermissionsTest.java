package de.eldoria.companies.permissions;

import de.eldoria.companies.components.company.CompanyPermission;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CompanyPermissionsTest {

    public static final long OWNER = CompanyPermission.OWNER.mask();
    public static final long INVITE = CompanyPermission.INVITE.mask();
    public static final long KICK = CompanyPermission.KICK.mask();
    public static final long MANAGE_ORDERS = CompanyPermission.MANAGE_ORDERS.mask();
    public static final long MANAGE_PERMISSIONS = CompanyPermission.MANAGE_PERMISSIONS.mask();

    @Test
    void hasPermission() {
        Assertions.assertTrue(CompanyPermission.OWNER.hasPermission(OWNER));
        Assertions.assertTrue(CompanyPermission.INVITE.hasPermission(INVITE));
        Assertions.assertTrue(CompanyPermission.MANAGE_ORDERS.hasPermission(MANAGE_ORDERS));

        Assertions.assertFalse(CompanyPermission.OWNER.hasPermission(0L));
        Assertions.assertFalse(CompanyPermission.OWNER.hasPermission(INVITE));
        Assertions.assertFalse(CompanyPermission.OWNER.hasPermission(MANAGE_ORDERS));

        Assertions.assertFalse(CompanyPermission.INVITE.hasPermission(0L));
        Assertions.assertFalse(CompanyPermission.INVITE.hasPermission(OWNER));
        Assertions.assertFalse(CompanyPermission.INVITE.hasPermission(MANAGE_ORDERS));

        Assertions.assertFalse(CompanyPermission.MANAGE_ORDERS.hasPermission(0L));
        Assertions.assertFalse(CompanyPermission.MANAGE_ORDERS.hasPermission(OWNER));
        Assertions.assertFalse(CompanyPermission.MANAGE_ORDERS.hasPermission(INVITE));

        Assertions.assertTrue(CompanyPermission.MANAGE_ORDERS.hasPermission(MANAGE_ORDERS + INVITE));
        Assertions.assertTrue(CompanyPermission.INVITE.hasPermission(MANAGE_ORDERS + INVITE));

        Assertions.assertTrue(CompanyPermission.INVITE.hasPermission(OWNER + INVITE + MANAGE_ORDERS));
        Assertions.assertTrue(CompanyPermission.MANAGE_ORDERS.hasPermission(OWNER + INVITE + MANAGE_ORDERS));
        Assertions.assertTrue(CompanyPermission.OWNER.hasPermission(OWNER + INVITE + MANAGE_ORDERS));
    }

    @Test
    void composePermissions() {
        Assertions.assertEquals(MANAGE_ORDERS + INVITE, CompanyPermission.composePermissions(CompanyPermission.INVITE, CompanyPermission.MANAGE_ORDERS));
    }

    @Test
    void getPermissionMask() {
        Assertions.assertEquals(1L, CompanyPermission.OWNER.mask());
        Assertions.assertEquals(2L, CompanyPermission.INVITE.mask());
        Assertions.assertEquals(8L, CompanyPermission.MANAGE_ORDERS.mask());
    }

    @Test
    void getPermissions() {
        var permissions = CompanyPermission.getPermissions(OWNER + INVITE + MANAGE_ORDERS);
        Assertions.assertTrue(permissions.contains(CompanyPermission.OWNER));
        Assertions.assertTrue(permissions.contains(CompanyPermission.MANAGE_ORDERS));
        Assertions.assertTrue(permissions.contains(CompanyPermission.INVITE));

        permissions = CompanyPermission.getPermissions(1L);
        Assertions.assertTrue(permissions.contains(CompanyPermission.OWNER));

        Assertions.assertTrue(CompanyPermission.getPermissions(0L).isEmpty());
    }
}
