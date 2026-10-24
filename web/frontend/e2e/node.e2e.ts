/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {expect, test} from '@playwright/test'
import WebSocket from 'ws'

/**
 * Opens a node connection through the frontend and collects what comes back.
 *
 * @param base the frontend's address
 * @returns the socket, the messages it got and its close code once closed
 */
async function connect(base: string) {
    const socket = new WebSocket(`${base.replace(/^http/, 'ws')}/api/v1/servers/connect`)
    const messages: {size: number, forwardedFor: string | null, cookie: string | null}[] = []
    const closed = new Promise<number>(resolve => socket.on('close', code => resolve(code)))
    socket.on('message', data => messages.push(JSON.parse(String(data))))
    await new Promise((resolve, reject) => {
        socket.once('open', resolve)
        socket.once('error', reject)
    })
    return {socket, messages, closed}
}

/** The plugin's node connection, which reaches the gateway through the published frontend. */
test.describe('node connection', () => {
    test('relays large messages and the gateway\'s close codes', async ({baseURL}, info) => {
        test.skip(info.project.name !== 'chromium', 'no browser involved')
        const {socket, messages, closed} = await connect(baseURL as string)
        const snapshot = 'x'.repeat(3 * 1024 * 1024)
        socket.send(snapshot)
        await expect.poll(() => messages.length).toBe(1)
        expect(messages[0]).toMatchObject({size: snapshot.length, cookie: null})
        expect(messages[0]?.forwardedFor).toBeTruthy()
        socket.send('bye')
        expect(await closed).toBe(4409)
    })

    test('closes a node that sends more than 4 MB at once', async ({baseURL}, info) => {
        test.skip(info.project.name !== 'chromium', 'no browser involved')
        const {socket, messages, closed} = await connect(baseURL as string)
        socket.send('x'.repeat(5 * 1024 * 1024))
        expect(await closed).toBe(1009)
        expect(messages).toEqual([])
    })
})
