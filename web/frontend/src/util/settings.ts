/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import type {AdminSetting} from '~/api/gateway'

/** Translates a key with values, as `useI18n().t` does. */
type Translate = (key: string, values?: Record<string, unknown>) => string

/**
 * Why the server refused a setting's value, as the admin reads it. A number outside its bounds names the bounds: only
 * the lower one when there is no upper one.
 *
 * @param t translates a message key
 * @param setting the setting
 * @param reason the server's reason, such as `range` or `whole`
 * @returns the sentence
 */
export function settingProblem(t: Translate, setting: Pick<AdminSetting, 'min' | 'max'>, reason: string): string {
    if (reason !== 'range') return t(`admin.settings.problem.${reason}`)
    if (setting.max == null) return t('admin.settings.problem.atLeast', {min: setting.min ?? 0})
    return t('admin.settings.problem.range', {min: setting.min ?? 0, max: setting.max})
}

/**
 * Whether a draft value differs from the value it started from, compared as the server would receive them.
 *
 * @param draft the edited value
 * @param original the value as loaded
 * @returns true when it changed
 */
export function changedValue(draft: unknown, original: unknown): boolean {
    return JSON.stringify(draft) !== JSON.stringify(original)
}
