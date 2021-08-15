package de.eldoria.companies.configuration.elements;

import de.eldoria.eldoutilities.serialization.SerializationUtil;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class OrderSettings implements ConfigurationSerializable {
    int maxItems = 64 * 8;
    int maxMaterials = 5;

    public OrderSettings(Map<String, Object> objectMap) {
        var map = SerializationUtil.mapOf(objectMap);
        maxItems = map.getValueOrDefault("maxItems", maxItems);
        maxMaterials = map.getValueOrDefault("maxMaterials", maxMaterials);
    }

    public OrderSettings() {
    }

    @Override
    @NotNull
    public Map<String, Object> serialize() {
        return SerializationUtil.newBuilder()
                .add("maxItems", maxItems)
                .add("maxMaterials", maxMaterials)
                .build();
    }

    public int maxItems() {
        return maxItems;
    }

    public void maxItems(int maxItems) {
        this.maxItems = maxItems;
    }

    public int maxMaterials() {
        return maxMaterials;
    }

    public void maxMaterials(int maxMaterials) {
        this.maxMaterials = maxMaterials;
    }
}
