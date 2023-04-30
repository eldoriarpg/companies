/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

import java.util.UUID;

public interface IContentPart {
    UUID worker();

    int amount();
}
