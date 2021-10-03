package de.eldoria.companies.configuration.elements;

import de.eldoria.companies.Companies;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.data.wrapper.company.CompanyStats;
import de.eldoria.eldoutilities.serialization.SerializationUtil;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CompanySettings implements ConfigurationSerializable {
    private int deliveryHours = 48;
    private double foudingPrice = 20000.0;
    private double renamePrice = 10000.0;
    private int expiredOrderPenalty = 3;
    private int abortedOrderPenalty = 1;
    private List<CompanyLevel> level = new ArrayList<>();

    public CompanySettings(Map<String, Object> objectMap) {
        var map = SerializationUtil.mapOf(objectMap);
        deliveryHours = map.getValueOrDefault("deliveryHours", deliveryHours);
        foudingPrice = map.getValueOrDefault("foudingPrice", foudingPrice);
        renamePrice = map.getValueOrDefault("renamePrice", renamePrice);
        expiredOrderPenalty = map.getValueOrDefault("expiredOrderPenalty", expiredOrderPenalty);
        abortedOrderPenalty = map.getValueOrDefault("abortedOrderPenalty", abortedOrderPenalty);
        level = map.getValueOrDefault("level", level);
        if (level.isEmpty()) {
            Companies.logger().info("No company level set. Creating default level.");
            level.add(new CompanyLevel());
        }
        updateLevel();
    }

    public CompanySettings() {
    }

    @Override
    @NotNull
    public Map<String, Object> serialize() {
        return SerializationUtil.newBuilder()
                .add("deliveryHours", deliveryHours)
                .add("foudingPrice", foudingPrice)
                .add("renamePrice", renamePrice)
                .add("expiredOrderPenalty", expiredOrderPenalty)
                .add("abortedOrderPenalty", abortedOrderPenalty)
                .add("level", level)
                .build();
    }

    public int deliveryHours() {
        return deliveryHours;
    }

    public double foudingPrice() {
        return foudingPrice;
    }

    public void deliveryHours(int deliveryHours) {
        this.deliveryHours = deliveryHours;
    }

    public void foudingPrice(float foudingPrice) {
        this.foudingPrice = foudingPrice;
    }

    public void foudingPrice(double foudingPrice) {
        this.foudingPrice = foudingPrice;
    }

    public int expiredOrderPenalty() {
        return expiredOrderPenalty;
    }

    public void expiredOrderPenalty(int expiredOrderPenalty) {
        this.expiredOrderPenalty = expiredOrderPenalty;
    }

    public int abortedOrderPenalty() {
        return abortedOrderPenalty;
    }

    public void abortedOrderPenalty(int abortedOrderPenalty) {
        this.abortedOrderPenalty = abortedOrderPenalty;
    }

    public double renamePrice() {
        return renamePrice;
    }

    public void renamePrice(double renamePrice) {
        this.renamePrice = renamePrice;
    }

    public CompanyLevel createLevel(int level) {
        var newLevel = new CompanyLevel();
        var clampedLevel = Math.max(0, Math.min(level - 1, this.level.size()));
        this.level.add(clampedLevel, newLevel);
        updateLevel();
        return newLevel;
    }

    public Optional<CompanyLevel> level(int level) {
        if (level > this.level.size()) return Optional.empty();
        return Optional.ofNullable(this.level.get(level - 1));
    }

    public void moveLevel(int source, int target) {
        if (source == target) return;
        var clampedTarget = Math.max(1, Math.min(target, level.size())) - 1;
        level.add(clampedTarget, level.remove(source - 1));
        updateLevel();
    }

    public List<CompanyLevel> level() {
        return level;
    }

    public CompanyLevel calcCompanyLevel(CompanyStats stats) {
        var finalLevel = level.get(0);
        for (var level : level) {
            if (!level.requirement().checkRequirements(stats)) break;
            finalLevel = level;
        }
        return finalLevel;
    }

    private void updateLevel() {
        for (var i = 0; i < level.size(); i++) {
            level.get(i).level(i + 1);
        }
    }

    public boolean deleteLevel(int level) {
        if (level < 1 || level < this.level.size()) return false;
        this.level.remove(level);
        return true;
    }
}
