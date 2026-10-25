/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.nodes.NodeMessage.SettingsChanged;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * How a secondary follows the shared files: it takes a revision the primary announces over the bus, and it looks for
 * new revisions every {@value #POLL_SECONDS} seconds, or only every {@value #REALTIME_POLL_TICKS}th time while the bus
 * delivers messages at once, as a safety net. One revision is taken after the other. A new {@code config.yml} is
 * applied like a reload; the other files are read where they are used. A revision that cannot be taken is tried again
 * on every look and reported once.
 */
public final class SettingsSync {
    /**
     * Seconds between two looks for new revisions.
     */
    public static final int POLL_SECONDS = 30;

    /**
     * With a realtime bus, only every this many looks reads the database.
     */
    public static final int REALTIME_POLL_TICKS = 10;

    private final Configuration configuration;
    private final ANodeData nodeData;
    private final Executor serial;
    private final Executor mainThread;
    private final Runnable reloaded;
    private final BooleanSupplier realtime;
    private final Logger logger;
    private final AtomicInteger ticks = new AtomicInteger();
    private final Map<String, Integer> failed = new ConcurrentHashMap<>();

    /**
     * @param configuration the configuration of this node
     * @param nodeData      the node repository holding the revisions
     * @param serial        takes one revision after the other
     * @param mainThread    runs the reload hooks
     * @param reloaded      applies a new {@code config.yml} like a reload
     * @param realtime      whether the bus delivers messages at once now
     * @param logger        reports what was taken and failures
     */
    public SettingsSync(
            Configuration configuration,
            ANodeData nodeData,
            Executor serial,
            Executor mainThread,
            Runnable reloaded,
            BooleanSupplier realtime,
            Logger logger) {
        this.configuration = configuration;
        this.nodeData = nodeData;
        this.serial = serial;
        this.mainThread = mainThread;
        this.reloaded = reloaded;
        this.realtime = realtime;
        this.logger = logger;
    }

    /**
     * Takes a revision the primary announced.
     *
     * @param changed the announcement
     */
    public void changed(SettingsChanged changed) {
        serial.execute(() -> apply(changed.path(), changed.revision()));
    }

    /**
     * Looks for revisions this node has not applied yet. Called every {@value #POLL_SECONDS} seconds.
     */
    public void poll() {
        if (realtime.getAsBoolean() && ticks.incrementAndGet() % REALTIME_POLL_TICKS != 0) return;
        pollNow();
    }

    /**
     * Looks for revisions this node has not applied yet at once, as when the bus connected again and may have missed an
     * announcement meanwhile.
     */
    public void pollNow() {
        serial.execute(() -> {
            try {
                nodeData.settingsRevisions().forEach(this::apply);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Could not look for new revisions of the shared files.", e);
            }
        });
    }

    private void apply(String path, int revision) {
        try {
            if (!configuration.takeIfNewer(nodeData, path, revision)) return;
            logger.info("Took revision " + revision + " of " + path + " from the primary node.");
            if (path.equals(Configuration.CONFIG_YML.path().toString())) mainThread.execute(reloaded);
        } catch (RuntimeException e) {
            var known = failed.put(path, revision);
            var level = known != null && known == revision ? Level.FINE : Level.SEVERE;
            logger.log(level, "Could not take revision " + revision + " of " + path + "; it is tried again.", e);
        }
    }
}
