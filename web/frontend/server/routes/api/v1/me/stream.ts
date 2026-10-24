/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {defineWebSocketHandler} from 'h3'
import {relaySocket} from '../../../../utils/relaySocket'

/**
 * The live stream of the dashboard session the cookie belongs to, relayed to the gateway's stream with the cookie.
 * The browser only sends pings, so its messages stay small.
 *
 * <p>Only the upgrade reaches this route. A plain HTTP request to the address is forwarded by the
 * backend proxy middleware before any route runs.
 */
export default defineWebSocketHandler(relaySocket({cookie: true, maxMessageBytes: 64 * 1024}))
