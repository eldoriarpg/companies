/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */

/** Where the gateway answers while developing, unless the environment names another address. */
const BACKEND_URL = process.env.NUXT_BACKEND_URL || 'http://localhost:8080'

/**
 * Whether file changes are found by polling, which a container needs: changes made on the host do not reach its file
 * events.
 */
const POLLING = process.env.CHOKIDAR_USEPOLLING === 'true'

/**
 * The Companies dashboard.
 *
 * <p>Every page renders in the browser only: the dashboard belongs to the session cookie the gateway set, which the
 * page never reads, and its data comes from the gateway's API and stream. `/api` belongs to the gateway: in
 * development the dev proxies hand it on, HTTP and WebSocket alike, and the built server forwards it through
 * `server/middleware/backend-proxy.ts` and relays the stream through `server/routes/api/v1/me/stream.ts`.
 *
 * <p>`runtimeConfig.backendUrl` is read per request, so one build serves any gateway address. `trustedProxies`
 * (`NUXT_TRUSTED_PROXIES`, addresses and networks separated by commas) names the proxies in front of this server whose
 * `CF-Connecting-IP` is handed on to the gateway; none by default.
 */
export default defineNuxtConfig({
  devServer: {
    port: 4000,
  },

  compatibilityDate: '2025-06-08',

  srcDir: 'src/',

  ssr: false,

  app: {
    head: {
      meta: [
        {name: 'viewport', content: 'width=device-width, initial-scale=1, viewport-fit=cover'},
        {name: 'theme-color', content: '#0D1117'},
        {name: 'color-scheme', content: 'dark'},
        {name: 'apple-mobile-web-app-capable', content: 'yes'},
        {name: 'apple-mobile-web-app-status-bar-style', content: 'black-translucent'},
      ],
      link: [
        {rel: 'manifest', href: '/manifest.webmanifest'},
        {rel: 'apple-touch-icon', href: '/icon-192.png'},
      ],
    },
  },

  modules: ['@nuxt/test-utils/module', '@nuxt/eslint', '@nuxtjs/i18n'],

  eslint: {
    config: {
      autoInit: false,
    },
  },

  i18n: {
    restructureDir: 'src',
    langDir: 'i18n',
    vueI18n: 'i18n/config.ts',
    strategy: 'no_prefix',
    defaultLocale: 'en',
    detectBrowserLanguage: {
      useCookie: false,
      fallbackLocale: 'en',
    },
    locales: [
      {code: 'en', language: 'en', file: 'en.ts'},
      {code: 'de', language: 'de', file: 'de.ts'},
      {code: 'fr', language: 'fr', file: 'fr.ts'},
    ],
  },

  runtimeConfig: {
    backendUrl: BACKEND_URL,
    trustedProxies: '',
  },

  css: ['~/style.css'],

  nitro: {
    experimental: {
      websocket: true,
    },
    devProxy: {
      '/api': {target: BACKEND_URL, changeOrigin: true, ws: true},
    },
    routeRules: {
      '/sw.js': {headers: {'cache-control': 'no-cache'}},
      '/_nuxt/**': {headers: {'cache-control': 'public, max-age=31536000, immutable'}},
      '/_nuxt/builds/**': {headers: {'cache-control': 'no-cache'}},
    },
  },

  vite: {
    build: {
      sourcemap: false,
    },
    optimizeDeps: {
      include: ['axios', 'vue-i18n', 'reka-ui', '@vueuse/core'],
    },
    server: {
      allowedHosts: true,
      watch: POLLING ? {usePolling: true, interval: 300} : undefined,
      proxy: {
        '/api': {target: BACKEND_URL, changeOrigin: true, ws: true},
      },
    },
  },

  watchers: {
    chokidar: POLLING ? {usePolling: true, interval: 300} : {},
  },

  hooks: {
    /** Adds Tailwind's Vite plugin to every Vite config Nuxt builds, client and server alike. */
    'vite:extendConfig'(config: { plugins?: unknown[] }) {
      import('@tailwindcss/vite').then(m => {
        config.plugins ||= []
        config.plugins.push(m.default())
      })
    },
  },

  typescript: {
    strict: true,
  },

  components: {
    dirs: [
      {path: '~/components', pathPrefix: false},
    ],
  },
})
