/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.saved;

/**
 * How saving an item ended.
 */
public enum SaveResult {
    /**
     * The item was saved.
     */
    SAVED,
    /**
     * The player already saved an item with this name.
     */
    NAME_TAKEN,
    /**
     * The player saved as many items as allowed.
     */
    LIMIT_REACHED,
    /**
     * Saving failed.
     */
    FAILED
}
