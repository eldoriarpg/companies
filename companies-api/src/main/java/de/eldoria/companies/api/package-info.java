/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
/**
 * The entry point of the Companies API: {@link de.eldoria.companies.api.CompaniesApi#get()} and the parts it hands
 * out for companies, orders, players, rankings, server events and areas. Reads and writes are asynchronous; writes
 * fail with {@link de.eldoria.companies.api.ApiNotReadyException} until Companies is ready.
 */
package de.eldoria.companies.api;
