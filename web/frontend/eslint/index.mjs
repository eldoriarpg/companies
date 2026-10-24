/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {i18nRules} from './i18n/index.mjs'
import buttonRow from './rules/button-row.mjs'
import contextTitle from './rules/context-title.mjs'
import maxFunctionLines from './rules/max-function-lines.mjs'
import noDeadButtonSize from './rules/no-dead-button-size.mjs'
import noHandKeptEnum from './rules/no-hand-kept-enum.mjs'
import noModuleState from './rules/no-module-state.mjs'
import noStackedInlineText from './rules/no-stacked-inline-text.mjs'
import repeatedClassPattern from './rules/repeated-class-pattern.mjs'
import routeViewContent from './rules/route-view-content.mjs'
import sectionDensity from './rules/section-density.mjs'
import styledElements from './rules/styled-elements.mjs'
import templateBlockSize from './rules/template-block-size.mjs'

/**
 * The rules this project holds its frontend to that no published plugin knows about.
 *
 * <p>Each one guards a convention of the frontend, reads the code through the parsers ESLint
 * already uses, and is registered here under the `companies` prefix.
 */
export default {
    meta: {name: 'companies'},
    rules: {
        ...i18nRules,
        'button-row': buttonRow,
        'context-title': contextTitle,
        'max-function-lines': maxFunctionLines,
        'no-dead-button-size': noDeadButtonSize,
        'no-hand-kept-enum': noHandKeptEnum,
        'no-module-state': noModuleState,
        'no-stacked-inline-text': noStackedInlineText,
        'repeated-class-pattern': repeatedClassPattern,
        'route-view-content': routeViewContent,
        'section-density': sectionDensity,
        'styled-elements': styledElements,
        'template-block-size': templateBlockSize,
    },
}
