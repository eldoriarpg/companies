/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.schema;

import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.AnnotatedElement;

/**
 * Adds keywords that only the caller knows about to the schemas {@link JsonSchemas} writes, for example annotations
 * of the described module that mark a property as deprecated. Both methods do nothing by default.
 */
public interface SchemaDecorator {
    /**
     * Called once for every definition after it was built.
     *
     * @param type       the described class
     * @param definition its schema, to be changed in place
     */
    default void definition(Class<?> type, ObjectNode definition) {}

    /**
     * Called for every property of a record or class.
     *
     * @param source     the record component or field
     * @param property   its schema, to be changed in place
     */
    default void property(AnnotatedElement source, ObjectNode property) {}
}
