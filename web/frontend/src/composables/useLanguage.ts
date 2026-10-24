/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {rememberLanguage} from '~/util/language'

/**
 * The dashboard's language among those it has, each named in its own language. The browser remembers the choice;
 * without one the browser's language is used.
 *
 * @returns the choices and the language picked
 */
export function useLanguage() {
    const {locale, locales, setLocale} = useI18n()

    const choices = computed(() => locales.value.map(entry => {
        const code = typeof entry === 'string' ? entry : entry.code
        const name = new Intl.DisplayNames([code], {type: 'language'}).of(code) ?? code
        return {value: code, label: name.charAt(0).toLocaleUpperCase(code) + name.slice(1)}
    }))

    return {
        choices,
        picked: computed({
            get: () => locale.value as string,
            set: (code: string) => {
                void setLocale(code as typeof locale.value)
                rememberLanguage(code)
            },
        }),
    }
}
