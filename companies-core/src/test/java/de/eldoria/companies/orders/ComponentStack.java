/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import io.papermc.paper.datacomponent.DataComponentType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockbukkit.mockbukkit.inventory.ItemStackMock;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An item stack with a data component patch on top of an empty prototype, like Paper's item stacks.
 * <p>
 * MockBukkit's item stacks implement neither {@code matchesWithoutData} nor {@code resetData}, and their clones drop
 * every component. This stack keeps its components through clones, resets a component by removing it from the patch,
 * and compares two stacks by type and patch without the excluded types. Two stacks are similar when type and patch
 * are equal.
 */
@SuppressWarnings("UnstableApiUsage")
final class ComponentStack extends ItemStackMock {
    private final Map<DataComponentType, Object> patch = new LinkedHashMap<>();

    ComponentStack(Material material) {
        super(material);
    }

    private ComponentStack(ComponentStack stack) {
        super(stack.getType(), stack.getAmount());
        patch.putAll(stack.patch);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getData(DataComponentType.@NotNull Valued<T> type) {
        return (T) patch.get(type);
    }

    @Override
    public boolean hasData(@NotNull DataComponentType type) {
        return patch.containsKey(type);
    }

    @Override
    public @NotNull Set<@NotNull DataComponentType> getDataTypes() {
        return Set.copyOf(patch.keySet());
    }

    @Override
    public <T> void setData(DataComponentType.@NotNull Valued<T> type, @NotNull T value) {
        patch.put(type, value);
    }

    @Override
    public void setData(DataComponentType.@NotNull NonValued type) {
        patch.put(type, Boolean.TRUE);
    }

    @Override
    public void unsetData(@NotNull DataComponentType type) {
        patch.remove(type);
    }

    @Override
    public void resetData(@NotNull DataComponentType type) {
        patch.remove(type);
    }

    @Override
    public boolean isDataOverridden(@NotNull DataComponentType type) {
        return patch.containsKey(type);
    }

    @Override
    public boolean matchesWithoutData(
            @NotNull ItemStack item, @NotNull Set<@NotNull DataComponentType> excludeTypes, boolean ignoreCount) {
        if (!(item instanceof ComponentStack other)) return false;
        if (getType() != other.getType()) return false;
        if (!ignoreCount && getAmount() != other.getAmount()) return false;
        return Objects.equals(without(patch, excludeTypes), without(other.patch, excludeTypes));
    }

    @Override
    public boolean isSimilar(@Nullable ItemStack stack) {
        return stack instanceof ComponentStack other && getType() == other.getType() && patch.equals(other.patch);
    }

    private static Map<DataComponentType, Object> without(
            Map<DataComponentType, Object> patch, Set<DataComponentType> excluded) {
        Map<DataComponentType, Object> result = new HashMap<>(patch);
        result.keySet().removeAll(excluded);
        return result;
    }

    @Override
    public @NotNull ItemStack clone() {
        return new ComponentStack(this);
    }
}
