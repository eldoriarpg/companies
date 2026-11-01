/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The admin sandbox: a visitor may take the group {@value #GROUP} for a while to look at the admin tools. The group
 * is a temporary LuckPerms node, so it ends by itself and holds on every server of the network; a second node marks
 * the time until the visitor may take it again, counted from taking it; ending it early does not shorten that. The
 * nightly reset undoes what admins changed.
 */
final class AdminSandbox {
    /**
     * The group of the sandbox.
     */
    static final String GROUP = "demo-admin";

    /**
     * Marks a player who took the sandbox until it may be taken again.
     */
    static final String COOLDOWN = "companiesdemo.sandbox.cooldown";

    private final LuckPerms luckPerms;
    private final Duration length;
    private final Duration pause;

    /**
     * @param luckPerms the permissions
     * @param length    how long the sandbox lasts
     * @param pause     how long after it ended it may be taken again
     */
    AdminSandbox(LuckPerms luckPerms, Duration length, Duration pause) {
        this.luckPerms = luckPerms;
        this.length = length;
        this.pause = pause;
    }

    /**
     * Gives a player the sandbox.
     *
     * @param player the player
     * @return what came of it
     */
    CompletableFuture<Result> enable(UUID player) {
        var result = new Result[] {Result.ENABLED};
        return luckPerms
                .getUserManager()
                .modifyUser(player, user -> {
                    if (user.getNodes().stream()
                            .anyMatch(node -> SandboxSessions.isSandbox(node) && !node.hasExpired())) {
                        result[0] = Result.ACTIVE;
                        return;
                    }
                    if (user.getNodes().stream()
                            .anyMatch(node -> node.getKey().equals(COOLDOWN) && !node.hasExpired())) {
                        result[0] = Result.WAIT;
                        return;
                    }
                    user.data()
                            .add(InheritanceNode.builder(GROUP).expiry(length).build());
                    user.data()
                            .add(PermissionNode.builder(COOLDOWN)
                                    .expiry(length.plus(pause))
                                    .build());
                })
                .thenApply(done -> result[0]);
    }

    /**
     * Ends the sandbox of a player early; the pause still runs.
     *
     * @param player the player
     * @return whether the player had the sandbox
     */
    CompletableFuture<Boolean> disable(UUID player) {
        var removed = new boolean[1];
        return luckPerms
                .getUserManager()
                .modifyUser(player, user -> {
                    for (var node : user.getNodes()) {
                        if (node instanceof InheritanceNode group
                                && group.getGroupName().equals(GROUP)) {
                            user.data().remove(node);
                            removed[0] = true;
                        }
                    }
                })
                .thenApply(done -> removed[0]);
    }

    /**
     * What came of taking the sandbox.
     */
    enum Result {
        /**
         * The player has the sandbox now.
         */
        ENABLED,
        /**
         * The player has the sandbox already.
         */
        ACTIVE,
        /**
         * The player took it lately and has to wait.
         */
        WAIT
    }

    /**
     * @return how long the sandbox lasts
     */
    Duration length() {
        return length;
    }
}
