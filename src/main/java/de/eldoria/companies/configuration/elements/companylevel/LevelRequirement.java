package de.eldoria.companies.configuration.elements.companylevel;

import de.eldoria.companies.data.wrapper.company.CompanyStats;
import de.eldoria.eldoutilities.serialization.SerializationUtil;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class LevelRequirement implements ConfigurationSerializable {
    private int orderCount = 0;
    private double earnedMoney = 0.0;
    private int deliveredItems = 0;
    private int memberCount = 0;

    public LevelRequirement() {
    }

    public LevelRequirement(Map<String, Object> objectMap) {
        var map = SerializationUtil.mapOf(objectMap);
        orderCount = map.getValueOrDefault("orderCount", orderCount);
        earnedMoney = map.getValueOrDefault("earnedMoney", earnedMoney);
        deliveredItems = map.getValueOrDefault("deliveredItems", deliveredItems);
        memberCount = map.getValueOrDefault("memberCount", memberCount);
    }

    @Override
    @NotNull
    public Map<String, Object> serialize() {
        return SerializationUtil.newBuilder()
                .add("orderCount", orderCount)
                .add("earnedMoney", earnedMoney)
                .add("deliveredItems", deliveredItems)
                .add("memberCount", memberCount)
                .build();
    }

    public boolean checkRequirements(CompanyStats stats) {
        return stats.orderCount() >= orderCount
               && stats.price() >= earnedMoney
               && stats.amount() >= deliveredItems
               && stats.memberCount() >= memberCount;
    }

    public int orderCount() {
        return orderCount;
    }

    public void orderCount(int orderCount) {
        this.orderCount = orderCount;
    }

    public double earnedMoney() {
        return earnedMoney;
    }

    public void earnedMoney(double earnedMoney) {
        this.earnedMoney = earnedMoney;
    }

    public int deliveredItems() {
        return deliveredItems;
    }

    public void deliveredItems(int deliveredItems) {
        this.deliveredItems = deliveredItems;
    }

    public int memberCount() {
        return memberCount;
    }

    public void memberCount(int memberCount) {
        this.memberCount = memberCount;
    }
}
