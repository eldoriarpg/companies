package de.eldoria.companies.configuration.elements;

import de.eldoria.eldoutilities.serialization.SerializationUtil;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class UserSettings implements ConfigurationSerializable {
    private int maxOrders = 7;

    public UserSettings(Map<String, Object> objectMap) {
        var map = SerializationUtil.mapOf(objectMap);
        maxOrders = map.getValueOrDefault("maxOrders", maxOrders);
    }

    public UserSettings() {
    }

    @Override
    @NotNull
    public Map<String, Object> serialize() {
        return SerializationUtil.newBuilder()
                .add("maxOrders", maxOrders)
                .build();
    }

    public int maxOrders() {
        return maxOrders;
    }

    public void maxOrders(int maxOrders) {
        this.maxOrders = maxOrders;
    }
}
