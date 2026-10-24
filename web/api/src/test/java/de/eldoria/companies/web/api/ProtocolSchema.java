/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api;

import de.eldoria.companies.schema.JsonSchemas;
import de.eldoria.companies.schema.SchemaDecorator;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.nio.file.Path;

/**
 * The JSON Schema of the current protocol version: every {@link Message} as one of its definitions, with the feature
 * and action names of this version as {@code x-features} and {@code x-actions}. Parts marked
 * {@link DeprecatedSince} carry {@code deprecated}, {@code x-deprecated-since} and {@code x-replacement}.
 */
final class ProtocolSchema {
    /**
     * Where the schema of the current version is committed, relative to this module.
     */
    static final Path PATH = Path.of("src/main/resources").resolve(Protocol.schemaPath(Protocol.VERSION));

    private ProtocolSchema() {}

    /**
     * Builds the schema document of the current protocol version.
     *
     * @return the document
     */
    static ObjectNode document() {
        JsonMapper mapper = Protocol.mapper();
        ObjectNode document = JsonSchemas.document(Message.class, mapper, new Deprecations());
        document.put("title", "Companies dashboard protocol v" + Protocol.VERSION);
        document.put("x-protocol", Protocol.VERSION);
        ObjectNode features = document.putObject("x-features");
        for (String feature : Features.ALL) features.set(feature, describe(Features.class, feature, mapper));
        ObjectNode actions = document.putObject("x-actions");
        for (String action : Actions.ALL) {
            ObjectNode described = describe(Actions.class, action, mapper);
            described.put("feature", Actions.feature(action));
            actions.set(action, described);
        }
        return document;
    }

    /**
     * Writes the schema document as committed: indented, with a final line break.
     *
     * @return the file's content
     */
    static String generate() {
        return Protocol.mapper()
                        .rebuild()
                        .enable(SerializationFeature.INDENT_OUTPUT)
                        .build()
                        .writeValueAsString(document())
                + "\n";
    }

    private static ObjectNode describe(Class<?> constants, String value, JsonMapper mapper) {
        ObjectNode described = mapper.createObjectNode();
        for (Field field : constants.getFields()) {
            if (field.getType() != String.class || !value.equals(constant(field))) continue;
            Deprecations.mark(field, described);
        }
        return described;
    }

    private static Object constant(Field field) {
        try {
            return field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Carries {@link DeprecatedSince} into the schema.
     */
    static final class Deprecations implements SchemaDecorator {
        @Override
        public void definition(Class<?> type, ObjectNode definition) {
            mark(type, definition);
        }

        @Override
        public void property(AnnotatedElement source, ObjectNode property) {
            mark(source, property);
        }

        static void mark(AnnotatedElement source, ObjectNode schema) {
            DeprecatedSince deprecated = source.getAnnotation(DeprecatedSince.class);
            if (deprecated == null) return;
            schema.put("deprecated", true);
            schema.put("x-deprecated-since", deprecated.value());
            if (!deprecated.replacement().isEmpty()) schema.put("x-replacement", deprecated.replacement());
        }
    }
}
