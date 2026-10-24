/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {rememberedLanguage} from '~/util/language'

/** Where the service worker is served. */
const WORKER = '/sw.js'

/**
 * What the browser does once when the app starts: shows the language picked on an earlier visit, and registers the
 * service worker that keeps the app shell for the home screen. The worker never holds data, see `public/sw.js`; in
 * development it is left out, so every change shows at once.
 */
export default defineNuxtPlugin({
    name: 'companies-setup',
    dependsOn: ['i18n:plugin'],
    async setup(nuxtApp) {
        const i18n = nuxtApp.$i18n
        const language = i18n.availableLocales.find(code => code === rememberedLanguage())
        if (language) await i18n.setLocale(language)
        if (!import.meta.dev && 'serviceWorker' in navigator) {
            void navigator.serviceWorker.register(WORKER).catch(() => undefined)
        }
    },
})
