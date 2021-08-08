package de.eldoria.companies.configuration.elements;

import de.eldoria.eldoutilities.serialization.SerializationUtil;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class CompanySettings implements ConfigurationSerializable {
    private int maxOrders = 5;
    private int deliveryHours = 48;
    private int maxMember = 20;
    private double foudingPrice = 20000.0f;

    public CompanySettings(Map<String, Object> objectMap) {
        var map = SerializationUtil.mapOf(objectMap);
        maxOrders = map.getValueOrDefault("maxOrders", maxOrders);
        deliveryHours = map.getValueOrDefault("deliveryHours", deliveryHours);
        maxMember = map.getValueOrDefault("maxMember", maxMember);
        foudingPrice = map.getValueOrDefault("foudingPrice", foudingPrice);
    }

    public CompanySettings() {
    }

    @Override
    @NotNull
    public Map<String, Object> serialize() {
        return SerializationUtil.newBuilder()
                .add("maxOrders", maxOrders)
                .add("deliveryHours", deliveryHours)
                .add("maxMember", maxMember)
                .add("foudingPrice", foudingPrice)
                .build();
    }

    public int maxOrders() {
        return maxOrders;
    }

    public int deliveryHours() {
        return deliveryHours;
    }

    public int maxMember() {
        return maxMember;
    }

    public double foudingPrice() {
        return foudingPrice;
    }

    public void maxOrders(int maxOrder) {
        this.maxOrders = maxOrder;
    }

    public void deliveryHours(int deliveryDays) {
        this.deliveryHours = deliveryDays;
    }

    public void maxMember(int maxMember) {
        this.maxMember = maxMember;
    }

    public void foudingPrice(float foudingPrice) {
        this.foudingPrice = foudingPrice;
    }
}
