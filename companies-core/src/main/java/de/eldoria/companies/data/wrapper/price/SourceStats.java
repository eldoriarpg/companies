/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.price;

/**
 * What one price source stored over the days that count, on every server together.
 *
 * @param source         id of the source, lower case
 * @param nodes          number of servers with a recent sample of it
 * @param quotes         number of offers in those samples
 * @param quoteMaterials number of materials offered
 * @param trades         number of trades
 * @param tradeMaterials number of materials traded
 */
public record SourceStats(String source, int nodes, long quotes, int quoteMaterials, long trades, int tradeMaterials) {}
