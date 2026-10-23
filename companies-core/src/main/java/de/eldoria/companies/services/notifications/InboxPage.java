/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import de.eldoria.companies.data.wrapper.inbox.InboxEntry;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One page of a player's inbox, newest first. The entries keep the read state they had before the page was shown.
 *
 * @param entries entries of the page
 * @param number  number of the page, from one
 * @param hasNext whether older entries follow
 * @param now     when the page was read, for the date headers
 */
public record InboxPage(List<InboxEntry> entries, int number, boolean hasNext, LocalDateTime now) {
    /**
     * @param entries entries of the page
     * @param number  number of the page, from one
     * @param hasNext whether older entries follow
     * @param now     when the page was read
     */
    public InboxPage {
        entries = List.copyOf(entries);
    }
}
