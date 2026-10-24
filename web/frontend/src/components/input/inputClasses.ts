/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
/**
 * The class list of every bordered field typed into: 44 pixels high, square, its edge turning the accent colour
 * while it has focus. Text, number, amount and multi-line fields take their look from here, so the edge, the focus
 * and the disabled state stay the same across the form library. The width is up to the caller.
 */
export const FIELD_CLASSES =
    'min-h-11 border border-line bg-ground px-3 text-sm text-ink outline-none transition-colors' +
    ' hover:border-ink-faint focus:border-accent aria-invalid:border-loss' +
    ' disabled:cursor-not-allowed disabled:opacity-50'
