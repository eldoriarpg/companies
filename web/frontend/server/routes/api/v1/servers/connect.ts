/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {defineWebSocketHandler} from 'h3'
import {relaySocket} from '../../../../utils/relaySocket'

/**
 * The connection of a Companies node, relayed to the gateway, since only the frontend is published. A node sends
 * snapshots of up to 4 MB and needs no cookie. Close codes pass through unchanged both ways, so the gateway's 4400,
 * 4401, 4408, 4409 and 4429 tell the plugin why it was closed, see `utils/relay.ts`.
 */
export default defineWebSocketHandler(relaySocket({cookie: false, maxMessageBytes: 4 * 1024 * 1024}))
