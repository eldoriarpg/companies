/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */

/**
 * The service worker of the dashboard. It keeps the app shell, and nothing else, so the dashboard opens from the home
 * screen at once and shows its own page when the network is gone: the page itself, the built scripts and styles with
 * their hashed names, the fonts and the icons. Nothing below `/api` is ever stored, no data, no server icon, no
 * session; those always go to the network.
 *
 * The cache is named after the build (`/_nuxt/builds/latest.json`, which itself is never cached). Whenever a page is
 * requested the worker looks at the build that is current, kept alive until it is done; a new one gets a cache of its own and the
 * caches of older builds are deleted, so assets of old builds do not pile up.
 */
const PREFIX = 'companies-shell-'

/** What is stored for every build. */
const SHELL = ['/', '/manifest.webmanifest', '/icon.svg', '/icon-192.png', '/icon-512.png']

/** Where Nuxt names the current build, and keeps its build metadata. */
const BUILDS = '/_nuxt/builds/'

/**
 * Whether a request belongs to the app shell and may be kept.
 *
 * @param url the request's address
 * @returns true for the built assets and the icons of this origin, the build metadata left out
 */
function isShell(url) {
    if (url.origin !== self.location.origin || url.pathname.startsWith('/api/') || url.pathname.startsWith(BUILDS)) return false
    return url.pathname.startsWith('/_nuxt/') || SHELL.includes(url.pathname)
}

/**
 * The cache of the newest build the worker knows.
 *
 * @returns its name, or nothing before the first build was seen
 */
async function currentCache() {
    return (await caches.keys()).find(key => key.startsWith(PREFIX)) ?? null
}

/**
 * Looks at the build that is current, gives a new one its cache with the shell, and deletes the caches of the others.
 */
async function followBuild() {
    try {
        const response = await fetch(`${BUILDS}latest.json`, {cache: 'no-store'})
        if (!response.ok) return
        const {id} = await response.json()
        const name = `${PREFIX}${id}`
        const keys = await caches.keys()
        if (!keys.includes(name)) await (await caches.open(name)).addAll(SHELL)
        await Promise.all(keys.filter(key => key !== name).map(key => caches.delete(key)))
    } catch {
        return
    }
}

/**
 * Answers a page request from the network, falling back to the stored shell when the network is gone.
 *
 * @param request the navigation
 * @returns the answer
 */
async function page(request) {
    try {
        return await fetch(request)
    } catch {
        return (await caches.match('/')) ?? Response.error()
    }
}

/**
 * Answers a shell request from the cache, fetching and storing it the first time.
 *
 * @param request the request
 * @returns the answer
 */
async function shell(request) {
    const cached = await caches.match(request)
    if (cached) return cached
    const response = await fetch(request)
    const name = await currentCache()
    if (response.ok && name) await (await caches.open(name)).put(request, response.clone())
    return response
}

self.addEventListener('install', event => {
    event.waitUntil(followBuild().then(() => self.skipWaiting()))
})

self.addEventListener('activate', event => {
    event.waitUntil(self.clients.claim())
})

self.addEventListener('fetch', event => {
    const url = new URL(event.request.url)
    if (event.request.method !== 'GET' || url.pathname.startsWith('/api/')) return
    if (event.request.mode === 'navigate') {
        event.respondWith(page(event.request))
        event.waitUntil(followBuild())
    } else if (isShell(url)) event.respondWith(shell(event.request))
})
