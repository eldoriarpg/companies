/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.schema;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Writes JSON Schemas (draft 2020-12, also OpenAPI 3.1) of the classes Jackson reads and writes: records by their
 * components, interfaces with {@link JsonSubTypes} or sealed ones as one of their subtypes, other classes by their
 * fields.
 * <p>
 * Every class of the {@code de.eldoria} packages and every enum becomes a definition, referenced by its simple name
 * (qualified by its package or outer class when two share one). Enums list the values the mapper writes, leaving out
 * the {@link JsonEnumDefaultValue} fallback, which only stands in
 * for values the reader does not know. Collections become arrays (sets with unique items); maps become objects whose
 * values follow the value type; {@link JsonNode} and {@link Object} accept anything; {@link Instant} is a date-time,
 * {@link LocalDate} a date and {@link UUID} a uuid string. Classes of other libraries that are not enums are opaque
 * objects.
 * <p>
 * Record components are required unless nullable or their record or the component itself leaves values out
 * ({@link JsonInclude} other than always). A record whose interface names its subtypes by a {@link JsonTypeInfo}
 * property gets that property as a required constant holding its {@link JsonTypeName}. Fields of classes are optional
 * and carry the value a new instance has as their default. Names follow {@link JsonProperty}; {@link JsonIgnore}d,
 * static and transient fields are left out. A {@link SchemaDecorator} may add keywords to definitions and properties.
 */
public final class JsonSchemas {
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private final String referencePrefix;
    private final ObjectMapper mapper;
    private final SchemaDecorator decorator;
    private final Map<String, ObjectNode> definitions = new TreeMap<>();
    private final Map<String, Class<?>> owners = new TreeMap<>();

    /**
     * Creates a writer whose definitions are collected for one document.
     *
     * @param referencePrefix where definitions are referenced from, {@code #/$defs/} or {@code #/components/schemas/}
     * @param mapper          writes the defaults of class fields
     * @param decorator       adds keywords to definitions and properties
     */
    public JsonSchemas(String referencePrefix, ObjectMapper mapper, SchemaDecorator decorator) {
        this.referencePrefix = referencePrefix;
        this.mapper = mapper;
        this.decorator = decorator;
    }

    /**
     * Creates a writer that adds nothing beyond what the classes say.
     *
     * @param referencePrefix where definitions are referenced from, {@code #/$defs/} or {@code #/components/schemas/}
     * @param mapper          writes the defaults of class fields
     */
    public JsonSchemas(String referencePrefix, ObjectMapper mapper) {
        this(referencePrefix, mapper, new SchemaDecorator() {});
    }

    /**
     * Describes a class as a complete document.
     *
     * @param root      the class the document describes
     * @param mapper    writes the defaults of class fields
     * @param decorator adds keywords to definitions and properties
     * @return a schema document: the root's schema with every definition it needs under {@code $defs}
     */
    public static ObjectNode document(Class<?> root, ObjectMapper mapper, SchemaDecorator decorator) {
        JsonSchemas schemas = new JsonSchemas("#/$defs/", mapper, decorator);
        ObjectNode document = NODES.objectNode();
        document.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        document.put("title", root.getSimpleName());
        document.setAll(schemas.schema(root));
        document.set("$defs", schemas.definitions());
        return document;
    }

    /**
     * Describes a class as a complete document without decoration.
     *
     * @param root   the class the document describes
     * @param mapper writes the defaults of class fields
     * @return a schema document: the root's schema with every definition it needs under {@code $defs}
     */
    public static ObjectNode document(Class<?> root, ObjectMapper mapper) {
        return document(root, mapper, new SchemaDecorator() {});
    }

    /**
     * Describes one type.
     *
     * @param type a type to describe; its definitions are collected
     * @return its schema, a reference for classes with a definition
     */
    public ObjectNode schema(Type type) {
        return schema(type, false);
    }

    /**
     * Lists the definitions of every type described so far.
     *
     * @return every definition collected so far, by name
     */
    public ObjectNode definitions() {
        ObjectNode all = NODES.objectNode();
        definitions.forEach(all::set);
        return all;
    }

