/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

/**
 * What came of {@link de.eldoria.companies.api.AreaApi#withdraw(int, String)}.
 */
public enum WithdrawResult {
    /**
     * The offer is gone.
     */
    WITHDRAWN,
    /**
     * A company has the area; the offer stays until the company gives it back.
     */
    TAKEN,
    /**
     * No offer has this id.
     */
    UNKNOWN
}
