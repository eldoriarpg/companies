/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import axios, {type AxiosInstance, isAxiosError} from 'axios'
import type {
    ActionResult,
    DeprecatedPart,
    ErrorMessage,
    ItemView,
    JoinMode,
    Limits,
    MemberPermission,
    NodeInfo,
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
    /** Whether the player has admin rights; the admin features are listed for them only. */
    admin?: boolean
    /** The connected servers of the network, primary first; listed for players with admin rights only. */
    nodes?: NodeInfo[]
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

/** The session's player came online on the server or went offline, which decides whether items can be moved. */
export interface PresenceUpdate {
    type: 'presence'
    online: boolean
}

/** What the gateway sends on the stream. Messages of any other type are ignored. */
export type StreamMessage = Patch | Pong | SessionClosed | ErrorMessage | Resync | ServerUpdate | PresenceUpdate

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

/**
 * An order as the plugin's read actions send it (`OrderSummary`). Neither its owner nor the company or worker on it are
 * ever part of it.
 */
export interface OrderListing {
    id: number
    name: string
    state: OrderState
    lines: OrderLineView[]
    created: string
    /** When an accepted order has to be done; none (or null) for an open order. */
    deadline?: string | null
    /** When an open order expires unless a company accepts it; none once claimed. */
    expires?: string | null
    /** What all lines pay together. */
    price?: number
    /** How many items all lines ask for. */
    amount?: number
    /** When it was claimed, for the company working on it. */
    claimed?: string | null
    /** Items delivered and waiting for the owner, for the owner of a delivered order. */
    waiting?: ItemView[] | null
}

/** A page of the order book. */
export interface OrderSearchPage {
    page: number
    pages: number
    /** How many orders match in total, where the plugin counts them. */
    total?: number
    /** The tax taken from payments, 5 meaning 5 %. */
    taxPercent?: number
    /** Whether the player may accept orders, for the company or alone. */
    canAccept?: boolean
    /** Whether the company or the player works on as many orders as allowed. */
    limitReached?: boolean
    orders: OrderListing[]
}

/** One message of the inbox. */
export interface InboxEntryView {
    id: number
    /** The kind of notification, an open set such as `ORDER_ACCEPTED`. */
    type: string
    /** The category the notification is muted by. */
    category?: string
    created: string
    read: boolean
    /** The message as the plugin renders it in the player's language, plain text. */
    text: string
}

/** A page of the inbox. */
export interface InboxPage {
    page: number
    pages: number
    total?: number
    entries: InboxEntryView[]
    /** The categories the player muted. */
    muted?: string[]
    /** The categories that can be muted. */
    mutable?: string[]
}

/** The price statistics of one material. Prices are absent when too few orders back them. */
export interface MarketItemView {
    item: ItemView
    avg?: number
    min?: number
    max?: number
    /** Items ordered over the period, where known. */
    volume?: number
    /** How many orders the statistics stand on. */
    orders?: number
    /** The change of the average in percent over the last week against the week before, where both weeks have enough orders. */
    change?: number
}

/** One day of a material's price history. The price is absent on days with too few orders. */
export interface MarketHistoryPoint {
    /** The day, as `yyyy-MM-dd`. */
    day: string
    orders: number
    items: number
    avg?: number
}

/** The price statistics of one material with its history, oldest day first. */
export interface MarketDetailView extends MarketItemView {
    /** How many days the history reaches back, today included. */
    days: number
    /** The server's today, the last day of the history, as `yyyy-MM-dd`. */
    to: string
    history: MarketHistoryPoint[]
}

/** A page of market statistics. */
export interface MarketPage {
    page: number
    pages: number
    total?: number
    items: MarketItemView[]
}

/** One movement of the treasury. */
export interface TreasuryLogEntry {
    id: number
    time: string
    /** What moved the money, an open set such as `DEPOSIT`, `WITHDRAWAL` or `PAYOUT_SHARE`. */
    kind: string
    /** How much moved, signed: positive into the treasury, negative out of it. */
    amount: number
    /** The balance after the movement. */
    balance: number
    /** The member who moved the money, where one did; inside the company members are named. */
    actor?: PlayerRef
    /** The order a payout share came from. */
    order?: number
    /** Free text the plugin adds, such as a company's new name. */
    note?: string
}

/** A page of the treasury log, newest first. */
export interface TreasuryLogPage {
    page: number
    pages: number
    total?: number
    entries: TreasuryLogEntry[]
}

/** An open application to the company. */
export interface Application {
    player: PlayerRef
    message: string
    created: string
    expires: string
}

/** An invite the company sent that is still open. */
export interface SentInvite {
    player: PlayerRef
    inviter: PlayerRef
    created: string
    expires: string
}

/** The open applications to the company and the invites it sent, all on one page. */
export interface ApplicationsPage {
    page: number
    pages: number
    total: number
    entries: Application[]
    invites?: SentInvite[]
}

/** An orderable material and what survival can give it. */
export interface CatalogueMaterial {
    item: ItemView
    /** The keys of the enchantments it can take; every enchantment for an enchanted book. */
    enchantments: string[]
    /** Whether it takes a potion type. */
    potion: boolean
    /** Whether it can be dyed. */
    dye: boolean
    /** Whether it takes an armour trim. */
    trim: boolean
}

/** An enchantment with its highest survival level and the enchantments it excludes. */
export interface CatalogueEnchantment {
    key: string
    name: string
    max: number
    conflicts: string[]
}

/** An entry of a registry, such as a potion type or a trim pattern. */
export interface CatalogueEntry {
    key: string
    name: string
}

/** A dye colour and the colour leather takes from it, as `#rrggbb`. */
export interface CatalogueDye extends CatalogueEntry {
    color: string
}

/** What orders can be made of on this server. */
export interface OrderCatalogue {
    materials: CatalogueMaterial[]
    enchantments: CatalogueEnchantment[]
    potions: CatalogueEntry[]
    dyes: CatalogueDye[]
    trimPatterns: CatalogueEntry[]
    trimMaterials: CatalogueEntry[]
}

/** An exact item the player can order again: a saved item or one of an earlier order. */
export interface KnownItem {
    /** How an order line names it: `saved:<id>` or `earlier:<entry>:<line>`. */
    ref: string
    /** The saved item's name, or the earlier order's name. */
    label: string
    item: ItemView
    /** What it carries, such as enchantments, as short lines. */
    details: string[]
}

/** The player's saved items and the exact items of their earlier orders. */
export interface KnownItems {
    saved: KnownItem[]
    earlier: KnownItem[]
}

/** An item defined in the browser: a material and what survival can give it. */
export interface ItemDefinition {
    material: string
    /** Enchantment keys with their levels. */
    enchantments?: Record<string, number>
    potion?: string
    dye?: string
    trim?: {pattern: string, material: string}
}

/** One line of an order to place: a material key, a known item's ref, or a defined item. */
export interface OrderLineArgs {
    item?: string
    define?: ItemDefinition
    amount: number
    unitPrice: number
}

/** How an order repeats: every day or week, a number of times or until a day (`yyyy-MM-dd`). */
export interface RepetitionArgs {
    interval: 'daily' | 'weekly'
    times?: number
    until?: string
}

/** An order to place. */
export interface OrderCreateArgs {
    name: string
    lines: OrderLineArgs[]
    repetition?: RepetitionArgs
}

/** What an order costs. */
export interface OrderQuote {
    price: number
    fee: number
    total: number
}

/** One line of an order as an admin sees it. */
export interface AdminOrderLine {
    item: ItemView
    delivered: number
    price: number
    /** Who delivered how many, by name. */
    workers: Record<string, number>
}

/** An order as an admin sees it, whoever owns or works on it. */
export interface AdminOrder {
    id: number
    name: string
    state: OrderState
    owner: PlayerRef
    /** The company working on it, none while open or worked on alone. */
    company?: string | null
    /** The player working on it alone. */
    soloWorker?: string | null
    created: string
    /** When it runs out, none once delivered. */
    deadline?: string | null
    /** Whether the open order is removed at the deadline rather than being due. */
    removal: boolean
    price: number
    fee: number
    lines: AdminOrderLine[]
    /** What cancelling with a refund gives the owner back. */
    refundOnCancel: number
    /** What cancelling without a refund hands to the collector. */
    keptOnCancel: number
    /** What marking it delivered refunds the owner for the missing items. */
    refundOnDeliver: number
}

/** An order of a page of an admin search. */
export interface AdminOrderRow {
    id: number
    name: string
    state: OrderState
    owner: string
    /** The company or the player working on it, none while open. */
    claimant?: string | null
    created: string
}

/** A page of an admin search. */
export interface AdminOrderPage {
    target: string
    page: number
    hasNext: boolean
    orders: AdminOrderRow[]
}

/** A player as an admin looks them up. */
export interface AdminPlayer {
    player: PlayerRef
    /** The player's company, none without one. */
    company?: string | null
    owner: boolean
    permissions: string[]
    orders: AdminOrderRow[]
    /** Items waiting for the player as a worker. */
    workerItems: number
    /** Items waiting for the player as an owner. */
    ownerItems: number
    unread: number
    latest: InboxEntryView[]
}

/** A company's treasury as an admin sees it. */
export interface AdminTreasury {
    company: string
    balance: number
    /** The share of payouts the company keeps, in percent. */
    share: number
    entries: TreasuryLogEntry[]
}

/** A company level as an admin edits it. The name is MiniMessage text as written in `company.yml`. */
export interface AdminLevel {
    level: number
    name: string
    orderCount: number
    memberCount: number
    earnedMoney: number
    deliveredItems: number
    maxMembers: number
    maxOrders: number
    /** The most items an accepted order may have, 0 for no limit. */
    maxOrderSize: number
    deliveryHoursBonus: number
    /** The tax on payouts in percent, none for the global tax. */
    taxPercent?: number | null
    /** The most order alerts, none for the global limit. */
    maxAlerts?: number | null
}

/** The company levels for an admin. */
export interface AdminLevels {
    /** Whether the answering server may change them, which only the primary does. */
    editable: boolean
    levels: AdminLevel[]
}

/** The editable fields of a level, by the names `/companyadmin level edit` uses. */
export type LevelFieldKey = 'name' | 'order_count' | 'member_count' | 'earned_money' | 'delivered_items' | 'max_members'
    | 'max_orders' | 'max_order_size' | 'delivery_hours_bonus' | 'tax_percent' | 'max_alerts'

/** A board preset with its open orders. */
export interface AdminBoardPreset {
    /** The preset's name, as signs and NPCs name it. */
    name: string
    label: string
    open: number
}

/** An NPC bound to a board. */
export interface AdminNpcBinding {
    hook: string
    npc: string
    /** The preset or materials, empty for every open order. */
    board: string
}

/** The NPCs bound to boards on a server, with the NPC plugins loaded there. */
export interface AdminNpcBoards {
    hooks: string[]
    bindings: AdminNpcBinding[]
}

/** A server registered in the database. */
export interface AdminNode {
    uid: string
    role: string
    version: string
    lastSeen?: string | null
    /** How it exchanges messages with the others, in the server's language. */
    bus: string
    /** Whether it is the server answering, which cannot be removed. */
    current: boolean
}

/** The server a per-server admin action goes to; none for the primary. */
export interface NodeChoice {
    node?: string
}

/** A level an admin action names. */
export interface LevelRef {
    level: number
}

/** A player an admin action names. */
export interface PlayerName {
    player: string
}

/** A company an admin action names. */
export interface CompanyName {
    company: string
}

/** The read actions of the dashboard with their arguments and the `data` of their answer. */
export interface ReadActions {
    'orders.browse.search': {args: OrderSearchArgs, result: OrderSearchPage}
    'orders.browse.details': {args: {order: number}, result: OrderListing}
    'inbox.page': {args: {page: number, query?: string}, result: InboxPage}
    'rankings.page': {args: {ranking: string, range: string, page: number}, result: RankingPage}
    'market.search': {args: {query: string, page: number}, result: MarketPage}
    'market.top': {args: {page: number}, result: MarketPage}
    'market.movers': {args: {page: number}, result: MarketPage}
    'market.item': {args: {material: string, days: number}, result: MarketDetailView}
    'company.treasury.log': {args: {page: number}, result: TreasuryLogPage}
    'company.applications.list': {args: Record<string, never>, result: ApplicationsPage}
    'orders.create.catalogue': {args: Record<string, never>, result: OrderCatalogue}
    'orders.create.items': {args: Record<string, never>, result: KnownItems}
    'orders.create.quote': {args: OrderCreateArgs, result: OrderQuote}
}

/** The name of a read action. */
export type ReadAction = keyof ReadActions

/** What a delivery from the web moved. */
export interface DeliveryDone {
    /** How many items were delivered. */
    delivered: number
    /** Whether the delivery completed the order. */
    done: boolean
}

/** An order an action named. */
export interface OrderRef {
    order: number
}

/**
 * The actions that change something, with their arguments and the `data` of their answer. Cancelling and raising
 * answer a quote and change nothing when `quote` is true.
 */
export interface WriteActions {
    'orders.create': {args: OrderCreateArgs, result: OrderRef & OrderQuote}
    'admin.orders.info': {args: OrderRef, result: AdminOrder}
    'admin.orders.search': {args: {player?: string, company?: string, page: number}, result: AdminOrderPage}
    'admin.orders.unclaim': {args: OrderRef, result: OrderRef & {workers: number}}
    'admin.orders.cancel': {args: OrderRef & {refund: boolean}, result: OrderRef & {refunded: number, collected: number}}
    'admin.orders.deliver': {args: OrderRef, result: OrderRef & {refund: number}}
    'admin.orders.extend': {args: OrderRef & {hours: number}, result: OrderRef & {hours: number}}
    'admin.players.lookup': {args: PlayerName, result: AdminPlayer}
    'admin.players.grant-items': {args: PlayerName & {material: string, amount: number}, result: PlayerName & {amount: number}}
    'admin.players.grant-money': {args: PlayerName & {amount: number}, result: PlayerName & {amount: string}}
    'admin.companies.treasury': {args: CompanyName, result: AdminTreasury}
    'admin.companies.treasury.give': {args: CompanyName & {amount: number}, result: CompanyName & {balance: number}}
    'admin.companies.treasury.take': {args: CompanyName & {amount: number}, result: CompanyName & {balance: number}}
    'admin.companies.rename': {args: CompanyName & {name: string}, result: CompanyName}
    'admin.companies.transfer-owner': {args: CompanyName & PlayerName, result: CompanyName & {owner: string}}
    'admin.companies.delete': {args: CompanyName, result: CompanyName}
    'admin.levels.list': {args: Record<string, never>, result: AdminLevels}
    'admin.levels.info': {args: LevelRef, result: AdminLevel}
    'admin.levels.create': {args: {position?: number}, result: AdminLevel}
    'admin.levels.edit': {args: LevelRef & {values: Partial<Record<LevelFieldKey, string>>, position?: number}, result: AdminLevel}
    'admin.levels.move': {args: LevelRef & {to: number}, result: AdminLevels}
    'admin.levels.remove': {args: LevelRef, result: AdminLevels}
    'admin.levels.recalculate': {args: Record<string, never>, result: Record<string, never>}
    'admin.boards.presets': {args: NodeChoice, result: {presets: AdminBoardPreset[]}}
    'admin.boards.reload': {args: NodeChoice, result: Record<string, never>}
    'admin.boards.npc.list': {args: NodeChoice, result: AdminNpcBoards}
    'admin.boards.npc.link': {args: NodeChoice & {hook: string, npc: string, board: string}, result: AdminNpcBinding}
    'admin.boards.npc.unlink': {args: NodeChoice & {hook: string, npc: string}, result: AdminNpcBinding}
    'admin.nodes.list': {args: Record<string, never>, result: {nodes: AdminNode[]}}
    'admin.nodes.remove': {args: {uid: string}, result: {nodes: AdminNode[]}}
    'admin.config.sync': {args: Record<string, never>, result: Record<string, never>}
    'orders.deliver.line': {args: OrderRef & {line: number, amount?: number}, result: OrderRef & DeliveryDone}
    'orders.deliver.all': {args: OrderRef, result: OrderRef & DeliveryDone}
    'orders.deliver.receive': {args: OrderRef, result: OrderRef & {amount: number, waiting: number}}
    'orders.deliver.collect': {args: Record<string, never>, result: {received: number, remaining: number}}
    'orders.accept': {args: OrderRef, result: OrderRef}
    'orders.accept.abort': {args: OrderRef, result: OrderRef}
    'orders.own.cancel': {args: OrderRef & {quote?: boolean}, result: OrderRef & {repeating?: boolean}}
    'orders.own.extend': {args: OrderRef, result: OrderRef & {left: number, refundInSeconds: number}}
    'orders.own.raise': {
        args: OrderRef & {increase: number, quote?: boolean}
        result: OrderRef & {increase: number, fee: number, total: number}
    }
    'inbox.read-all': {args: Record<string, never>, result: {marked: number}}
    'inbox.clear': {args: Record<string, never>, result: {deleted: number}}
    'inbox.mute': {args: {category: string, mute: boolean}, result: {category: string, muted: boolean, mask: number}}
    'company.members.revoke': {args: PlayerArg, result: PlayerArg}
    'company.members.kick': {args: PlayerArg, result: PlayerArg}
    'company.members.leave': {args: Record<string, never>, result: {company: number}}
    'company.members.transfer': {args: PlayerArg, result: PlayerArg}
    'company.create': {args: {name: string, quote?: boolean}, result: {name?: string, company?: number, price: number}}
    'company.disband': {args: {name: string}, result: {company: number}}
    'company.members.permissions': {
        args: PlayerArg & {permissions: Partial<Record<MemberPermission, boolean>>}
        result: PlayerArg & {permissions: MemberPermission[]}
    }
    'company.applications.accept': {args: PlayerArg, result: PlayerArg}
    'company.applications.deny': {args: PlayerArg, result: PlayerArg}
    'company.applications.join-mode': {args: {mode: JoinMode}, result: {mode: JoinMode}}
    'company.profile.description': {args: {description: string}, result: ProfileResult}
    'company.profile.tag': {args: {tag: string}, result: ProfileResult}
    'company.profile.color': {args: {color: string}, result: ProfileResult}
    'company.profile.rename': {
        args: {name: string, quote?: boolean, payment?: RenamePayment}
        result: RenameResult
    }
    'company.treasury.deposit': {args: {amount: number}, result: {balance: number}}
    'company.treasury.withdraw': {args: {amount: number}, result: {balance: number}}
    'company.treasury.share': {args: {percent: number}, result: {share: number, balance: number}}
}

/** A player an action named, by id. */
export interface PlayerArg {
    player: string
}

/** The company's profile as an edit left it. */
export interface ProfileResult {
    name: string
    description?: string
    tag?: string
    color?: string
}

/** What renaming costs, or the new name once it is done. */
export interface RenameResult {
    price?: number
    treasury?: number
    name?: string
}

/** Who pays a rename. */
export type RenamePayment = 'PLAYER' | 'TREASURY'

/** The name of an action that changes something. */
export type WriteAction = keyof WriteActions

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
    act<N extends WriteAction>(name: N, args: WriteActions[N]['args']): Promise<WriteActions[N]['result']>
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
        act: (name, args) => http.post<ActionResult>('/me/actions', {name, args}).then(response => resultData(response.data)),
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
