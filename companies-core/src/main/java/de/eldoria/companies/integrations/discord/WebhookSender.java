/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import java.io.IOException;

/**
 * Sends one request to the webhook.
 */
@FunctionalInterface
public interface WebhookSender {
    /**
     * Posts the body and waits for the answer.
     *
     * @param json JSON body
     * @return the answer
     * @throws IOException          when the request could not be sent or answered
     * @throws InterruptedException when the thread was interrupted while waiting
     */
    WebhookResponse post(String json) throws IOException, InterruptedException;
}
