/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import type {MarketItemView} from '~/api/gateway'

/** The market as the dashboard last loaded it. */
export interface MarketData {
    /** The most ordered items, which the ticker runs through. */
    top: MarketItemView[]
    status: 'idle' | 'loading' | 'ready' | 'failed'
}

/**
 * The market's most ordered items, shared by the ticker, the market page and the order book's comparison with the
 * average. Read and changed only through `useMarket`.
 *
 * @returns the state
 */
export function marketState() {
    return useState<MarketData>('marketState', () => ({top: [], status: 'idle'}))
}
