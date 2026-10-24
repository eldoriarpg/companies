/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.handshake;

/**
 * The limits a node applies to requests.
 *
 * @param maxPageSize the most entries the node returns in one page
 */
public record Limits(int maxPageSize) {}
