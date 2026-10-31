/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import de.eldoria.eldoutilities.utils.Permissions;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Permission nodes of the plugin. Every node is a compile-time constant, so commands can name it in annotations.
 */
public final class Permission {
    private static final String BASE = "companies";

    private Permission() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * The permission to hear about new versions on join and to download them.
     */
    public static final String UPDATES = BASE + ".updates";

    public static final class Orders {
        private static final String ORDERS = BASE + ".orders";
        public static final String LIMIT = ORDERS + ".limit";
        public static final String CREATE = ORDERS + ".create";
        public static final String REPEAT = ORDERS + ".repeat";
        /**
         * The permission to claim orders alone, without a company, while solo orders are enabled.
         */
        public static final String SOLO = ORDERS + ".solo";
        /**
         * The permission to post jobs: work in an area of the player's land that a company takes like an order.
         */
        public static final String JOBS = ORDERS + ".jobs";

        private Orders() {
            throw new UnsupportedOperationException("This is a utility class.");
        }

        public static Optional<Integer> getOrderOverride(Player player) {
            var limit = Permissions.findHighestIntPermission(player, LIMIT + ".", -1);
            if (limit == -1) return Optional.empty();
            return Optional.of(limit);
        }
    }

    public static final class Company {
        private static final String COMPANY = BASE + ".company";
        public static final String CREATE = COMPANY + ".create";
        public static final String JOIN = COMPANY + ".join";
        /**
         * The permission to teleport to the company's home.
         */
        public static final String HOME = COMPANY + ".home";

        private Company() {
            throw new UnsupportedOperationException("This is a utility class.");
        }
    }

    /**
     * Permissions of the order boards.
     */
    public static final class Boards {
        /**
         * The permission to open boards through signs, NPCs and {@code /company board}.
         */
        public static final String USE = BASE + ".boards.use";

        private Boards() {
            throw new UnsupportedOperationException("This is a utility class.");
        }
    }

    /**
     * Permissions of the web dashboard.
     */
    public static final class Dashboard {
        /**
         * The permission to open the dashboard with {@code /company dashboard}.
         */
        public static final String USE = BASE + ".dashboard.use";

        private Dashboard() {
            throw new UnsupportedOperationException("This is a utility class.");
        }
    }

    public static final class Admin {
        public static final String ADMIN = BASE + ".admin";
        public static final String CALC_LEVEL = ADMIN + ".calcLevel";
        public static final String DELETE = ADMIN + ".delete";
        public static final String LEVEL = ADMIN + ".level";
        public static final String RELOAD = ADMIN + ".reload";
        public static final String RENAME = ADMIN + ".rename";
        public static final String TRANSFER_OWNER = ADMIN + ".transferOwner";
        public static final String ORDERS = ADMIN + ".orders";
        public static final String PLAYER = ADMIN + ".player";
        public static final String GRANT = ADMIN + ".grant";
        public static final String TREASURY = ADMIN + ".treasury";
        /**
         * The permission to create and break board signs and to manage boards.
         */
        public static final String BOARDS = ADMIN + ".boards";
        /**
         * The permission to place blocks and use buckets in the area of a job taken by another company, and to act
         * outside a job's area on land reached only through a job.
         */
        public static final String JOBS_BYPASS = ADMIN + ".jobs.bypass";
        /**
         * The permission to list and settle disputes about jobs.
         */
        public static final String JOBS = ADMIN + ".jobs";
        /**
         * The permission to create debug reports.
         */
        public static final String DEBUG = ADMIN + ".debug";
        /**
         * The permission to list and remove the nodes registered in the database.
         */
        public static final String NODES = ADMIN + ".nodes";
        /**
         * The permission to show the dashboard connection, end a player's dashboard sessions and enter the key.
         */
        public static final String DASHBOARD = ADMIN + ".dashboard";
        /**
         * The permission to create, look at, pause, resume and cancel event offers.
         */
        public static final String OFFERS = ADMIN + ".offers";

        /**
         * The permission to create, look at, end, cancel and change server events.
         */
        public static final String EVENTS = ADMIN + ".events";

        private Admin() {
            throw new UnsupportedOperationException("This is a utility class.");
        }
    }
}
