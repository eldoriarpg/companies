/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import de.eldoria.companies.data.wrapper.series.OrderSeries;
import de.eldoria.companies.data.wrapper.series.RepeatInterval;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.Repetition;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The texts describing how an order repeats, such as "Weekly, 4 times" or "Daily until 2026-12-24", shared by the
 * order builder and the repeating order menus.
 */
public final class RepetitionTexts {
    private RepetitionTexts() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @param repetition repetition of a draft
     * @return interval and end
     */
    public static MenuText summary(Repetition repetition) {
        return summary(repetition.interval(), repetition.count(), repetition.until());
    }

    /**
     * @param series a repeating order
     * @return interval and end
     */
    public static MenuText summary(OrderSeries series) {
        return summary(series.interval(), series.maxCopies(), series.ends().map(LocalDateTime::toLocalDate));
    }

    /**
     * @param series a repeating order
     * @return the copies posted, with the count limit when there is one
     */
    public static MenuText copies(OrderSeries series) {
        var max = series.maxCopies();
        var posted = Values.number("posted", series.posted());
        if (max.isEmpty()) return MenuText.create().locale("order.series.copies", posted);
        return MenuText.create().locale("order.series.copiesOf", posted, Values.number("max_copies", max.getAsInt()));
    }

    private static MenuText summary(RepeatInterval interval, OptionalInt count, Optional<LocalDate> until) {
        var text = MenuText.create().text("<value><i18n:" + interval.translationKey() + "><default>");
        if (count.isPresent()) {
            text.text(", ").locale("order.series.times", Values.number("count", count.getAsInt()));
        }
        until.ifPresent(date -> text.space().locale("order.series.until", Values.text("until", date.toString())));
        return text;
    }
}
