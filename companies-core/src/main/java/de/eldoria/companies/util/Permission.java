package de.eldoria.companies.util;

import de.eldoria.eldoutilities.permissions.PermUtil;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class Permission {
    private static final String BASE = "companies";

    private Permission() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    private static String perm(String perm, String... perms) {
        if (perms.length == 0) return perm;
        return String.format("%s.%s", perm, String.join(".", perms));
    }

    public static final class Orders {
        private static final String ORDERS = perm(BASE, "orders");
        public static final String LIMIT = perm(ORDERS, "limit");
        public static final String CREATE = perm(ORDERS, "create");

        private Orders() {
            throw new UnsupportedOperationException("This is a utility class.");
        }

        public static Optional<Integer> getOrderOverride(Player player) {
            var limit = PermUtil.findHighestIntPermission(player, LIMIT + ".", -1);
            if (limit == -1) return Optional.empty();
            return Optional.of(limit);
        }
    }

    public static final class Company {
        private static final String COMPANY = perm(BASE, "company");
        public static final String CREATE = perm(COMPANY, "create");
        public static final String JOIN = perm(COMPANY, "join");

        private Company() {
            throw new UnsupportedOperationException("This is a utility class.");
        }
    }

    public static final class Admin {
        public static final String ADMIN = perm(BASE, "admin");
        public static final String CALC_LEVEL = perm(ADMIN, "calcLevel");
        public static final String DELETE = perm(ADMIN, "delete");
        public static final String LEVEL = perm(ADMIN, "level");
        public static final String RELOAD = perm(ADMIN, "reload");
        public static final String RENAME = perm(ADMIN, "rename");
        public static final String TRANSFER_OWNER = perm(ADMIN, "transferOwner");

        private Admin() {
            throw new UnsupportedOperationException("This is a utility class.");
        }
    }
}
