package de.eldoria.companies.configuration.elements;

import de.eldoria.eldoutilities.serialization.SerializationUtil;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class GeneralSettings implements ConfigurationSerializable {
    private String language = "de_US";
    private int orderCheckInterval = 60;

    public GeneralSettings() {
    }

    public GeneralSettings(Map<String, Object> objectMap) {
        var map = SerializationUtil.mapOf(objectMap);
        orderCheckInterval = map.getValueOrDefault("orderCheckInterval", orderCheckInterval);
        language = map.getValueOrDefault("language", language);
    }

    @Override
    @NotNull
    public Map<String, Object> serialize() {
        return SerializationUtil.newBuilder()
                .add("orderCheckInterval", orderCheckInterval)
                .add("language", language)
                .build();
    }

    public int orderCheckInterval() {
        return orderCheckInterval;
    }

    public String language(){
        return language;
    }
}
