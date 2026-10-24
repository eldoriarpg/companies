/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

/**
 * Why stored items wait for a player.
 */
public enum ReturnKind {
    /**
     * Items a worker delivered to an order that was released before completion.
     */
    WORKER(0),
    /**
     * Items of a received order that did not fit into the owner's inventory.
     */
    OWNER(1);

    private final int id;

    ReturnKind(int id) {
        this.id = id;
    }

    /**
     * The id stored in the database.
     *
     * @return id of the kind
     */
    public int id() {
        return id;
    }
}
