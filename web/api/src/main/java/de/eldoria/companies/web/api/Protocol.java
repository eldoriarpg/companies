/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The protocol version and the mapper both sides read and write messages with.
 */
public final class Protocol {
    /**
     * The protocol version of the envelope and the session messages. It changes only when that core changes
     * incompatibly; payloads grow without it. The schema of each version is at {@link #schemaPath(int)}.
     */
    public static final int VERSION = 1;

    private Protocol() {}

    /**
     * Where the JSON Schema of a protocol version lies in this module's resources.
     *
     * @param version the protocol version
     * @return the resource path of its schema
     */
    public static String schemaPath(int version) {
        return "schema/v" + version + "/protocol.schema.json";
    }

    /**
     * Creates the mapper for messages. It reads leniently, as the compatibility rules require: unknown fields are
     * ignored, unknown enum values read as their {@code UNKNOWN} constant, and a missing primitive reads as its
     * default. It leaves out null values and writes times as ISO-8601 strings.
     *
     * @return a new mapper
     */
    public static JsonMapper mapper() {
        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
    }
}
