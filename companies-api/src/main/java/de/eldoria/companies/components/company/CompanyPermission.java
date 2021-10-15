package de.eldoria.companies.components.company;

import net.kyori.adventure.translation.Translatable;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public enum CompanyPermission implements Translatable {
    OWNER(0),
    INVITE(1),
    KICK(2),
    MANAGE_ORDERS(3),
    MANAGE_PERMISSIONS(4);

    private final long mask;
    private final int rawMask;

    CompanyPermission(int mask) {
        this.mask = toBitMask(mask);
        rawMask = mask;
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

    public boolean hasPermission(long mask) {
        return (mask & (mask())) != 0L;
    }

    private long toBitMask(int mask) {
        return 1L << mask;
    }

    public long mask() {
        return mask;
    }

    public int rawMask() {
        return rawMask;
    }

    @Override
    public @NotNull String translationKey() {
        return "enums.companyPermission." + name().toLowerCase(Locale.ROOT);
    }
}
