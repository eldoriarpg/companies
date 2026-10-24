/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */

/** Which way a value moved, for its colour and its sign. */
export type Trend = 'up' | 'down' | 'flat'

/** A change below this many percent reads as no change. */
const FLAT_PERCENT = 0.05

/**
 * An amount of money with two decimals and the reader's grouping, as a trading app shows prices.
 *
 * @param value the amount
 * @param locale the reader's language
 * @returns the amount as text
 */
export function money(value: number, locale: string): string {
    return new Intl.NumberFormat(locale, {minimumFractionDigits: 2, maximumFractionDigits: 2}).format(value)
}

/**
 * A count with the reader's grouping.
 *
 * @param value the count
 * @param locale the reader's language
 * @returns the count as text
 */
export function count(value: number, locale: string): string {
    return new Intl.NumberFormat(locale, {maximumFractionDigits: 0}).format(value)
}

/**
 * A large count shortened, such as 9.4k for 9,400.
 *
 * @param value the count
 * @param locale the reader's language
 * @returns the count as text
 */
export function compact(value: number, locale: string): string {
    return new Intl.NumberFormat(locale, {notation: 'compact', maximumFractionDigits: 1}).format(value)
}

/**
 * Which way a change went.
 *
 * @param change the change, in percent or absolute
 * @returns its trend
 */
export function trendOf(change: number): Trend {
    if (Math.abs(change) < FLAT_PERCENT) return 'flat'
    return change > 0 ? 'up' : 'down'
}

/**
 * A change in percent with its arrow: ▲4.1% up, ▼2.0% down, 0.0% flat.
 *
 * @param change the change in percent
 * @param locale the reader's language
 * @returns the change as text
 */
export function percentChange(change: number, locale: string): string {
    const text = new Intl.NumberFormat(locale, {minimumFractionDigits: 1, maximumFractionDigits: 1}).format(Math.abs(change))
    return `${arrow(trendOf(change))}${text}%`
}

/**
 * A change of money with its arrow and the percent it makes of the value before.
 *
 * @param delta the change
 * @param before the value before it
 * @param locale the reader's language
 * @returns the change as text, such as ▲ 1,240.00 (7.2%)
 */
export function moneyChange(delta: number, before: number, locale: string): string {
    const percent = before === 0 ? 0 : delta / Math.abs(before) * 100
    const share = new Intl.NumberFormat(locale, {minimumFractionDigits: 1, maximumFractionDigits: 1}).format(Math.abs(percent))
    const sign = arrow(trendOf(delta))
    return `${sign}${sign ? ' ' : ''}${money(Math.abs(delta), locale)} (${share}%)`
}

/**
 * The sign of a trend: ▲ for up, ▼ for down, nothing for flat.
 *
 * @param trend the trend
 * @returns the sign
 */
export function arrow(trend: Trend): string {
    if (trend === 'up') return '▲'
    if (trend === 'down') return '▼'
    return ''
}

/**
 * The hours left until a moment, rounded up, never below zero.
 *
 * @param until the moment
 * @param now the current time
 * @returns the hours left
 */
export function hoursLeft(until: string, now: Date): number {
    return Math.max(0, Math.ceil((new Date(until).getTime() - now.getTime()) / 3_600_000))
}

/**
 * When something happened, short: the 24 hour clock time today, the weekday this week, the date before.
 *
 * @param time the moment
 * @param now the current time
 * @param locale the reader's language
 * @returns the moment as text
 */
export function when(time: string, now: Date, locale: string): string {
    const moment = new Date(time)
    if (moment.toDateString() === now.toDateString()) {
        return new Intl.DateTimeFormat(locale, {hour: '2-digit', minute: '2-digit', hourCycle: 'h23'}).format(moment)
    }
    const days = (now.getTime() - moment.getTime()) / 86_400_000
    if (days < 6) return new Intl.DateTimeFormat(locale, {weekday: 'short'}).format(moment)
    return new Intl.DateTimeFormat(locale, {day: 'numeric', month: 'short'}).format(moment)
}
