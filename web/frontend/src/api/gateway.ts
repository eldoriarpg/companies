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
    /** The in-game sorting: `AGE`, `NAME`, `PRICE`, `SIZE`, `UNIT_PRICE` or `OFFER_END`. */
    sort?: string
    asc?: boolean
    /** Which orders of the server to show while the server marks them: `only` or `hide`; every order without. */
    serverOrders?: 'only' | 'hide'
    /** `only` to show the chunks of event offers alone. */
    offers?: 'only'
    /** The event offer whose chunks alone are shown. */
    offer?: number
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
    /** Whether the server placed it; only set while the server marks its orders, never for the chunk of an event offer. */
    server?: boolean
    /** The event offer it is a chunk of, which it is marked with; none for any other order. */
    offer?: OfferBadge | null
    /** Items delivered and waiting for the owner, for the owner of a delivered order. */
    waiting?: ItemView[] | null
}

/** The event offer an order is a chunk of. */
export interface OfferBadge {
    id: number
    name: string
    ends: string
    /** `scheduled`, `active`, `paused` or a closed state. */
    state: string
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
    /** The item tags whose items can all be ordered. Missing from servers before criteria lines. */
    tags?: CatalogueTag[]
}

/** An item tag a criteria line can name. */
export interface CatalogueTag {
    /** The tag as a criteria entry, e.g. `#minecraft:logs`. */
    key: string
    name: string
    /** The material keys of its items. */
    materials: string[]
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

/**
 * What a criteria line accepts: any item of a set of materials (`minecraft:oak_log`) and tags (`#minecraft:logs`), with
 * at least `minEnchantments` enchantments and each of `enchantments` at a minimum level.
 */
export interface CriteriaArgs {
    items: string[]
    minEnchantments?: number
    enchantments?: Record<string, number>
}

/** One line of an order to place: a material key, a known item's ref, a defined item, or criteria. */
export interface OrderLineArgs {
    item?: string
    define?: ItemDefinition
    criteria?: CriteriaArgs
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
    /** The most areas a company of the level may have. */
    maxAreas: number
}

/** The company levels for an admin. */
export interface AdminLevels {
    /** Whether the answering server may change them, which only the primary does while it can read `company.yml`. */
    editable: boolean
    /** Why it may not: the server is a secondary, or `company.yml` cannot be read; none when it may. */
    blocked?: 'secondary' | 'unreadable' | null
    levels: AdminLevel[]
}

/** The editable fields of a level, by the names `/companyadmin level edit` uses. */
export type LevelFieldKey = 'name' | 'order_count' | 'member_count' | 'earned_money' | 'delivered_items' | 'max_members'
    | 'max_orders' | 'max_order_size' | 'delivery_hours_bonus' | 'tax_percent' | 'max_alerts' | 'max_areas'

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

/** A system order open or in work, as the admin list shows it. */
export interface AdminSystemOrder {
    order: number
    name: string
    /** The rule that placed it, `guessed` for a guessed order, `event-offer` for a chunk of an event offer. */
    rule: string
    /** How it was decided: `DEFINED`, `GUESSED` or `OFFER` for a chunk of an event offer. */
    kind: string
    /** Its state, missing when it was removed meanwhile. */
    state?: string | null
    placed: string
    paid: number
    /** The economy account that paid it, missing for minted money. */
    account?: string | null
}

/** Why the server placed one of its orders, with the numbers its decision stood on as stored. */
export interface AdminSystemDecision {
    order: number
    rule: string
    kind: string
    placed: string
    paid: number
    account?: string | null
    record?: Record<string, unknown> | null
}

/**
 * One thing a rule orders: a material, criteria in the text form players type, or an exact item captured in game,
 * which the editor keeps as it is.
 */
export interface SystemOrderRuleItem {
    material?: string
    criteria?: string
    /** Whether a criteria order takes any item of the set or one material picked from it. */
    resolve?: 'ANY' | 'ONE'
    /** An exact item as the plugin stores it. */
    item?: unknown
    /** The name of an exact item. */
    name?: string
}

/** A defined rule of the server's own orders, as the rules editor edits it. */
export interface SystemOrderRule {
    id: string
    name: string
    enabled: boolean
    /** A material name such as `IRON_INGOT`, for a rule written before `items`. */
    material: string
    /** What the rule orders; one entry is picked per order. */
    items: SystemOrderRuleItem[]
    /** How an order picks one of several entries. */
    pick: 'RANDOM' | 'ROTATE'
    amount?: number
    price: {
        mode: string
        value?: number
        factor?: number
        jitter?: number
        min?: number
        max?: number
    }
    everyHours?: number
    lifetimeHours?: number
    maxOpen?: number
    source: {
        type: string
        account: string
        dailyBudget?: number
    }
}

/** What the guessed orders of one material learned. */
export interface AdminSystemLearned {
    material: string
    posted: number
    taken: number
    expired: number
    minutesToTake?: number | null
    factor: number
    offset: number
    takeRate: number
}

/** What an event offer orders. */
export interface OfferItem {
    /** The item's name, or the criteria as players type them. */
    name: string
    /** The icon of a material; none for criteria. */
    icon?: string | null
    /** The criteria of a criteria offer. */
    criteria?: string | null
}

/** The kind of an event offer. */
export type OfferKind = 'goal' | 'contest'

/** An event offer as the Events page lists it. */
export interface EventRow {
    id: number
    kind: OfferKind
    name: string
    item: OfferItem
    amount: number
    delivered: number
    /** The share delivered, from 0 to 100. */
    percent: number
    unitPrice: number
    /** `scheduled`, `active` or `paused` in the list; an opened offer may also be `reached`, `ended`, `depleted` or `cancelled`. */
    state: string
    starts: string
    ends: string
    /** The money of the prizes of a contest. */
    prizeTotal: number
    /** The place of the player's company in a contest; none before it delivered. */
    place?: number | null
    /** Whether the player's company takes part in the contest. */
    joined: boolean
    /** The server event the offer belongs to; none for one that stands alone. */
    event?: number | null
}

/** What a server event changes. */
export type ServerEventKind = 'tax_reduction' | 'reward_multiplier' | 'fee_reduction'

/** A server event as the Events page lists it: running, or starting within a day. */
export interface ServerEventView {
    id: number
    kind: ServerEventKind
    name: string
    /** Percentage points off the tax, the factor on payouts, or percent off the order fee. */
    value: number
    /** The materials it is limited to; empty for every material. */
    materials: string[]
    /** The company levels it is limited to; empty for every company. */
    levels: number[]
    /** `scheduled` or `active`. */
    state: string
    starts: string
    ends: string
    /** What a multiplier with a budget can still pay; none otherwise. */
    budgetLeft?: number | null
}

/** The event offers and server events a player sees. */
export interface EventList {
    offers: EventRow[]
    /** The server events running or starting within a day, the soonest start first. */
    serverEvents: ServerEventView[]
    /** Whether the player is part of a company. */
    member: boolean
    /** Whether the player may let the company join or leave contests. */
    canManage: boolean
}

/** Who delivered to a goal; players working alone are not named. */
export interface EventContributor {
    rank: number
    name?: string | null
    alone: boolean
    delivered: number
    chunks: number
}

/** A company in the ranking of a contest. */
export interface EventRank {
    place: number
    name: string
    delivered: number
    active: boolean
    own: boolean
}

/** A prize of a contest. */
export interface EventPrize {
    place: number
    amount: number
    /** The company that won it once the contest closed. */
    winner?: string | null
    /** `pending`, `paying`, `paid`, `failed` or `none`. */
    state: string
}

/** The part of the player's company in a contest. */
export interface EventOwnPart {
    company: string
    joined: boolean
    delivered: number
    paid: number
    place?: number | null
}

/** An event offer as its page shows it. */
export interface EventDetails {
    offer: EventRow
    chunkSize?: number | null
    contributors: EventContributor[]
    ranking: EventRank[]
    prizes: EventPrize[]
    own?: EventOwnPart | null
    member: boolean
    canManage: boolean
}

/** An event offer in the admin list. */
export interface AdminOfferRow {
    id: number
    kind: OfferKind
    name: string
    item: OfferItem
    state: string
    delivered: number
    amount: number
    percent: number
    /** `minted` or `account`. */
    source: string
    account?: string | null
    budget: number
    spent: number
    left: number
    starts: string
    ends: string
}

/** Who delivered to a goal, as admins see it. */
export interface AdminOfferContributor {
    rank: number
    company?: number | null
    name?: string | null
    alone: boolean
    delivered: number
    chunks: number
}

/** A company in a contest, as admins see it. */
export interface AdminOfferEntry {
    place: number
    company: number
    name?: string | null
    delivered: number
    paid: number
    active: boolean
    joined: string
    left?: string | null
}

/** A settled prize of a contest. */
export interface AdminOfferPrize {
    place: number
    amount: number
    company?: number | null
    name?: string | null
    state: string
}

/** An event offer with what its decision record shows. */
export interface AdminOffer {
    id: number
    kind: OfferKind
    name: string
    item: OfferItem
    /** `material`, `criteria` or `item`. */
    line: string
    state: string
    amount: number
    delivered: number
    percent: number
    unitPrice: number
    chunkSize?: number | null
    openChunks?: number | null
    /** Items of chunks waiting to be taken. */
    waiting: number
    /** Items of chunks taken and not delivered yet. */
    taken: number
    source: string
    account?: string | null
    budget: number
    spent: number
    left: number
    /** What a started contest took from its account. */
    held?: number | null
    prizeMoney: number[]
    prizeTotal: number
    createdBy: string
    created: string
    starts: string
    ends: string
    started?: string | null
    closed?: string | null
    settled?: string | null
    refund?: number | null
    refunded?: string | null
    contributors: AdminOfferContributor[]
    ranking: AdminOfferEntry[]
    prizes: AdminOfferPrize[]
}

/** A server event in the admin list. */
export interface AdminEventRow {
    id: number
    kind: ServerEventKind
    name: string
    value: number
    materials: string[]
    levels: number[]
    /** `scheduled`, `active`, `ended`, `depleted` or `cancelled`. */
    state: string
    starts: string
    ends: string
    /** `account` or `minted` for a multiplier; none for the other kinds. */
    funding?: 'account' | 'minted' | null
    account?: string | null
    /** What a multiplier may pay in all; none for no cap. */
    budget?: number | null
    spent: number
    /** The template of `events.yml` that created it; none for one created by hand. */
    template?: string | null
    createdBy: string
    created: string
    closed?: string | null
    /** Who ended or cancelled it; `#missed` or `#template` for the primary's markers. */
    closedBy?: string | null
    /** The event offers that belong to it. */
    offers: {id: number, name: string}[]
}

/** A new server event: the fields of `/companyadmin event create`, as an admin types them. */
export interface AdminEventDraft {
    name: string
    /** `TAX_REDUCTION`, `REWARD_MULTIPLIER` or `FEE_REDUCTION`. */
    kind: string
    value: string
    /** Materials, separated by commas; empty for every material. */
    materials?: string
    /** Levels, separated by commas; empty for every company. */
    levels?: string
    /** `now`, a duration such as `2h`, or a date `yyyy-MM-dd HH:mm` in the server's zone. */
    start: string
    /** A duration such as `1d12h`, or a date `yyyy-MM-dd HH:mm`. */
    end: string
    /** `ACCOUNT` or `MINTED`, for a multiplier. */
    funding?: string
    /** The account paying a multiplier; empty for the collector account. */
    account?: string
    /** What a multiplier may pay in all. */
    budget?: string
}

/** Names a server event. */
export interface ServerEventRef {
    event: number
}

/** A new goal or contest, checked by the plugin as `/companyadmin offers goal` and `contest` check it. */
export interface AdminOfferDraft {
    kind: OfferKind
    name: string
    /** `minted` or the paying economy account. */
    money: string
    /** A material name; or `criteria`. */
    material?: string
    /** Criteria in the text form players type. */
    criteria?: string
    amount: number
    price: number
    /** Items per chunk of a goal. */
    chunk?: number
    /** Chunks of a goal waiting at once. */
    open?: number
    /** The money of the first places of a contest. */
    prizes?: number[]
    /** Hours until it starts. */
    start: number
    /** Hours it runs. */
    hours: number
    /** What the whole offer may spend; none for what it costs. */
    budget?: number
}

/** An event offer an action named. */
export interface OfferRef {
    offer: number
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
    /** Whether it uses an older revision of a shared settings file than the latest. */
    behind?: boolean
    /** The changed settings it applies only after a restart, none when no restart is pending. */
    restart?: string | null
}

/** A shared settings file. */
export type SettingsFileName =
  | 'config.yml'
  | 'company.yml'
  | 'order.yml'
  | 'user.yml'
  | 'system-orders.yml'
  | 'events.yml'
  | 'jobs.yml'
  | 'areas.yml'

/** How a shared setting is entered. */
export type SettingType = 'FLAG' | 'WHOLE' | 'DECIMAL' | 'TEXT' | 'CHOICE' | 'CHOICES' | 'TEXT_LIST' | 'FLAG_MAP' | 'SECRET'

/** Whether a secret setting is set, never its value. */
export interface AdminSecret {
    set: boolean
    source: 'environment' | 'file' | 'none'
}

/** One shared setting as its latest revision holds it. */
export interface AdminSetting {
    section: string
    key: string
    type: SettingType
    choices: string[]
    /** Whether it applies only after a restart. */
    restart: boolean
    /** Its value, none for a secret. */
    value?: unknown
    secret?: AdminSecret | null
    /** Whether the answering server sets it for itself in its node.yml. */
    overridden: boolean
    /** The smallest number allowed, none unless a number. */
    min?: number | null
    /** The largest number allowed, none when unbounded. */
    max?: number | null
}

/** A shared settings file with how the servers stand on it. */
export interface AdminSettingsFile {
    file: SettingsFileName
    revision: number
    /** The server that answered, whose the revision used, the unshared edits, the overrides and the secrets' sources are. */
    node: string
    /** The revision the answering server uses. */
    applied: number
    /** Whether the answering server changed it without sharing the change. */
    editedHere: boolean
    /** The servers on an older revision. */
    behind: string[]
    settings: AdminSetting[]
    /** Whether the admin may change it, which needs companies.admin.reload. */
    editable: boolean
}

/** One revision of a shared settings file. */
export interface AdminSettingsChange {
    revision: number
    actor: string
    node: string
    changed: string
}

/** One setting a revision changed; values are absent for secrets and settings edited in the file only. */
export interface AdminSettingDiff {
    key: string
    before?: unknown
    after?: unknown
    secret: boolean
    /** Whether it is edited in the file only, whose values are not shown. */
    fileOnly: boolean
}

/** A setting a setup page shows. */
export interface AdminSetupField {
    file: string
    key: string
}

/** One page of the guided setup. */
export interface AdminSetupPage {
    id: string
    state: 'open' | 'done' | 'skipped'
    /** The settings it shows, none for a page that explains or links. */
    fields: AdminSetupField[]
}

/** The guided setup and how far it got, for the whole network. */
export interface AdminSetup {
    pages: AdminSetupPage[]
    /** Whether an admin dismissed the reminder to finish it. */
    dismissed: boolean
    /** Whether the admin may save settings and mark pages, which needs companies.admin.reload. */
    editable: boolean
}

/** What saving settings or rolling a file back did. */
export interface AdminSettingsSaved {
    /** `unchanged`: nothing differed from the latest revision; `newer`: another revision was saved after the one shown. */
    status: 'saved' | 'unchanged' | 'invalid' | 'newer'
    /** The stored revision, or the latest one when unchanged or newer. */
    revision: number
    /** Why each refused value was refused, by key. */
    fields: Record<string, string>
    /** The submitted settings as the newer revision holds them, without secrets. */
    values: Record<string, unknown>
    /** The settings the newer revisions changed. */
    changed: string[]
    /** The saved settings that apply only after a restart. */
    restart: string[]
    /** Whether the answering server uses the stored revision. */
    applied: boolean
}

/** What one revision changed against the one before. */
export interface AdminSettingsRevision extends AdminSettingsChange {
    file: SettingsFileName
    /** Whether it is the first revision, which holds the file as it was first stored and changed nothing. */
    first: boolean
    changes: AdminSettingDiff[]
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

/** The kind of a job. */
export type JobKind = 'dig' | 'terraform' | 'harvest' | 'build' | 'contract'

/** Where a job is in its life. */
export type JobState = 'open' | 'taken' | 'done' | 'disputed' | 'paid' | 'settled' | 'cancelled' | 'expired'

/** A job as the Jobs page lists it. The owner is never named to companies, the company never to the owner. */
export interface JobRow {
    id: number
    kind: JobKind
    state: JobState
    title: string
    /** The price, the budget of a harvest job. */
    price: number
    fee: number
    /** When the company's time runs out, as an ISO instant; none while nobody holds the job. */
    due?: string | null
    expires?: string | null
    /** The blocks of the area; 0 for a contract. */
    blocks: number
    world?: string | null
    center?: string | null
    unitPrice?: number | null
    paid?: number | null
    /** Whether the player owns it. */
    mine: boolean
    /** Whether the player's company holds it. */
    ours: boolean
}

/** The lists of the Jobs page. */
export interface JobList {
    open: JobRow[]
    mine: JobRow[]
    company: JobRow[]
    inCompany: boolean
    canManage: boolean
    canPost: boolean
}

/** A job with what its page shows. */
export interface JobDetails {
    job: JobRow
    description?: string | null
    rejected?: string | null
    dispute?: string | null
    disputeByOwner: boolean
    settled?: number | null
    approveAt?: string | null
    targetY?: number | null
    surface?: string | null
    /** The stock of the player's harvest job, as amounts by material name. */
    stock: Record<string, number>
    canManage: boolean
    /** Whether the player's company may take the job: it is open and nobody of the company owns it. */
    takeable: boolean
}

/** A job an action names. */
export interface JobRef {
    job: number
}

/** A disputed job as admins see it. */
export interface DisputeRow {
    id: number
    kind: JobKind
    title: string
    description?: string | null
    price: number
    owner: string
    company?: string | null
    openedBy?: string | null
    byOwner: boolean
    reason?: string | null
    due?: string | null
    disputedAt?: string | null
    overdue: boolean
    world?: string | null
    center?: string | null
    work: number
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
    'events.list': {args: Record<string, never>, result: EventList}
    'events.info': {args: OfferRef, result: EventDetails}
    'jobs.list': {args: Record<string, never>, result: JobList}
    'jobs.info': {args: JobRef, result: JobDetails}
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
    'admin.systemorders.list': {args: Record<string, never>, result: {orders: AdminSystemOrder[]}}
    'admin.systemorders.info': {args: {order: number}, result: AdminSystemDecision}
    'admin.systemorders.stats': {args: Record<string, never>, result: {materials: AdminSystemLearned[]}}
    'admin.systemorders.rules': {args: Record<string, never>, result: {revision: number, editable: boolean, rules: Partial<SystemOrderRule>[]}}
    'admin.systemorders.rules.save': {
        args: {base: number, rules: SystemOrderRule[]}
        result: {result: 'saved' | 'unchanged' | 'newer' | 'invalid', revision: number, errors: Record<string, string>, rules?: Partial<SystemOrderRule>[] | null, applied: boolean}
    }
    'admin.nodes.remove': {args: {uid: string}, result: {nodes: AdminNode[]}}
    'events.join': {args: OfferRef, result: EventDetails}
    'jobs.contract': {args: {title: string, description: string, price: number, hours: number}, result: JobDetails}
    'jobs.describe': {args: JobRef & {text: string}, result: JobDetails}
    'jobs.approve': {args: JobRef, result: JobDetails}
    'jobs.reject': {args: JobRef & {reason: string}, result: JobDetails}
    'jobs.dispute': {args: JobRef & {reason: string}, result: JobDetails}
    'jobs.withdraw': {args: JobRef, result: JobDetails}
    'jobs.cancel': {args: JobRef, result: JobDetails}
    'jobs.end': {args: JobRef, result: JobDetails}
    'jobs.take': {args: JobRef, result: JobDetails}
    'jobs.giveback': {args: JobRef, result: JobDetails}
    'jobs.done': {args: JobRef, result: JobDetails}
    'admin.jobs.disputes': {args: Record<string, never>, result: {disputes: DisputeRow[]}}
    'admin.jobs.settle': {args: JobRef & {percent: number}, result: {disputes: DisputeRow[]}}
    'events.leave': {args: OfferRef, result: EventDetails}
    'admin.offers.list': {args: Record<string, never>, result: {offers: AdminOfferRow[]}}
    'admin.offers.info': {args: OfferRef, result: AdminOffer}
    'admin.offers.create': {args: AdminOfferDraft, result: AdminOffer}
    'admin.offers.pause': {args: OfferRef, result: AdminOffer}
    'admin.offers.resume': {args: OfferRef, result: AdminOffer}
    'admin.offers.cancel': {args: OfferRef, result: AdminOffer}
    'admin.offers.open': {args: OfferRef & {count: number}, result: AdminOffer}
    'admin.offers.prize': {args: OfferRef & {place: number, outcome: 'paid' | 'failed'}, result: AdminOffer}
    'admin.events.list': {args: Record<string, never>, result: {events: AdminEventRow[]}}
    'admin.events.create': {args: AdminEventDraft, result: AdminEventRow}
    'admin.events.end': {args: ServerEventRef, result: AdminEventRow}
    'admin.events.cancel': {args: ServerEventRef, result: AdminEventRow}
    'admin.events.extend': {args: ServerEventRef & {duration: string}, result: AdminEventRow}
    'admin.events.budget': {args: ServerEventRef & {budget: number | null}, result: AdminEventRow}
    'admin.config.sync': {args: Record<string, never>, result: Record<string, never>}
    'admin.settings.get': {args: {file: SettingsFileName}, result: AdminSettingsFile}
    'admin.settings.history': {args: {file: SettingsFileName, page: number}, result: {revisions: AdminSettingsChange[], pageSize: number}}
    'admin.settings.revision': {args: {file: SettingsFileName, revision: number}, result: AdminSettingsRevision}
    'admin.settings.save': {args: {file: SettingsFileName, base: number, values: Record<string, unknown>}, result: AdminSettingsSaved}
    'admin.settings.rollback': {args: {file: SettingsFileName, revision: number, base: number}, result: AdminSettingsSaved}
    'admin.settings.icon': {args: {file: 'config.yml', base: number, png: string}, result: AdminSettingsSaved}
    'admin.setup.get': {args: Record<string, never>, result: AdminSetup}
    'admin.setup.mark': {args: {page: string, state: 'done' | 'skipped' | 'open' | 'dismissed'}, result: AdminSetup}
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
