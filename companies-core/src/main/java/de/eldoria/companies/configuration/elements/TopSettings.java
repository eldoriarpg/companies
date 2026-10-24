/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import java.time.Duration;

/**
 * How the company ranking is cached, the {@code top} section of the config.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class TopSettings {
    private int cacheSeconds = 30;

    public TopSettings() {}

    /**
     * How long a page of the ranking and the rank of a company are kept on this node. Zero disables the cache.
     *
     * @return cache lifetime
     */
    public Duration cache() {
        return Duration.ofSeconds(Math.max(0, cacheSeconds));
    }
}
