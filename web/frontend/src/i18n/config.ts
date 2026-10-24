/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */

/**
 * English is the language fallen back to; German and French are translated.
 *
 * <p>The messages are not part of this configuration. `@nuxtjs/i18n` reads the locale files named
 * in `nuxt.config.ts` and loads a language only once it is shown.
 */
export default defineI18nConfig(() => ({
    legacy: false,
    fallbackLocale: 'en',
}))
