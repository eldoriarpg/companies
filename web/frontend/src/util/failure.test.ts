/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {AxiosError, AxiosHeaders} from 'axios'
import {describe, expect, it} from 'vitest'
import {describeFailure, FailureKind, kindOf} from './failure'

/**
 * An answer of the gateway with a status.
 *
 * @param status the status
 * @returns the error axios throws for it
 */
function answered(status: number): AxiosError {
    return new AxiosError('failed', 'ERR_BAD_RESPONSE', undefined, undefined, {
        status, statusText: '', data: {}, headers: {}, config: {headers: new AxiosHeaders()},
    })
}

/** Describing failed calls to the gateway. */
describe('a failure', () => {
    it('is told apart by what the reader can do about it', () => {
        expect(kindOf(new Error('network'))).toBe(FailureKind.OFFLINE)
        expect(kindOf(answered(401))).toBe(FailureKind.SIGNED_OUT)
        expect(kindOf(answered(429))).toBe(FailureKind.TOO_OFTEN)
        expect(kindOf(answered(504))).toBe(FailureKind.TIMEOUT)
        expect(kindOf(answered(503))).toBe(FailureKind.SERVER_OFFLINE)
        expect(kindOf(answered(500))).toBe(FailureKind.SERVER_FAULT)
        expect(kindOf(answered(418))).toBe(FailureKind.UNKNOWN)
    })

    it('names what happened and what to do', () => {
        expect(describeFailure(answered(429), key => key)).toEqual({
            kind: FailureKind.TOO_OFTEN,
            message: 'failure.tooOften.message',
            guidance: 'failure.tooOften.guidance',
        })
    })
})
