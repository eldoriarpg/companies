/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a part of the protocol as deprecated: a field (record component), a message type (record) or a feature or
 * action name (constant). The schema carries it as {@code deprecated: true} with {@code x-deprecated-since} and
 * {@code x-replacement}.
 * <p>
 * A part is never renamed or changed in place. Its replacement is added next to it, both are sent and read during the
 * deprecation window, and the gateway removes the old part once the window has passed.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.TYPE, ElementType.FIELD})
public @interface DeprecatedSince {
    /**
     * The release date of the first plugin version that sends the replacement, as an ISO-8601 date. The deprecation
     * window starts there.
     *
     * @return the date, e.g. {@code 2027-03-01}
     */
    String value();

    /**
     * What replaces the part: a field name, a message type or a feature name.
     *
     * @return the replacement, empty when the part goes without one
     */
    String replacement() default "";
}
