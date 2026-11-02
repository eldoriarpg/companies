/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

import java.time.Instant;

/**
 * The event offer an order is a chunk of.
 *
 * @param id the id of the offer
 * @param name the name of the offer
 * @param ends when the offer ends
 * @param state the state of the offer in lower case
 */
public record OfferBadgeView(int id, String name, Instant ends, String state) {}
