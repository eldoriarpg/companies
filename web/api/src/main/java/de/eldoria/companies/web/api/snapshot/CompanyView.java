/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * The player's company.
 *
 * @param id the company id
 * @param name its name
 * @param tag its tag
 * @param color its colour as a hex string
 * @param description its description as plain text
 * @param joinMode how players join
 * @param founded when it was founded
 * @param level its level and progress
 * @param members its members with permissions and statistics
 * @param treasury the treasury, only for players with the treasury permission
 * @param orders the orders it claimed, with progress
 * @param alerts its alerts
 * @param colorName its colour as the palette entry the profile stores
 * @param palette the colours of the server's palette a company may choose from
 * @param areas its areas, none from a peer that sends no areas
 * @param home its home, none when it has set none
 */
public record CompanyView(
        int id,
        String name,
        @Nullable String tag,
        @Nullable String color,
        @Nullable String description,
        JoinMode joinMode,
        Instant founded,
        LevelView level,
        List<MemberView> members,
        @Nullable TreasuryView treasury,
        List<ClaimedOrderView> orders,
        List<AlertView> alerts,
        @Nullable String colorName,
        @Nullable List<PaletteColor> palette,
        @Nullable List<AreaView> areas,
        @Nullable HomeView home) {}
