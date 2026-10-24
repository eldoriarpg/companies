/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import type {Ref} from 'vue'

/** Where Cloudflare serves the Turnstile script, rendered on request. */
export const TURNSTILE_SCRIPT = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'

const SCRIPT_ID = 'cf-turnstile-script'

/** The part of Cloudflare's Turnstile API the login uses. */
interface TurnstileApi {
    render(container: HTMLElement, options: Record<string, unknown>): string
    reset(widget: string): void
    remove(widget: string): void
}

/**
 * The Turnstile API once its script has loaded, loading it the first time.
 *
 * @returns the API
 */
function turnstileApi(): Promise<TurnstileApi> {
    const loaded = (window as unknown as {turnstile?: TurnstileApi}).turnstile
    if (loaded) return Promise.resolve(loaded)
    return new Promise((resolve, reject) => {
        const existing = document.getElementById(SCRIPT_ID) as HTMLScriptElement | null
        const script = existing ?? Object.assign(document.createElement('script'), {id: SCRIPT_ID, src: TURNSTILE_SCRIPT, async: true})
        script.addEventListener('load', () => resolve((window as unknown as {turnstile: TurnstileApi}).turnstile))
        script.addEventListener('error', () => reject(new Error('Turnstile could not be loaded')))
        if (!existing) document.head.append(script)
    })
}

/**
 * Cloudflare's Turnstile challenge on the code form, where the gateway names a site key. Most players never see it;
 * the token it hands out goes along with the code, and a refused code asks for a fresh one.
 *
 * @param siteKey the gateway's site key, none when it asks for no challenge
 * @param container the element the widget is drawn in
 * @param language the language the widget speaks
 * @returns the token, whether the challenge failed, and a reset
 */
export function useTurnstile(siteKey: Ref<string | undefined>, container: Ref<HTMLElement | null>, language: Ref<string>) {
    const token = ref<string | null>(null)
    const failed = ref(false)
    const widget = shallowRef<{api: TurnstileApi, id: string} | null>(null)

    /** Draws the widget once both the key and its element are there. */
    async function render(): Promise<void> {
        if (!siteKey.value || !container.value || widget.value) return
        try {
            const api = await turnstileApi()
            const id = api.render(container.value, {
                'sitekey': siteKey.value,
                'theme': 'dark',
                'language': language.value,
                'callback': (value: string) => {
                    token.value = value
                },
                'expired-callback': () => {
                    token.value = null
                },
                'error-callback': () => {
                    failed.value = true
                },
            })
            widget.value = {api, id}
        } catch {
            failed.value = true
        }
    }

    watch([siteKey, container], () => void render(), {immediate: true})
    onBeforeUnmount(() => widget.value?.api.remove(widget.value.id))

    return {
        needed: computed(() => Boolean(siteKey.value)),
        token: readonly(token),
        failed: readonly(failed),

        /** Asks for a fresh token, after the gateway refused a code. */
        reset(): void {
            token.value = null
            if (widget.value) widget.value.api.reset(widget.value.id)
        },
    }
}
