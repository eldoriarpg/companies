/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {readonly, ref, shallowRef, type Ref, type ShallowRef} from 'vue'

/**
 * Whether this module was built for the server.
 *
 * <p>Vite's own flag rather than Nuxt's `import.meta.server`, which the test runner fixes to the
 * browser in every environment. Vite's follows the environment a test runs in, so a test in the
 * `node` environment sees the server side of the helpers below and one in a DOM environment sees
 * the browser side, exactly as the two bundles do.
 */
function builtForServer(): boolean {
    return import.meta.env.SSR
}

/**
 * State only the browser writes: toasts, prompts, caches of what the browser fetched, counters and
 * timers. A plain ref in the browser.
 *
 * <p>On the server it is read-only. A module is evaluated once per server process, so a writable
 * ref there would be one value shared by every request; a read-only one keeps the literal initial
 * value for all of them. A write is ignored there, and in development Vue warns about it, which is
 * what turns a server render that reaches for browser state into a failing test.
 *
 * @param initial the value every server render sees and the browser starts from; a literal, never
 *                something read from the browser, or the first render in the browser disagrees with
 *                the page the server sent
 */
export function browserRef<T>(initial: T): Ref<T> {
    if (builtForServer()) return readonly(ref(initial)) as Ref<T>
    return ref(initial) as Ref<T>
}

/**
 * {@link browserRef} without deep reactivity in the browser, for values Vue should not proxy: a
 * `Blob`, promises and their callbacks, or a record of counters and timers changed in place.
 *
 * @param initial the value every server render sees and the browser starts from
 */
export function browserShallowRef<T>(initial: T): ShallowRef<T> {
    if (builtForServer()) return readonly(shallowRef(initial)) as unknown as ShallowRef<T>
    return shallowRef(initial)
}
