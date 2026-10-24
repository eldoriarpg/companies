/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import de.eldoria.companies.web.api.link.UnknownMessage;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replays the recorded messages of every protocol version under {@code src/test/resources/messages/v<N>/}. Each one
 * must validate against the committed schema of its version and read with the current records; read and written
 * again, it must still validate against the current schema and keep every value it carried.
 * <p>
 * Recorded messages are never changed once released, only added. They are what the gateway replays for every
 * released plugin version.
 */
class RecordedMessagesTest {
    private static final Path MESSAGES = Path.of("src/test/resources/messages");
    private static final JsonMapper MAPPER = Protocol.mapper();

    @TestFactory
    Stream<DynamicTest> recordedMessagesValidateAndRead() {
        return versions().flatMap(version -> {
            JsonNode schema = schema(version);
            return files(MESSAGES.resolve("v" + version))
                    .map(file -> DynamicTest.dynamicTest(
                            "v" + version + "/" + file.getFileName(), () -> replay(schema, file)));
        });
    }

    @Test
    void everyMessageTypeIsRecordedForTheCurrentVersion() {
        Set<String> recorded = files(MESSAGES.resolve("v" + Protocol.VERSION))
                .map(file -> read(file).path("type").asString())
                .collect(Collectors.toSet());
        List<String> missing = new ArrayList<>();
        for (String type : messageTypes()) if (!recorded.contains(type)) missing.add(type);
        assertTrue(missing.isEmpty(), "No recorded message of type " + missing);
    }

    @Test
    void theSchemaRefusesAMessageWithoutARequiredField() {
        ObjectNode hello = (ObjectNode) read(MESSAGES.resolve("v1/hello.json"));
        hello.remove("plugin");
        assertFalse(validator(schema(1), "hello").validate(hello).isEmpty());
    }

    @Test
    void everyVersionUpToTheCurrentHasRecordedMessages() {
        assertEquals(Protocol.VERSION, versions().count(), "Every protocol version needs a messages/v<N> directory");
    }

    private static void replay(JsonNode schema, Path file) {
        JsonNode recorded = read(file);
        String type = recorded.path("type").asString();
        assertValid(schema, type, recorded, "recorded");
        Message message = MAPPER.treeToValue(recorded, Message.class);
        assertFalse(message instanceof UnknownMessage, "The type " + type + " is unknown to the current records");
        JsonNode written = MAPPER.valueToTree(message);
        assertValid(schema(Protocol.VERSION), type, written, "written again");
        assertContained(written, recorded, "");
    }

    private static void assertValid(JsonNode document, String type, JsonNode message, String what) {
        List<Error> errors = validator(document, type).validate(message);
        assertTrue(errors.isEmpty(), "The " + what + " " + type + " does not match the schema: " + errors);
    }

    private static void assertContained(JsonNode written, JsonNode recorded, String path) {
        if (written.isNumber() && recorded.isNumber()) {
            assertEquals(recorded.decimalValue().compareTo(written.decimalValue()), 0, "Value changed at " + path);
            return;
        }
        if (written.isObject()) {
            assertTrue(recorded.isObject(), "Not an object at " + path);
            for (Map.Entry<String, JsonNode> entry : written.properties()) {
                String child = path + "/" + entry.getKey();
                assertTrue(recorded.has(entry.getKey()), "Written but not recorded: " + child);
                assertContained(entry.getValue(), recorded.get(entry.getKey()), child);
            }
            return;
        }
        if (written.isArray()) {
            assertEquals(recorded.size(), written.size(), "Entries changed at " + path);
            for (int i = 0; i < written.size(); i++) assertContained(written.get(i), recorded.get(i), path + "/" + i);
            return;
        }
        assertEquals(recorded, written, "Value changed at " + path);
    }

    private static Schema validator(JsonNode document, String type) {
        ObjectNode single = (ObjectNode) document.deepCopy();
        single.remove("oneOf");
        single.put("$ref", "#/$defs/" + definition(document, type));
        return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(single);
    }

    private static String definition(JsonNode document, String type) {
        for (Map.Entry<String, JsonNode> entry : document.path("$defs").properties()) {
            if (type.equals(entry.getValue().at("/properties/type/const").asString())) return entry.getKey();
        }
        throw new AssertionError("The schema has no message of type " + type);
    }

    private static List<String> messageTypes() {
        return Arrays.stream(Message.class.getAnnotation(JsonSubTypes.class).value())
                .map(type -> type.value().getAnnotation(JsonTypeName.class).value())
                .toList();
    }

    private static Stream<Integer> versions() {
        Map<Integer, Path> versions = new TreeMap<>();
        files(MESSAGES)
                .forEach(dir -> versions.put(
                        Integer.parseInt(dir.getFileName().toString().substring(1)), dir));
        return versions.keySet().stream();
    }

    private static JsonNode schema(int version) {
        try (InputStream in =
                RecordedMessagesTest.class.getClassLoader().getResourceAsStream(Protocol.schemaPath(version))) {
            assertNotNull(in, "No schema for protocol version " + version);
            return MAPPER.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode read(Path file) {
        return MAPPER.readTree(file.toFile());
    }

    private static Stream<Path> files(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.sorted().toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