    private ObjectNode schema(Type type, boolean unique) {
        if (type instanceof ParameterizedType parameterized) return parameterized(parameterized);
        if (type instanceof GenericArrayType array) return array(schema(array.getGenericComponentType()), false);
        if (type instanceof WildcardType wildcard) return schema(wildcard.getUpperBounds()[0]);
        if (!(type instanceof Class<?> clazz)) return NODES.objectNode();
        if (clazz.isArray()) return array(schema(clazz.getComponentType()), false);
        ObjectNode simple = simple(clazz);
        if (simple != null) return simple;
        if (clazz.isEnum()) return reference(clazz);
        if (Collection.class.isAssignableFrom(clazz))
            return array(NODES.objectNode(), Set.class.isAssignableFrom(clazz) || unique);
        if (Map.class.isAssignableFrom(clazz)) return NODES.objectNode().put("type", "object");
        if (!clazz.getName().startsWith("de.eldoria."))
            return NODES.objectNode().put("type", "object");
        return reference(clazz);
    }

    private ObjectNode parameterized(ParameterizedType type) {
        Class<?> raw = (Class<?>) type.getRawType();
        Type[] arguments = type.getActualTypeArguments();
        if (Collection.class.isAssignableFrom(raw)) {
            return array(schema(arguments[0]), Set.class.isAssignableFrom(raw));
        }
        if (Map.class.isAssignableFrom(raw)) {
            ObjectNode map = NODES.objectNode().put("type", "object");
            map.set("additionalProperties", schema(arguments[1]));
            return map;
        }
        return schema(raw);
    }

    private static ObjectNode array(ObjectNode items, boolean unique) {
        ObjectNode array = NODES.objectNode().put("type", "array");
        array.set("items", items);
        if (unique) array.put("uniqueItems", true);
        return array;
    }

    private static @Nullable ObjectNode simple(Class<?> clazz) {
        if (clazz == String.class || clazz == Character.class || clazz == char.class) {
            return NODES.objectNode().put("type", "string");
        }
        if (clazz == UUID.class) return NODES.objectNode().put("type", "string").put("format", "uuid");
        if (clazz == boolean.class || clazz == Boolean.class)
            return NODES.objectNode().put("type", "boolean");
        if (clazz == int.class
                || clazz == Integer.class
                || clazz == long.class
                || clazz == Long.class
                || clazz == short.class
                || clazz == Short.class
                || clazz == byte.class
                || clazz == Byte.class) {
            return NODES.objectNode().put("type", "integer");
        }
        if (clazz == double.class
                || clazz == Double.class
                || clazz == float.class
                || clazz == Float.class
                || Number.class.isAssignableFrom(clazz)) {
            return NODES.objectNode().put("type", "number");
        }
        if (clazz == Instant.class)
            return NODES.objectNode().put("type", "string").put("format", "date-time");
        if (clazz == LocalDate.class)
            return NODES.objectNode().put("type", "string").put("format", "date");
        if (clazz == Object.class || JsonNode.class.isAssignableFrom(clazz)) return NODES.objectNode();
        return null;
    }

    private ObjectNode enumeration(Class<?> clazz) {
        ObjectNode schema = NODES.objectNode().put("type", "string");
        ArrayNode values = schema.putArray("enum");
        for (Object value : clazz.getEnumConstants()) {
            Field constant = constant(clazz, (Enum<?>) value);
            if (constant.isAnnotationPresent(JsonEnumDefaultValue.class)) continue;
            values.add(mapper.valueToTree(value).asString());
        }
        return schema;
    }

