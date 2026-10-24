/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {defineNitroPlugin} from '#imports'

/** The largest WebSocket frame the server reads at all: a node's snapshot of up to 4 MB, with room for its envelope. */
export const MAX_PAYLOAD_BYTES = 4 * 1024 * 1024 + 64 * 1024

/**
 * Limits the frames the WebSocket server reads, which `ws` otherwise allows up to 100 MiB.
 *
 * <p>Nitro builds its WebSocket server from the options of the h3 app's `websocket`, which it hands on unchanged,
 * and takes no configuration of its own for them, so this plugin sets `serverOptions.maxPayload` there before the
 * server starts. The limit is one for every route; the relay holds each route to its own, smaller one as well (see
 * `utils/relay.ts`), and a larger frame closes the connection with 1009.
 */
export default defineNitroPlugin(nitroApp => {
    const websocket = nitroApp.h3App.websocket as {serverOptions?: Record<string, unknown>}
    websocket.serverOptions = {...websocket.serverOptions, maxPayload: MAX_PAYLOAD_BYTES}
})
