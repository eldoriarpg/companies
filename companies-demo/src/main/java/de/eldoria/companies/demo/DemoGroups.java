/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.Node;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Sets up the LuckPerms groups of the demo on every start, since the nightly reset empties their database: each group
 * gets exactly the permissions of the config, a permission written as {@code -node} is denied. LuckPerms tells the
 * other servers of the network.
 */
final class DemoGroups {
    private final LuckPerms luckPerms;

    /**
     * @param luckPerms the permissions
     */
    DemoGroups(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    /**
     * @param group       the name of the group
     * @param permissions its permissions
     * @return completes once the group is stored
     */
    CompletableFuture<Void> setUp(String group, List<String> permissions) {
        var groups = luckPerms.getGroupManager();
        return groups.createAndLoadGroup(group)
                .thenCompose(loaded -> {
                    loaded.data().clear();
                    for (var permission : permissions) {
                        var parsed = Permission.of(permission);
                        loaded.data()
                                .add(Node.builder(parsed.key())
                                        .value(parsed.granted())
                                        .build());
                    }
                    return groups.saveGroup(loaded);
                })
                .thenRun(() -> luckPerms.getMessagingService().ifPresent(service -> service.pushUpdate()));
    }

    /**
     * A permission of a group.
     *
     * @param key     the permission
     * @param granted false for a denied one
     */
    record Permission(String key, boolean granted) {
        /**
         * @param permission a permission, {@code -} in front to deny it
         * @return the permission
         */
        static Permission of(String permission) {
            var denied = permission.startsWith("-");
            return new Permission(denied ? permission.substring(1) : permission, !denied);
        }
    }
}
