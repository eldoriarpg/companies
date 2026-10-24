/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
/** @vitest-environment node */
import {describe, expect, it} from 'vitest'
import {NORMAL_CLOSURE, relayableCloseCode, upstreamAddress, upstreamHeaders} from './socketAddress'

/** Where the relay connects to, and which close codes it can pass on. */
describe('the dashboard stream relay', () => {
    it('keeps the path and the query of the request at the backend', () => {
        expect(upstreamAddress('http://localhost:8080', '/api/v1/me/stream?v=1'))
            .toBe('ws://localhost:8080/api/v1/me/stream?v=1')
    })

    it('reads an absolute request address the same way', () => {
        expect(upstreamAddress('http://backend:8080', 'http://localhost:3000/api/v1/me/stream?v=1'))
            .toBe('ws://backend:8080/api/v1/me/stream?v=1')
    })

    it('keeps a path the backend address carries', () => {
        expect(upstreamAddress('http://host/gateway/', '/api/v1/me/stream'))
            .toBe('ws://host/gateway/api/v1/me/stream')
    })

    it('speaks wss to a backend behind https', () => {
        expect(upstreamAddress('https://dash.example.org', '/api/v1/me/stream'))
            .toBe('wss://dash.example.org/api/v1/me/stream')
    })

    it('hands on the session cookie and the addresses', () => {
        const request = new Headers({'cookie': 'companies_session=abc', 'origin': 'https://dash.example.org', 'x-forwarded-for': '203.0.113.7'})
        expect(upstreamHeaders(request, '10.0.0.2')).toEqual({
            'cookie': 'companies_session=abc',
            'origin': 'https://dash.example.org',
            'x-forwarded-for': '203.0.113.7, 10.0.0.2',
        })
    })

    it('passes on the codes an application closes with', () => {
        expect(relayableCloseCode(4401)).toBe(4401)
        expect(relayableCloseCode(NORMAL_CLOSURE)).toBe(NORMAL_CLOSURE)
    })

    it('sends a normal closure for codes a client may not send', () => {
        expect(relayableCloseCode(1001)).toBe(NORMAL_CLOSURE)
        expect(relayableCloseCode(1006)).toBe(NORMAL_CLOSURE)
        expect(relayableCloseCode(undefined)).toBe(NORMAL_CLOSURE)
    })
})
