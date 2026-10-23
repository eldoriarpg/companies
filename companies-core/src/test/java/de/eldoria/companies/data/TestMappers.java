/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * JSON mappers for tests without a running server.
 */
public final class TestMappers {
    private TestMappers() {}

    /**
     * Creates a mapper which stores items by type, amount and enchantments only, since the full item data needs a
     * running server.
     *
     * @return mapper for item stacks
     */
    public static ObjectMapper items() {
        var module = new SimpleModule();
        module.addSerializer(ItemStack.class, new ValueSerializer<>() {
            @Override
            public void serialize(ItemStack value, JsonGenerator gen, SerializationContext context) {
                gen.writeStartObject();
                gen.writeStringProperty("type", value.getType().name());
                gen.writeNumberProperty("amount", value.getAmount());
                gen.writeObjectPropertyStart("enchantments");
                for (var enchantment : value.getEnchantments().entrySet()) {
                    gen.writeNumberProperty(enchantment.getKey().getKey().toString(), enchantment.getValue());
                }
                gen.writeEndObject();
                gen.writeEndObject();
            }
        });
        module.addDeserializer(ItemStack.class, new ValueDeserializer<>() {
            @Override
            public ItemStack deserialize(JsonParser p, DeserializationContext ctxt) {
                JsonNode node = ctxt.readTree(p);
                var stack = new ItemStack(
                        Material.valueOf(node.get("type").asString()),
                        node.get("amount").asInt());
                var enchantments = node.get("enchantments");
                if (enchantments == null) return stack;
                for (var entry : enchantments.properties()) {
                    var enchantment = RegistryAccess.registryAccess()
                            .getRegistry(RegistryKey.ENCHANTMENT)
                            .getOrThrow(Key.key(entry.getKey()));
                    stack.addUnsafeEnchantment(enchantment, entry.getValue().asInt());
                }
                return stack;
            }
        });
        return JsonMapper.builder().addModule(module).build();
    }
}
