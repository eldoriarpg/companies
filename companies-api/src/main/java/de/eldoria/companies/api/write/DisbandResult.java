/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

/**
 * What came of {@link de.eldoria.companies.api.CompanyApi#disband(int, java.util.UUID)}.
 */
public enum DisbandResult {
    /**
     * The company is gone; its treasury and what the server paid back for its areas went to the owner.
     */
    DISBANDED,
    /**
     * The actor does not own the company, or no company has the id.
     */
    NOT_OWNER,
    /**
     * The company could not be deleted; its treasury was given back, but the orders it had taken are released anyway.
     */
    FAILED
}
