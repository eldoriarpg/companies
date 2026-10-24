/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.policy;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A plugin version such as {@code 2.2.0}, compared by its numbers. A suffix after {@code -} or {@code +}, such as
 * {@code -SNAPSHOT}, is ignored, and missing numbers count as zero, so {@code 2.2} equals {@code 2.2.0}.
 *
 * @param numbers the version's numbers
 */
public record PluginVersion(List<Integer> numbers) implements Comparable<PluginVersion> {

    /**
     * @param text the version
     * @return the version; a text without leading numbers reads as {@code 0}
     */
    public static PluginVersion parse(String text) {
        String core = text.strip().split("[-+]", 2)[0];
        List<Integer> numbers = new ArrayList<>();
        for (String part : core.split("\\.")) {
            String digits = part.replaceAll("\\D.*$", "");
            if (digits.isEmpty()) break;
            numbers.add(Integer.parseInt(digits));
        }
        return new PluginVersion(List.copyOf(numbers));
    }

    @Override
    public int compareTo(PluginVersion other) {
        int length = Math.max(numbers.size(), other.numbers.size());
        for (int i = 0; i < length; i++) {
            int compared = Integer.compare(number(i), other.number(i));
            if (compared != 0) return compared;
        }
        return 0;
    }

    private int number(int index) {
        return index < numbers.size() ? numbers.get(index) : 0;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof PluginVersion version && compareTo(version) == 0;
    }

    @Override
    public int hashCode() {
        List<Integer> trimmed = new ArrayList<>(numbers);
        while (!trimmed.isEmpty() && trimmed.getLast() == 0) trimmed.removeLast();
        return trimmed.hashCode();
    }

    @Override
    public String toString() {
        return String.join(".", numbers.stream().map(String::valueOf).toList());
    }
}
