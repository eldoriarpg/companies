/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.snapshot;

import de.eldoria.companies.web.api.snapshot.Snapshot;
import de.eldoria.companies.web.api.update.Patch;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Applies a {@link Patch} to a snapshot held as a JSON tree.
 * <p>
 * The path is dotted, such as {@code company.members}. {@code REPLACE}, and {@code UPSERT} without a key, set the
 * value at the path. With a key, {@code UPSERT} replaces or appends the list entry with that key and {@code REMOVE}
 * drops it; on an object the key names a property. {@code REMOVE} without a key drops the value at the path. A list
 * entry's key is its {@code id}, {@code uuid} or {@code key} property, or the entry itself when it is a plain value.
 * <p>
 * A patch that does not fit the snapshot, such as one into a company the snapshot does not have, or one with an
 * operation this version does not know, is refused; the snapshot is then loaded again.
 */
public final class PatchApplier {
    private static final List<String> KEY_FIELDS = List.of("id", "uuid", "key");
    private static final Set<String> ENVELOPE = Set.of("type", "id", "v", "session", "node", "seq");
    private static final Set<String> ROOTS = Arrays.stream(Snapshot.class.getRecordComponents())
            .map(RecordComponent::getName)
            .filter(name -> !ENVELOPE.contains(name))
            .collect(Collectors.toUnmodifiableSet());

    private PatchApplier() {}

    /**
     * Whether a patch reaches into a company the snapshot does not have: its path is below {@code company.} while the
     * snapshot has no company. Such a patch is never applied; the snapshot is loaded again instead.
     *
     * @param snapshot the snapshot
     * @param patch the patch
     * @return whether the patch must not be applied to this snapshot
     */
    public static boolean foreignCompany(ObjectNode snapshot, Patch patch) {
        String path = patch.path();
        if (path == null || !path.startsWith("company.")) return false;
        return !snapshot.path("company").path("id").isNumber();
    }

    /**
     * @param snapshot a snapshot
     * @return the id of its company as text, empty without one
     */
    public static String company(ObjectNode snapshot) {
        JsonNode id = snapshot.path("company").path("id");
        return id.isValueNode() && !id.isNull() ? id.asString() : "";
    }

    /**
     * @param patch a patch
     * @return whether it changes a part of the snapshot this version knows, such as {@code company}
     */
    public static boolean knownRoot(Patch patch) {
        String path = patch.path();
        return path != null && ROOTS.contains(path.split("\\.", 2)[0]);
    }

    /**
     * @param snapshot the snapshot, changed in place
     * @param patch the change
     * @throws Unapplicable when the patch does not fit the snapshot
     */
    public static void apply(ObjectNode snapshot, Patch patch) throws Unapplicable {
        if (patch.path() == null || patch.path().isBlank() || patch.op() == null) {
            throw new Unapplicable("The patch has no path or operation");
        }
        String[] segments = patch.path().split("\\.");
        ObjectNode parent = snapshot;
        for (int i = 0; i < segments.length - 1; i++) {
            JsonNode child = parent.get(segments[i]);
            if (!(child instanceof ObjectNode object)) throw new Unapplicable("No object at " + segments[i]);
            parent = object;
        }
        String field = segments[segments.length - 1];
        String key = patch.key();
        JsonNode value = patch.value() == null ? JsonNodeFactory.instance.nullNode() : patch.value();
        switch (patch.op()) {
            case REPLACE -> parent.set(field, value);
            case UPSERT -> {
                if (key == null) parent.set(field, value);
                else upsert(parent.get(field), key, value);
            }
            case REMOVE -> {
                if (key == null) parent.remove(field);
                else remove(parent.get(field), key);
            }
            case UNKNOWN -> throw new Unapplicable("Unknown patch operation");
        }
    }

    private static void upsert(@Nullable JsonNode target, String key, JsonNode value) throws Unapplicable {
        switch (target) {
            case ArrayNode list -> {
                int index = indexOf(list, key);
                if (index < 0) list.add(value);
                else list.set(index, value);
            }
            case ObjectNode object -> object.set(key, value);
            case null, default -> throw new Unapplicable("No list or object to upsert into");
        }
    }

    private static void remove(@Nullable JsonNode target, String key) throws Unapplicable {
        switch (target) {
            case ArrayNode list -> {
                int index = indexOf(list, key);
                if (index >= 0) list.remove(index);
            }
            case ObjectNode object -> object.remove(key);
            case null, default -> throw new Unapplicable("No list or object to remove from");
        }
    }

    private static int indexOf(ArrayNode list, String key) {
        for (int i = 0; i < list.size(); i++) {
            if (key.equals(keyOf(list.get(i)))) return i;
        }
        return -1;
    }

    private static @Nullable String keyOf(JsonNode entry) {
        if (entry.isValueNode()) return entry.asString();
        for (String field : KEY_FIELDS) {
            JsonNode value = entry.get(field);
            if (value != null && value.isValueNode()) return value.asString();
        }
        return null;
    }

    /**
     * A patch does not fit the snapshot.
     */
    public static final class Unapplicable extends Exception {
        private static final long serialVersionUID = 1L;

        /**
         * @param message why
         */
        public Unapplicable(String message) {
            super(message);
        }
    }
}
