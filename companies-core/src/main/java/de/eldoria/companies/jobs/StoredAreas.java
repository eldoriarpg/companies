/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import com.google.common.cache.CacheBuilder;
import de.eldoria.companies.configuration.elements.jobs.JobSettings;
import de.eldoria.companies.data.repository.AJobData;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.StoredArea;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * The areas players mark and store for their area jobs, so no WorldEdit is needed to post one. Two corners are marked
 * in game, with a command or the area tool; the box between them is the player's marked area, which the area job
 * commands use, and which may be stored under a name. Stored areas are kept in the database with the server whose
 * world holds them, so the dashboard can post jobs on them there. Marks are kept in memory for half an hour.
 * The methods that store or read areas block; call them off the main thread.
 */
public final class StoredAreas {
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final long MARK_MINUTES = 30;

    private final AJobData jobs;
    private final Supplier<JobSettings> settings;
    private final Supplier<LocalDateTime> now;
    private final Map<UUID, Marks> marks = CacheBuilder.newBuilder()
            .expireAfterAccess(MARK_MINUTES, TimeUnit.MINUTES)
            .<UUID, Marks>build()
            .asMap();

    /**
     * @param jobs     job repository, which keeps the areas
     * @param settings the settings of jobs
     * @param now      the current time
     */
    public StoredAreas(AJobData jobs, Supplier<JobSettings> settings, Supplier<LocalDateTime> now) {
        this.jobs = jobs;
        this.settings = settings;
        this.now = now;
    }

    /**
     * A block a corner was marked at.
     *
     * @param world name of its world
     * @param x     its x
     * @param y     its y
     * @param z     its z
     */
    public record Corner(String world, int x, int y, int z) {}

    /**
     * The corners a player marked.
     *
     * @param first  corner 1, null while unmarked
     * @param second corner 2, null while unmarked
     */
    private record Marks(@Nullable Corner first, @Nullable Corner second) {
        private Optional<JobArea> area() {
            if (first == null || second == null || !first.world().equals(second.world())) return Optional.empty();
            return Optional.of(
                    new JobArea(first.world(), first.x(), first.y(), first.z(), second.x(), second.y(), second.z()));
        }
    }

    /**
     * Why an area was not stored.
     */
    public enum Refusal {
        /**
         * The name is not 1 to 32 lower case letters, digits, dashes or underscores.
         */
        NAME,
        /**
         * The player keeps as many areas as allowed.
         */
        LIMIT
    }

    /**
     * Marks one corner of the player's area.
     *
     * @param player the player
     * @param second true for corner 2, false for corner 1
     * @param corner the block marked
     * @return the area both corners span now, empty while one is missing or they lie in different worlds
     */
    public Optional<JobArea> mark(UUID player, boolean second, Corner corner) {
        var marked = marks.compute(player, (id, old) -> {
            var first = old == null ? null : old.first();
            var other = old == null ? null : old.second();
            return second ? new Marks(first, corner) : new Marks(corner, other);
        });
        return marked.area();
    }

    /**
     * Marks both corners of an area, so the area job commands use it.
     *
     * @param player the player
     * @param area   the area
     */
    public void mark(UUID player, JobArea area) {
        marks.put(
                player,
                new Marks(
                        new Corner(area.world(), area.minX(), area.minY(), area.minZ()),
                        new Corner(area.world(), area.maxX(), area.maxY(), area.maxZ())));
    }

    /**
     * Forgets the corners a player marked once a job was posted on them, so a later WorldEdit selection is not
     * overruled by an old mark.
     *
     * @param player the player
     * @param area   the area the job was posted on; marks of another area stay
     */
    public void used(UUID player, JobArea area) {
        marks.computeIfPresent(
                player, (id, old) -> old.area().filter(area::equals).isPresent() ? null : old);
    }

    /**
     * @param player a player
     * @return the area between the corners the player marked, empty while one is missing
     */
    public Optional<JobArea> marked(UUID player) {
        var found = marks.get(player);
        return found == null ? Optional.empty() : found.area();
    }

    /**
     * Stores an area under a name, replacing one of the same name.
     *
     * @param owner the player
     * @param name  the name, compared ignoring case
     * @param node  the server whose world holds the area
     * @param area  the area
     * @return the area as stored, or why it was not
     */
    public Stored save(UUID owner, String name, UUID node, JobArea area) {
        var key = key(name);
        if (!NAME.matcher(key).matches()) return new Stored.Refused(Refusal.NAME);
        var kept = jobs.areas(owner);
        var replaces = kept.stream().anyMatch(stored -> stored.name().equals(key));
        if (!replaces && kept.size() >= settings.get().maxAreas()) return new Stored.Refused(Refusal.LIMIT);
        var stored = new StoredArea(key, node, area);
        jobs.saveArea(owner, stored, now.get());
        return new Stored.Done(stored);
    }

    /**
     * What came of storing an area.
     */
    public sealed interface Stored {
        /**
         * The area was stored.
         *
         * @param area the stored area
         */
        record Done(StoredArea area) implements Stored {}

        /**
         * Nothing was stored.
         *
         * @param refusal why
         */
        record Refused(Refusal refusal) implements Stored {}
    }

    /**
     * @param owner a player
     * @return the player's stored areas, sorted by name
     */
    public List<StoredArea> areas(UUID owner) {
        return jobs.areas(owner);
    }

    /**
     * @param owner a player
     * @param name  the name of one of the player's areas, compared ignoring case
     * @return the area, empty when the player has none of that name
     */
    public Optional<StoredArea> area(UUID owner, String name) {
        return jobs.area(owner, key(name));
    }

    /**
     * @param owner a player
     * @param name  the name of one of the player's areas, compared ignoring case
     * @return true when it existed and is gone
     */
    public boolean delete(UUID owner, String name) {
        return jobs.deleteArea(owner, key(name));
    }

    /**
     * @return the most areas a player may keep
     */
    public int max() {
        return settings.get().maxAreas();
    }

    private static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
