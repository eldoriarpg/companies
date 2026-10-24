/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

import java.util.List;
import java.util.UUID;

/**
 * A member of a company.
 *
 * @param uuid the member's id
 * @param name the member's name
 * @param permissions the member's permissions
 * @param stats the member's statistics
 */
public record MemberView(UUID uuid, String name, List<MemberPermission> permissions, MemberStatsView stats) {}
