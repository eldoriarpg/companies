/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.eldoria.companies.data.wrapper.treasury.Treasury;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Provides the suggestions of {@code /company bank}.
 */
public final class TreasuryProviders {
    private TreasuryProviders() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @return the balance of the sender's company treasury, when it starts with what is typed
     */
    @TreasuryAmounts
    public static CompletableFuture<Suggestions> treasuryAmounts(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        if (!(context.getSource().getSender() instanceof Player player)) return builder.buildFuture();
        return TreasurySources.current()
                .map(sources ->
                        sources.treasuries().suggest(player.getUniqueId(), builder, TreasuryProviders::suggestBalance))
                .orElseGet(builder::buildFuture);
    }

    /**
     * Suggests the balance of a treasury that holds money.
     *
     * @param treasury treasury of the player's company, empty without a company
     * @param builder  builder of the suggestions
     */
    static void suggestBalance(Optional<Treasury> treasury, SuggestionsBuilder builder) {
        treasury.filter(found -> found.cents() > 0)
                .map(found -> found.balance().toPlainString())
                .filter(balance -> balance.startsWith(builder.getRemaining()))
                .ifPresent(builder::suggest);
    }
}
