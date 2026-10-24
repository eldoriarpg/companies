/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */

/**
 * The service worker of the dashboard. It keeps the app shell, and nothing else, so the dashboard opens from the home
 * screen at once and shows its own page when the network is gone: the page itself, the built scripts and styles with
 * their hashed names, the fonts and the icons. Nothing below `/api` is ever stored, no data, no server icon, no
 * session; those always go to the network. A new version of this file empties the caches of the old one.
 */
const CACHE = 'companies-shell-v1'

/** What is stored when the worker is installed. */
const SHELL = ['/', '/manifest.webmanifest', '/icon.svg', '/icon-192.png', '/icon-512.png']

/**
 * Whether a request belongs to the app shell and may be kept.
 *
 * @param url the request's address
 * @returns true for the built assets and the icons of this origin
 */
function isShell(url) {
    if (url.origin !== self.location.origin || url.pathname.startsWith('/api/')) return false
    return url.pathname.startsWith('/_nuxt/') || SHELL.includes(url.pathname)
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
    if (response.ok) {
        const cache = await caches.open(CACHE)
        await cache.put(request, response.clone())
    }
    return response
}

self.addEventListener('install', event => {
    event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(SHELL)).then(() => self.skipWaiting()))
})

self.addEventListener('activate', event => {
    event.waitUntil(caches.keys()
        .then(keys => Promise.all(keys.filter(key => key !== CACHE).map(key => caches.delete(key))))
        .then(() => self.clients.claim()))
})

self.addEventListener('fetch', event => {
    const url = new URL(event.request.url)
    if (event.request.method !== 'GET' || url.pathname.startsWith('/api/')) return
    if (event.request.mode === 'navigate') event.respondWith(page(event.request))
    else if (isShell(url)) event.respondWith(shell(event.request))
})
