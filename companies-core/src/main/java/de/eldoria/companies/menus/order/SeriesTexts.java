/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import de.eldoria.companies.data.wrapper.series.OrderSeries;
import de.eldoria.companies.data.wrapper.series.SeriesState;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * The texts of a repeating order shared by its list and its info menu.
 */
final class SeriesTexts {
    private SeriesTexts() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @param prefix prefix of the placeholder keys, empty for none
     * @param series the series
     * @return id and name
     */
    static MenuText title(String prefix, OrderSeries series) {
        return OrderTexts.title(prefix, series.id(), series.name());
    }

    /**
     * How the series repeats, its copies, its state and what comes next: when the next copy is due, why it waits or
     * why the series was stopped.
     *
     * @param series the series
     * @param now    current time
     * @return the lines describing the series
     */
    static MenuText details(OrderSeries series, LocalDateTime now) {
        var text = MenuText.create()
                .text("<name><i18n:order.draft.repeat.label>: ")
                .append(RepetitionTexts.summary(series))
                .newLine()
                .text("<name>")
                .append(RepetitionTexts.copies(series))
                .newLine()
                .text("<name><i18n:words.status>: <value><i18n:"
                        + series.state().translationKey() + ">");
        series.stopReason().ifPresent(reason -> text.text(" <inactive>(<i18n:" + reason.translationKey() + ">)"));
        if (series.state() != SeriesState.ACTIVE) return text;
        text.newLine();
        if (series.waiting().isPresent()) {
            return text.text("<neutral><i18n:" + series.waiting().get().translationKey() + ">");
        }
        return text.text("<name>")
                .locale(
                        "order.series.next",
                        Values.text("next_due", OrderTexts.pretty(Duration.between(now, series.nextDue()))));
    }
}
