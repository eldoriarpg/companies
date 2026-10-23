/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The four lines of a board sign: the header, the board's label, the open order count and a hint to right-click.
 * <p>
 * Texts come from the locale of the server, since a sign looks the same to everyone.
 */
public final class SignBoardLines {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Function<String, String> localize;
    private final Supplier<String> header;

    /**
     * @param localize localizes a locale key in the server's language
     * @param header   the configured header of board signs
     */
    public SignBoardLines(Function<String, String> localize, Supplier<String> header) {
        this.localize = localize;
        this.header = header;
    }

    /**
     * @param target what the board shows
     * @param count  open orders of the board, or null while they are counted
     * @return the four lines of the sign
     */
    public List<Component> lines(BoardTarget target, @Nullable Integer count) {
        var label = target.label().isBlank()
                ? MINI_MESSAGE.deserialize(localize.apply("boards.sign.all"))
                : Component.text(target.label());
        return List.of(
                Component.text(header.get(), NamedTextColor.DARK_BLUE, TextDecoration.BOLD),
                label,
                countLine(count),
                MINI_MESSAGE.deserialize(localize.apply("boards.sign.use")));
    }

    /**
     * @param count open orders of the board, or null while they are counted
     * @return the line with the count
     */
    public Component countLine(@Nullable Integer count) {
        if (count == null) return MINI_MESSAGE.deserialize(localize.apply("boards.sign.counting"));
        return MINI_MESSAGE.deserialize(
                localize.apply("boards.sign.open"), Placeholder.unparsed("count", String.valueOf(count)));
    }

    /**
     * Writes the lines to both sides of a sign. The sign state still has to be updated.
     *
     * @param sign  the sign
     * @param lines the lines to write
     */
    public static void write(Sign sign, List<Component> lines) {
        for (var side : Side.values()) {
            var signSide = sign.getSide(side);
            for (var line = 0; line < lines.size(); line++) {
                signSide.line(line, lines.get(line));
            }
        }
    }
}
