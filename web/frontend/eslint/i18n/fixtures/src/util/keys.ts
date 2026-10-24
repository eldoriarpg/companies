/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
export const LABELS = {help: 'help.link', data: 'literal.key'}

export function errorKey(code: string): string {
    return `errors.${code}`
}

export function kindKey(kind: string): string {
    return `kinds.${kind}`
}

export function concatenated(t: (key: string) => string, name: string): string {
    return t('concat.' + name)
}