    private static Field constant(Class<?> clazz, Enum<?> value) {
        try {
            return clazz.getField(value.name());
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    private ObjectNode reference(Class<?> clazz) {
        String name = name(clazz);
        if (!definitions.containsKey(name)) {
            definitions.put(name, NODES.objectNode());
            ObjectNode definition = clazz.isEnum()
                    ? enumeration(clazz)
                    : clazz.isRecord() ? record(clazz) : hasSubtypes(clazz) ? subtypes(clazz) : fields(clazz);
            decorator.definition(clazz, definition);
            definitions.put(name, definition);
        }
        return NODES.objectNode().put("$ref", referencePrefix + name);
    }

    private String name(Class<?> clazz) {
        String name = clazz.getSimpleName();
        Class<?> owner = owners.putIfAbsent(name, clazz);
        if (owner == null || owner == clazz) return name;
        String qualified = clazz.getName()
                .substring(clazz.getName().lastIndexOf('.', clazz.getName().lastIndexOf('.') - 1) + 1)
                .replace('.', '_')
                .replace('$', '_');
        owners.putIfAbsent(qualified, clazz);
        return qualified;
    }

    private ObjectNode subtypes(Class<?> clazz) {
        JsonSubTypes declared = clazz.getAnnotation(JsonSubTypes.class);
        Class<?>[] subtypes = declared == null
                ? clazz.getPermittedSubclasses()
                : Arrays.stream(declared.value()).map(JsonSubTypes.Type::value).toArray(Class<?>[]::new);
        ObjectNode schema = NODES.objectNode();
        ArrayNode oneOf = schema.putArray("oneOf");
        for (Class<?> subtype : subtypes) oneOf.add(schema(subtype));
        return schema;
    }

    private static boolean hasSubtypes(Class<?> clazz) {
        return clazz.isSealed() || clazz.isAnnotationPresent(JsonSubTypes.class);
    }

    private ObjectNode record(Class<?> clazz) {
        ObjectNode schema = NODES.objectNode().put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        List<String> required = new ArrayList<>();
        discriminator(clazz, properties, required);
        boolean omitsEmpty = omitsValues(clazz);
        for (RecordComponent component : clazz.getRecordComponents()) {
            if (ignored(component)) continue;
            String property = propertyName(component, component.getName());
            ObjectNode propertySchema = schema(component.getGenericType());
            decorator.property(component, propertySchema);
            properties.set(property, propertySchema);
            boolean omitted = omitsEmpty || omits(component.getAccessor().getAnnotation(JsonInclude.class));
            if (!omitted && !nullable(component, component.getAnnotatedType())) required.add(property);
        }
        if (!required.isEmpty()) required.forEach(schema.putArray("required")::add);
        return schema;
    }

    private static void discriminator(Class<?> clazz, ObjectNode properties, List<String> required) {
        JsonTypeName typeName = clazz.getAnnotation(JsonTypeName.class);
        if (typeName == null) return;
        for (Class<?> supertype : clazz.getInterfaces()) {
            JsonTypeInfo info = supertype.getAnnotation(JsonTypeInfo.class);
            if (info == null || info.use() != JsonTypeInfo.Id.NAME) continue;
            String property = info.property().isEmpty() ? "@type" : info.property();
            properties.putObject(property).put("type", "string").put("const", typeName.value());
            required.add(property);
            return;
        }
    }

    private ObjectNode fields(Class<?> clazz) {
        ObjectNode schema = NODES.objectNode().put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        Object instance = instantiate(clazz);
        for (Field field : fields(clazz, new ArrayList<>())) {
            if (ignored(field)) continue;
            ObjectNode property = schema(field.getGenericType());
            JsonNode value = defaultValue(field, instance);
            if (value != null && !value.isNull()) {
                if (property.has("$ref")) {
                    ObjectNode wrapped = NODES.objectNode();
                    wrapped.putArray("allOf").add(property);
                    property = wrapped;
                }
                property.set("default", value);
            }
            decorator.property(field, property);
            properties.set(propertyName(field, field.getName()), property);
        }
        return schema;
    }

    private static List<Field> fields(Class<?> clazz, List<Field> collected) {
        if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class)
            fields(clazz.getSuperclass(), collected);
        collected.addAll(Arrays.asList(clazz.getDeclaredFields()));
        return collected;
    }

    private static boolean omitsValues(Class<?> clazz) {
        return omits(clazz.getAnnotation(JsonInclude.class));
    }

    private static boolean omits(@Nullable JsonInclude include) {
        return include != null
                && include.value() != JsonInclude.Include.ALWAYS
                && include.value() != JsonInclude.Include.USE_DEFAULTS;
    }

    private static boolean ignored(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isStatic(modifiers)
                || Modifier.isTransient(modifiers)
                || field.isSynthetic()
                || field.isAnnotationPresent(JsonIgnore.class);
    }

    private static boolean ignored(RecordComponent component) {
        return component.isAnnotationPresent(JsonIgnore.class);
    }

    private static String propertyName(AnnotatedElement element, String fallback) {
        JsonProperty property = element.getAnnotation(JsonProperty.class);
        return property == null || property.value().isEmpty() ? fallback : property.value();
    }

    private static boolean nullable(AnnotatedElement element, AnnotatedType type) {
        return Arrays.stream(element.getAnnotations())
                        .anyMatch(a -> a.annotationType().getSimpleName().equals("Nullable"))
                || Arrays.stream(type.getAnnotations())
                        .anyMatch(a -> a.annotationType().getSimpleName().equals("Nullable"));
    }

    private static @Nullable Object instantiate(Class<?> clazz) {
        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private @Nullable JsonNode defaultValue(Field field, @Nullable Object instance) {
        if (instance == null) return null;
        try {
            field.setAccessible(true);
            Object value = field.get(instance);
            return value == null ? null : mapper.valueToTree(value);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
