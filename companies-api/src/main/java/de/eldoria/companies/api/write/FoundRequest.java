/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

import java.util.Objects;
import java.util.UUID;

/**
 * A company to found through {@link de.eldoria.companies.api.CompanyApi#found(FoundRequest)}, with the same checks as
 * {@code /company create}.
 *
 * @param owner  the player who founds the company and owns it; they need not be online
 * @param name   the name of the company
 * @param charge whether the owner pays the founding price; a founding without charge is logged, and a
 *               {@link de.eldoria.companies.events.company.PreCompanyCreateEvent} fired for it names a price of 0
 */
public record FoundRequest(UUID owner, String name, boolean charge) {
    /**
     * Validates the request.
     */
    public FoundRequest {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
    }
}
