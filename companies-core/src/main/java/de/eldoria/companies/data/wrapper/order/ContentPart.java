/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

import de.eldoria.companies.components.order.IContentPart;

import java.util.UUID;

public record ContentPart(UUID worker, int amount) implements IContentPart {
}
