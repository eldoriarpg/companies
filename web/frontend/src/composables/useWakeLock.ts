/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {useEventListener} from '@vueuse/core'

const WAKE_LOCK_KEY = 'companies.wakeLock'

/** The part of the Screen Wake Lock API the dashboard uses. */
interface WakeLockSentinel {
    release(): Promise<void>
    addEventListener(type: 'release', listener: () => void): void
}

/**
 * Keeps the screen on while the home view is open, for a phone lying next to the game as its dashboard. Whether the
 * reader wants it is remembered; the lock itself is released by the browser when the page is hidden and taken again
 * when it shows. Browsers without the Screen Wake Lock API show no switch.
 *
 * @returns whether it is supported, wanted and held, and the switch
 */
export function useWakeLock() {
    const supported = ref(false)
    const wanted = ref(false)
    const held = ref(false)
    const sentinel = shallowRef<WakeLockSentinel | null>(null)

    /** Takes the lock while it is wanted and the page shows. */
    async function take(): Promise<void> {
        if (!supported.value || !wanted.value || sentinel.value || document.visibilityState !== 'visible') return
        try {
            const lock = await (navigator as unknown as {wakeLock: {request(type: 'screen'): Promise<WakeLockSentinel>}}).wakeLock.request('screen')
            lock.addEventListener('release', () => {
                sentinel.value = null
                held.value = false
            })
            sentinel.value = lock
            held.value = true
        } catch {
            held.value = false
        }
    }

    /** Lets the screen turn off again. */
    async function release(): Promise<void> {
        const lock = sentinel.value
        sentinel.value = null
        held.value = false
        await lock?.release()
    }

    /**
     * Remembers whether the reader wants the screen on.
     *
     * @param value the choice
     */
    function remember(value: boolean): void {
        try {
            localStorage.setItem(WAKE_LOCK_KEY, value ? 'on' : 'off')
        } catch {
            return
        }
    }

    onMounted(() => {
        supported.value = 'wakeLock' in navigator
        try {
            wanted.value = localStorage.getItem(WAKE_LOCK_KEY) === 'on'
        } catch {
            wanted.value = false
        }
        void take()
    })
    useEventListener(document, 'visibilitychange', () => void take())
    onBeforeUnmount(() => void release())

    return {
        supported: readonly(supported),
        held: readonly(held),
        wanted: computed({
            get: () => wanted.value,
            set: (value: boolean) => {
                wanted.value = value
                remember(value)
                if (value) void take()
                else void release()
            },
        }),
    }
}
