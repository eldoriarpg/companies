/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.eldoria.companies.menus.admin.LevelField;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/**
 * Provides the suggestions of the company level editor.
 */
public final class LevelProviders {
    /**
     * The fields of a level that {@code /companyadmin level edit} changes.
     */
    public static final List<String> FIELDS =
            Arrays.stream(LevelField.values()).map(LevelField::key).toList();

    private LevelProviders() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @return the configured level numbers starting with what is typed
     */
    @LevelNumbers
    public static CompletableFuture<Suggestions> levels(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        var count =
                SuggestionSources.current().map(SuggestionSources::levelCount).orElse(0);
        levelNumbers(count, builder.getRemaining()).forEach(builder::suggest);
        return builder.buildFuture();
    }

    /**
     * @return the editable fields starting with what is typed
     */
    @LevelFields
    public static CompletableFuture<Suggestions> fields(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CompanyProviders.matching(FIELDS, builder.getRemaining()).forEach(builder::suggest);
        return builder.buildFuture();
    }

    /**
     * @return example values of the field in argument {@code field} starting with what is typed
     */
    @LevelValues
    public static CompletableFuture<Suggestions> values(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CompanyProviders.matching(exampleValues(field(context)), builder.getRemaining())
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    /**
     * @param count how many levels are configured
     * @param typed what is typed so far
     * @return the level numbers from 1 to the count whose digits start with what is typed
     */
    public static List<Integer> levelNumbers(int count, String typed) {
        return IntStream.rangeClosed(1, count)
                .filter(level -> String.valueOf(level).startsWith(typed))
                .boxed()
                .toList();
    }

    /**
     * @param field the edited field
     * @return values worth suggesting for the field; the tax can be cleared with {@code none}
     */
    public static List<String> exampleValues(String field) {
        if ("tax_percent".equals(field)) return List.of("none", "0", "5", "10");
        return List.of();
    }

    private static String field(CommandContext<CommandSourceStack> context) {
        try {
            return context.getArgument("field", String.class);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
