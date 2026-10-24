/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {gatewayFailure} from '~/api/gateway'

/**
 * What kind of failure a call to the gateway was, which decides what the reader can do about it.
 */
export const FailureKind = {
    /** The request never reached the gateway, or the network dropped it. */
    OFFLINE: 'OFFLINE',
    /** The gateway answered, but the server did not answer it in time. */
    TIMEOUT: 'TIMEOUT',
    /** The session ended. */
    SIGNED_OUT: 'SIGNED_OUT',
    /** Asked too often, too quickly. */
    TOO_OFTEN: 'TOO_OFTEN',
    /** The server is not connected to the gateway right now. */
    SERVER_OFFLINE: 'SERVER_OFFLINE',
    /** The gateway or the server broke. */
    SERVER_FAULT: 'SERVER_FAULT',
    /** None of the above. */
    UNKNOWN: 'UNKNOWN',
} as const

/** A kind of failure. */
export type FailureKindName = (typeof FailureKind)[keyof typeof FailureKind]

/**
 * A failure in the terms a reader can act on: what happened and what to do about it.
 */
export interface Failure {
    kind: FailureKindName
    message: string
    guidance: string
}

/** How a failure is worded; the caller hands in its translator, so this stays free of the i18n instance. */
export type Translate = (key: string) => string

/**
 * Says what a failed call to the gateway means for the reader.
 *
 * @param error what the call threw
 * @param t the translator
 * @returns the failure
 */
export function describeFailure(error: unknown, t: Translate): Failure {
    return describeKind(kindOf(error), t)
}

/**
 * Words a kind of failure.
 *
 * @param kind the kind
 * @param t the translator
 * @returns the failure
 */
export function describeKind(kind: FailureKindName, t: Translate): Failure {
    const key = kind.toLowerCase().replace(/_(\w)/g, (_, letter: string) => letter.toUpperCase())
    return {kind, message: t(`failure.${key}.message`), guidance: t(`failure.${key}.guidance`)}
}

/**
 * The kind of a failed call.
 *
 * @param error what the call threw
 * @returns the kind
 */
export function kindOf(error: unknown): FailureKindName {
    const {status} = gatewayFailure(error)
    if (status === 0) return FailureKind.OFFLINE
    if (status === 401) return FailureKind.SIGNED_OUT
    if (status === 429) return FailureKind.TOO_OFTEN
    if (status === 504) return FailureKind.TIMEOUT
    if (status === 502 || status === 503) return FailureKind.SERVER_OFFLINE
    if (status >= 500) return FailureKind.SERVER_FAULT
    return FailureKind.UNKNOWN
}
