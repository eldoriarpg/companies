/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
/** @vitest-environment node */
import {describe, expect, it} from 'vitest'
import {NORMAL_CLOSURE, relayableCloseCode, sendableCloseCode, UNAVAILABLE, upstreamAddress, upstreamHeaders} from './socketAddress'
import {isTrusted, parseRanges} from './trustedProxies'

/** Where the relay connects to, what it hands on, and which close codes it can pass on. */
describe('the socket relay', () => {
    it('keeps the path and the query of the request at the backend', () => {
        expect(upstreamAddress('http://localhost:8080', '/api/v1/me/stream?v=1'))
            .toBe('ws://localhost:8080/api/v1/me/stream?v=1')
        expect(upstreamAddress('http://backend:8080', 'http://localhost:3000/api/v1/servers/connect'))
            .toBe('ws://backend:8080/api/v1/servers/connect')
    })

    it('keeps a path the backend address carries and speaks wss behind https', () => {
        expect(upstreamAddress('http://host/gateway/', '/api/v1/me/stream')).toBe('ws://host/gateway/api/v1/me/stream')
        expect(upstreamAddress('https://dash.example.org', '/api/v1/me/stream')).toBe('wss://dash.example.org/api/v1/me/stream')
    })

    it('hands on the cookie where the route needs it and appends the address', () => {
        const request = new Headers({'cookie': 'companies_session=abc', 'origin': 'https://dash.example.org', 'x-forwarded-for': '203.0.113.7'})
        expect(upstreamHeaders(request, '10.0.0.2', {cookie: true, trustedPeer: false})).toEqual({
            'cookie': 'companies_session=abc',
            'origin': 'https://dash.example.org',
            'x-forwarded-for': '203.0.113.7, 10.0.0.2',
        })
        expect(upstreamHeaders(request, '10.0.0.2', {cookie: false, trustedPeer: false})).not.toHaveProperty('cookie')
    })

    it('hands on the cookie header as it is, the gateway\'s __Host- name included', () => {
        const cookie = '__Host-companies_session=s3cr3t; other=1'
        expect(upstreamHeaders(new Headers({cookie}), undefined, {cookie: true, trustedPeer: false}).cookie).toBe(cookie)
    })

    it('believes Cloudflare\'s client address only from a trusted proxy', () => {
        const request = new Headers({'cf-connecting-ip': '198.51.100.1'})
        expect(upstreamHeaders(request, '203.0.113.9', {cookie: false, trustedPeer: false})).not.toHaveProperty('cf-connecting-ip')
        expect(upstreamHeaders(request, '10.0.0.2', {cookie: false, trustedPeer: true})['cf-connecting-ip']).toBe('198.51.100.1')
    })

    it('cuts a client\'s close codes to what a client may send', () => {
        expect(relayableCloseCode(4409)).toBe(4409)
        expect(relayableCloseCode(NORMAL_CLOSURE)).toBe(NORMAL_CLOSURE)
        expect(relayableCloseCode(1001)).toBe(NORMAL_CLOSURE)
        expect(relayableCloseCode(undefined)).toBe(NORMAL_CLOSURE)
    })

    it('passes the backend\'s close codes on unchanged where they can be sent', () => {
        for (const code of [1000, 1001, 1008, 1011, 4401, 4403, 4429]) expect(sendableCloseCode(code, UNAVAILABLE)).toBe(code)
        expect(sendableCloseCode(1006, UNAVAILABLE)).toBe(UNAVAILABLE)
        expect(sendableCloseCode(1005, UNAVAILABLE)).toBe(UNAVAILABLE)
        expect(sendableCloseCode(undefined, UNAVAILABLE)).toBe(UNAVAILABLE)
    })
})

/** The proxies in front of the frontend whose client address is believed. */
describe('trusted proxies', () => {
    const ranges = parseRanges('127.0.0.1, 10.0.0.0/8, fd00::/8, nonsense, 300.1.1.1/40')

    it('trusts addresses in the listed networks', () => {
        expect(isTrusted('10.20.30.40', ranges)).toBe(true)
        expect(isTrusted('::ffff:10.1.2.3', ranges)).toBe(true)
        expect(isTrusted('127.0.0.1', ranges)).toBe(true)
        expect(isTrusted('fd12:3456::1', ranges)).toBe(true)
    })

    it('trusts nothing else', () => {
        expect(isTrusted('11.0.0.1', ranges)).toBe(false)
        expect(isTrusted('fe80::1', ranges)).toBe(false)
        expect(isTrusted(undefined, ranges)).toBe(false)
        expect(isTrusted('10.0.0.1', parseRanges(''))).toBe(false)
    })
})
