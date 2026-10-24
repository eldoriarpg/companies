/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import axios, {type AxiosInstance, isAxiosError} from 'axios'
import type {
    ActionResult,
    DeprecatedPart,
    ErrorMessage,
    ItemView,
    Limits,
    OrderLineView,
    OrderState,
    Patch,
    PlayerRef,
    Pong,
    RankingPage,
    SessionClosed,
    Snapshot,
    VersionStatus,
} from '~/api/generated/protocol'

/** Where the gateway's browser API lives, relative to the page. */
export const API_BASE = '/api/v1'

/** The address of the live stream of the session the cookie belongs to. */
export const STREAM_PATH = `${API_BASE}/me/stream`

/** The server a session belongs to, as the login page, the header and the browser tab show it. */
export interface ServerBranding {
    /** The name the server owner configured, the server's MOTD without formatting by default. */
    name: string
    /** Where the server icon is served, `/api/v1/icons/{hash}`; none when the server has no icon. */
    iconUrl?: string
}

/** `GET /api/v1/s/{shortId}`: a session waiting for its code. */
export interface PendingSession {
    server: ServerBranding
    /** When the code stops being valid. */
    expiresAt: string
    /** How many wrong codes are still accepted before the pending session is gone. */
    attemptsLeft: number
    /** The Turnstile site key when the gateway asks for a challenge with the code; none when it does not. */
    turnstileSiteKey?: string
    /** The state of the server's plugin version; a discontinued server shows only its message. */
    version: VersionStatus
}

/** `POST /api/v1/s/{shortId}/login`: the body. A 204 answer sets the session cookie. */
export interface LoginRequest {
    /** The six letters the chat showed, upper case. */
    code: string
    /** The Turnstile token, sent when the pending session named a site key. */
    turnstileToken?: string
}

/** `GET /api/v1/me/server`: the server of the logged in session and what its plugin supports. */
export interface ServerInfo {
    server: ServerBranding
    /** The features the plugin listed in its hello and the gateway still supports; they decide what is shown. */
    features: string[]
    limits: Limits
    version: VersionStatus
    /** The deprecated parts this server still uses; the gateway lists them for players with admin rights only. */
    deprecated: DeprecatedPart[]
    /** Whether a node of the server takes requests; while none does, the features are not to be trusted. */
    online: boolean
}

/** Why a call failed, as the gateway names it in the body of every answer that is not 2xx. */
export type GatewayErrorCode =
    | 'UNKNOWN_SESSION'
    | 'WRONG_CODE'
    | 'TOO_MANY_ATTEMPTS'
    | 'TURNSTILE_FAILED'
    | 'UNAUTHORIZED'
    | 'DISCONTINUED'
    | 'SERVER_OFFLINE'
    | 'RATE_LIMITED'
    | 'INTERNAL'

/** The body of an answer that is not 2xx. */
export interface GatewayError {
    error: GatewayErrorCode
    /** A sentence for the reader, where the gateway has one. */
    message?: string
    /** With `WRONG_CODE`: the attempts still accepted. */
    attemptsLeft?: number
    /** With `DISCONTINUED`: the version state and its message. */
    version?: VersionStatus
}

/** The gateway tells the browser to load the snapshot again, after it rebuilt it from the plugin. */
export interface Resync {
    type: 'resync'
}

/** The server's branding, features or version changed; the new state replaces the old one. */
export interface ServerUpdate {
    type: 'server'
    server: ServerInfo
}

/** What the gateway sends on the stream. Messages of any other type are ignored. */
export type StreamMessage = Patch | Pong | SessionClosed | ErrorMessage | Resync | ServerUpdate

/** The search of the order book, mirroring the in-game search: every field is optional but the page. */
export interface OrderSearchArgs {
    /** Part of the order's name. */
    name?: string
    /** Material keys the order asks for. */
    materials?: string[]
    minPrice?: number
    maxPrice?: number
    minOrderSize?: number
    maxOrderSize?: number
    /** The in-game sorting: `AGE`, `NAME`, `PRICE`, `SIZE` or `UNIT_PRICE`. */
    sort?: string
    asc?: boolean
    page: number
    pageSize: number
}

/** An open order as the order book shows it. The owner is never part of it. */
export interface OrderListing {
    id: number
    name: string
    state: OrderState
    lines: OrderLineView[]
    created: string
    /** When an accepted order has to be done; none for an open order. */
    deadline?: string
}

/** A page of the order book. */
export interface OrderSearchPage {
    page: number
    pages: number
    /** How many orders match in total, where the plugin counts them. */
    total?: number
    orders: OrderListing[]
}

/** One message of the inbox. */
export interface InboxEntryView {
    id: number
    /** The kind of notification, an open set such as `ORDER_ACCEPTED`. */
    type: string
    created: string
    read: boolean
    /** The message as the plugin renders it in the player's language, plain text. */
    text: string
}

