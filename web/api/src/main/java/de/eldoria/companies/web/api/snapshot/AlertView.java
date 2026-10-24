/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

/**
 * A saved search that notifies the company about new matching orders.
 *
 * @param name the alert's name
 * @param enabled whether it notifies
 */
public record AlertView(String name, boolean enabled) {}
