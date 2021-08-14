package de.eldoria.companies.permissions;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum CompanyPermission {
    OWNER(0),
    INVITE(1),
    KICK(2),
    MANAGE_ORDERS(3),
    MANAGE_PERMISSIONS(4);

    CompanyPermission(int mask) {
        this.mask = toBitMask(mask);
    }

    private final long mask;

    public boolean hasPermission(long mask) {
        return (mask & (mask())) != 0L;
    }

    private long toBitMask(int mask) {
        return 1L << mask;
    }

    public long mask() {
        return mask;
    }

    public static long composePermissions(CompanyPermission... permissions) {
        return Arrays.stream(permissions).mapToLong(CompanyPermission::mask).sum();
    }

    public static Set<CompanyPermission> getPermissions(long mask) {
        return Arrays.stream(values()).filter(p -> p.hasPermission(mask)).collect(Collectors.toSet());
    }

    public static boolean hasAnyPermission(long mask, CompanyPermission... permissions) {
        for (var permission : permissions) if (permission.hasPermission(mask)) return true;
        return false;
    }

    public static boolean hasPermission(long mask, CompanyPermission... permissions) {
        for (var permission : permissions) if (!permission.hasPermission(mask)) return false;
        return true;
    }
}
