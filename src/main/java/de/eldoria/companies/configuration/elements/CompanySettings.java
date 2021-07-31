package de.eldoria.companies.configuration.elements;

import de.eldoria.eldoutilities.serialization.SerializationUtil;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class CompanySettings implements ConfigurationSerializable {
    private int maxOrder = 5;
    private int deliveryDays = 7;
    private int maxMember = 20;
    private float foudingPrice = 20000.0f;

    public CompanySettings(Map<String, Object> objectMap) {
        var map = SerializationUtil.mapOf(objectMap);
        maxOrder = map.getValueOrDefault("maxOrder", maxOrder);
        deliveryDays = map.getValueOrDefault("deliveryDays", deliveryDays);
        maxMember = map.getValueOrDefault("maxMember", maxMember);
        foudingPrice = map.getValueOrDefault("foudingPrice", foudingPrice);
    }

    public CompanySettings() {
    }

    @Override
    @NotNull
    public Map<String, Object> serialize() {
        return SerializationUtil.newBuilder()
                .add("maxOrder", maxOrder)
                .add("deliveryDays", deliveryDays)
                .add("maxMember", maxMember)
                .add("foudingPrice", foudingPrice)
                .build();
    }

    public int maxOrder() {
        return maxOrder;
    }

    public int deliveryDays() {
        return deliveryDays;
    }

    public int maxMember() {
        return maxMember;
    }

    public float foudingPrice() {
        return foudingPrice;
    }

    public void maxOrder(int maxOrder) {
        this.maxOrder = maxOrder;
    }

    public void deliveryDays(int deliveryDays) {
        this.deliveryDays = deliveryDays;
    }

    public void maxMember(int maxMember) {
        this.maxMember = maxMember;
    }

    public void foudingPrice(float foudingPrice) {
        this.foudingPrice = foudingPrice;
    }
}
