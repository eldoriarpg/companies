/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.schema;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSchemasTest {
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .changeDefaultVisibility(v -> v.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                    .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE))
            .build();

    enum Mode {
        NORMAL,
        STRETCHED
    }

    enum Op {
        @JsonProperty("upsert")
        UPSERT,
        @JsonProperty("remove")
        REMOVE,
        @JsonEnumDefaultValue
        UNKNOWN
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    sealed interface Message permits Ping, Pong {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({@JsonSubTypes.Type(Joined.class)})
    interface Event {}

    @JsonTypeName("joined")
    record Joined(String name) implements Event {}

    @JsonTypeName("ping")
    record Ping(long time) implements Message {}

    @JsonTypeName("pong")
    record Pong(long time) implements Message {}

    record Dated(
            UUID id,
            LocalDate day,
            Instant at,
            Op op,
            @Marked String old) {}

    @Retention(RetentionPolicy.RUNTIME)
    @interface Marked {}

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record Sparse(String id, List<String> tags) {}

    record PartlySparse(
            String id,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> tags) {}

    record Mob(String id, @Nullable String name, List<String> tags, Map<String, Integer> weights, JsonNode editor) {}

    static class Night {
        private int begin = 13000;
        private Mode mode = Mode.STRETCHED;
        private Set<Mode> allowed = EnumSet.of(Mode.NORMAL);
        private List<String> commands = new ArrayList<>(List.of("say hi"));
        private Inner inner = new Inner();

        @JsonProperty("skip")
        private boolean skippable;

        @JsonIgnore
        private int cache;

        private static final int CONSTANT = 1;
        private transient int scratch;
    }

    static class Inner {
        private @Nullable Instant at;
    }

    static class Base {
        private String base = "b";
    }

    static class Child extends Base {
        private String child = "c";
    }

    private static JsonNode definition(ObjectNode document, String name) {
        return document.path("$defs").path(name);
    }

    @Test
    void recordsListTheirComponentsAndWhatIsRequired() {
        ObjectNode document = JsonSchemas.document(Mob.class, MAPPER);
        assertEquals("#/$defs/Mob", document.path("$ref").asString());
        JsonNode mob = definition(document, "Mob");
        assertEquals("string", mob.at("/properties/id/type").asString());
        assertEquals("array", mob.at("/properties/tags/type").asString());
        assertEquals(
                "integer",
                mob.at("/properties/weights/additionalProperties/type").asString());
        assertTrue(mob.at("/properties/editor").isEmpty());
        List<String> required = new ArrayList<>();
        mob.path("required").forEach(node -> required.add(node.asString()));
        assertEquals(List.of("id", "tags", "weights", "editor"), required);
    }

    @Test
    void recordsLeavingValuesOutRequireNothing() {
        assertFalse(
                definition(JsonSchemas.document(Sparse.class, MAPPER), "Sparse").has("required"));
    }

    @Test
    void componentsLeftOutWhenEmptyAreOptional() {
        JsonNode schema = definition(JsonSchemas.document(PartlySparse.class, MAPPER), "PartlySparse");
        assertEquals("[\"id\"]", schema.get("required").toString());
    }

    @Test
    void sealedInterfacesAreOneOfTheirSubtypesWithTheirTypeName() {
        ObjectNode document = JsonSchemas.document(Message.class, MAPPER);
        JsonNode message = definition(document, "Message");
        assertEquals("#/$defs/Ping", message.at("/oneOf/0/$ref").asString());
        assertEquals("#/$defs/Pong", message.at("/oneOf/1/$ref").asString());
        JsonNode ping = definition(document, "Ping");
        assertEquals("ping", ping.at("/properties/type/const").asString());
        assertEquals("[\"type\",\"time\"]", ping.get("required").toString());
    }

    @Test
    void declaredSubtypesAreOneOfThemWithTheirTypeName() {
        ObjectNode document = JsonSchemas.document(Event.class, MAPPER);
        assertEquals(
                "#/$defs/Joined",
                definition(document, "Event").at("/oneOf/0/$ref").asString());
        JsonNode joined = definition(document, "Joined");
        assertEquals("joined", joined.at("/properties/kind/const").asString());
        assertEquals("[\"kind\",\"name\"]", joined.get("required").toString());
    }

    @Test
    void enumsListTheWrittenNamesWithoutTheFallback() {
        ObjectNode document = JsonSchemas.document(Dated.class, MAPPER);
        assertEquals(
                "[\"upsert\",\"remove\"]",
                definition(document, "Op").get("enum").toString());
    }

    @Test
    void datesAndIdsCarryTheirFormat() {
        JsonNode dated = definition(JsonSchemas.document(Dated.class, MAPPER), "Dated");
        assertEquals("uuid", dated.at("/properties/id/format").asString());
        assertEquals("date", dated.at("/properties/day/format").asString());
        assertEquals("date-time", dated.at("/properties/at/format").asString());
    }

    @Test
    void theDecoratorSeesDefinitionsAndProperties() {
        SchemaDecorator decorator = new SchemaDecorator() {
            @Override
            public void definition(Class<?> type, ObjectNode definition) {
                definition.put("x-class", type.getSimpleName());
            }

            @Override
            public void property(AnnotatedElement source, ObjectNode property) {
                if (source instanceof RecordComponent && source.isAnnotationPresent(Marked.class)) {
                    property.put("deprecated", true);
                }
            }
        };
        JsonNode dated = definition(JsonSchemas.document(Dated.class, MAPPER, decorator), "Dated");
        assertEquals("Dated", dated.path("x-class").asString());
        assertTrue(dated.at("/properties/old/deprecated").asBoolean());
        assertFalse(dated.at("/properties/at").has("deprecated"));
    }

    @Test
    void classesListTheirFieldsWithDefaults() {
        ObjectNode document = JsonSchemas.document(Night.class, MAPPER);
        JsonNode night = definition(document, "Night");
        assertEquals(13000, night.at("/properties/begin/default").asInt());
        assertEquals("STRETCHED", night.at("/properties/mode/default").asString());
        assertEquals("#/$defs/Mode", night.at("/properties/mode/allOf/0/$ref").asString());
        assertEquals("NORMAL", definition(document, "Mode").at("/enum/0").asString());
        assertTrue(night.at("/properties/allowed/uniqueItems").asBoolean());
        assertEquals("say hi", night.at("/properties/commands/default/0").asString());
        assertEquals("#/$defs/Inner", night.at("/properties/inner/allOf/0/$ref").asString());
        assertTrue(night.at("/properties/skip").isObject());
        assertFalse(night.path("properties").has("cache"));
        assertFalse(night.path("properties").has("CONSTANT"));
        assertFalse(night.path("properties").has("scratch"));
        assertFalse(night.has("required"));
        assertEquals(
                "date-time",
                definition(document, "Inner").at("/properties/at/format").asString());
    }

    @Test
    void inheritedFieldsComeFirst() {
        JsonNode child = definition(JsonSchemas.document(Child.class, MAPPER), "Child");
        List<String> names = new ArrayList<>();
        child.path("properties").propertyNames().forEach(names::add);
        assertEquals(List.of("base", "child"), names);
    }
}
