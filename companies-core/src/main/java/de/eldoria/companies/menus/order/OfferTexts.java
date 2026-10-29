/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import de.eldoria.companies.data.wrapper.offer.OfferMark;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Times;

import java.time.format.DateTimeFormatter;

/**
 * Texts of the event offers orders belong to.
 */
public final class OfferTexts {
    private static final DateTimeFormatter ENDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private OfferTexts() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * The event offer of an order with its progress and its end, or that it is closed. Its placeholders are fixed, so
     * render it on its own before inserting it into a message holding several.
     *
     * @param offer the offer
     * @return the line
     */
    public static MenuText line(OfferMark offer) {
        var id = Values.number("offer_id", offer.offer());
        var name = Values.text("offer_name", offer.name());
        var percent = Values.number("offer_percent", offer.percent());
        if (offer.state().closed()) return MenuText.create().locale("offers.markClosed", id, name, percent);
        return MenuText.create()
                .locale(
                        "offers.mark",
                        id,
                        name,
                        percent,
                        Values.text("offer_ends", ENDS.format(Times.local(offer.ends()))));
    }
}
