/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.data.wrapper.stats.MemberStats;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What the members of a company contributed, all time and in the last 30 days. A member without payouts contributed
 * nothing.
 *
 * @param allTime    contributions since the company was founded, by member
 * @param last30Days contributions of the last 30 days, by member
 */
public record MemberContributions(Map<UUID, MemberStats> allTime, Map<UUID, MemberStats> last30Days) {
    /**
     * The ranges the member list can show.
     */
    public static final List<TimeRange> RANGES = List.of(TimeRange.ALL, TimeRange.LAST_30_DAYS);

    public MemberContributions {
        allTime = Map.copyOf(allTime);
        last30Days = Map.copyOf(last30Days);
    }

    /**
     * @param allTime    member statistics since the company was founded
     * @param last30Days member statistics of the last 30 days
     * @return the contributions
     */
    public static MemberContributions of(List<MemberStats> allTime, List<MemberStats> last30Days) {
        return new MemberContributions(byWorker(allTime), byWorker(last30Days));
    }

    /**
     * @param member id of a member
     * @param range  {@link TimeRange#ALL} or {@link TimeRange#LAST_30_DAYS}
     * @return what the member contributed in the range
     */
    public MemberStats of(UUID member, TimeRange range) {
        var stats = range == TimeRange.ALL ? allTime : last30Days;
        return stats.getOrDefault(member, new MemberStats(member, 0, 0, 0));
    }

    private static Map<UUID, MemberStats> byWorker(List<MemberStats> stats) {
        return stats.stream()
                .collect(Collectors.toMap(MemberStats::worker, Function.identity(), (left, right) -> left));
    }
}