/** A page of the inbox. */
export interface InboxPage {
    page: number
    pages: number
    entries: InboxEntryView[]
}

/** The price statistics of one material. Prices are absent when too few orders back them. */
export interface MarketItemView {
    item: ItemView
    avg?: number
    min?: number
    max?: number
    /** Items ordered over the period, where known. */
    volume?: number
    /** The change of the average over the period in percent, once price history exists. */
    change?: number
}

/** A page of market statistics. */
export interface MarketPage {
    page: number
    pages: number
    items: MarketItemView[]
}

/** One movement of the treasury. */
export interface TreasuryLogEntry {
    id: number
    time: string
    /** What moved the money, an open set such as `DEPOSIT`, `WITHDRAWAL` or `PAYOUT_SHARE`. */
    kind: string
    /** How much moved. */
    amount: number
    /** The balance after the movement. */
    balance: number
    /** The member who moved the money, where one did; inside the company members are named. */
    actor?: PlayerRef
    /** The order a payout share came from. */
    order?: number
}

/** A page of the treasury log, newest first. */
export interface TreasuryLogPage {
    page: number
    pages: number
    total?: number
    entries: TreasuryLogEntry[]
}

/** The read actions of the dashboard with their arguments and the `data` of their answer. */
export interface ReadActions {
    'orders.browse.search': {args: OrderSearchArgs, result: OrderSearchPage}
    'orders.browse.details': {args: {order: number}, result: OrderListing}
    'inbox.page': {args: {page: number, query?: string}, result: InboxPage}
    'rankings.page': {args: {ranking: string, range: string, page: number}, result: RankingPage}
    'market.search': {args: {query: string, page: number}, result: MarketPage}
    'market.top': {args: {page: number}, result: MarketPage}
    'company.treasury.log': {args: {page: number}, result: TreasuryLogPage}
}

/** The name of a read action. */
export type ReadAction = keyof ReadActions

/** An action the plugin refused, with its reason key and the sentence it rendered. */
export class ActionRefused extends Error {
    /**
     * @param reason the plugin's reason key
     * @param text the rendered sentence
     */
    constructor(readonly reason: string, text: string) {
        super(text)
        this.name = 'ActionRefused'
    }
}

/** The gateway's calls of the browser API. */
export interface GatewayApi {
    pending(shortId: string): Promise<PendingSession>
    login(shortId: string, request: LoginRequest): Promise<void>
    server(): Promise<ServerInfo>
    snapshot(): Promise<Snapshot>
    read<N extends ReadAction>(name: N, args: ReadActions[N]['args']): Promise<ReadActions[N]['result']>
    logout(): Promise<void>
}

/**
 * The calls of the gateway's browser API. The session travels as the HttpOnly cookie the login set; the page never
 * reads or writes it.
 *
 * @param http the HTTP client, for tests
 * @returns the calls
 */
export function gatewayApi(http: AxiosInstance = gatewayHttp()): GatewayApi {
    return {
        pending: shortId => http.get<PendingSession>(`/s/${encodeURIComponent(shortId)}`).then(response => response.data),
        login: (shortId, request) => http.post(`/s/${encodeURIComponent(shortId)}/login`, request).then(() => undefined),
        server: () => http.get<ServerInfo>('/me/server').then(response => response.data),
        snapshot: () => http.get<Snapshot>('/me/snapshot').then(response => response.data),
        read: (name, args) => http.post<ActionResult>('/me/actions', {name, args}).then(response => resultData(response.data)),
        logout: () => http.post('/me/logout').then(() => undefined),
    }
}

/**
 * The data of an action's answer, or the refusal it carries.
 *
 * @param result the answer
 * @returns its data
 */
function resultData<T>(result: ActionResult): T {
    if (!result.ok) throw new ActionRefused(result.reason ?? 'refused', result.message ?? '')
    return result.data as T
}

/**
 * The HTTP client of the browser API: same origin, so the cookie goes along, and JSON both ways.
 *
 * @returns the client
 */
function gatewayHttp(): AxiosInstance {
    return axios.create({baseURL: API_BASE, withCredentials: true, headers: {Accept: 'application/json'}})
}

/**
 * What a failed call says: its status and the gateway's error body, where it sent one.
 *
 * @param error what the call threw
 * @returns the status, 0 without an answer, and the body
 */
export function gatewayFailure(error: unknown): {status: number, body: GatewayError | null} {
    if (!isAxiosError(error)) return {status: 0, body: null}
    const data = error.response?.data as Partial<GatewayError> | undefined
    return {status: error.response?.status ?? 0, body: typeof data?.error === 'string' ? data as GatewayError : null}
}
