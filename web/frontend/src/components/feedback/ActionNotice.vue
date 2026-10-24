/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {ActionProblem} from '~/composables/useAction'
import NoticeBanner from './NoticeBanner.vue'

/**
 * Says what went wrong with an action: the plugin's own sentence for a refusal, a warning to check before trying again
 * when the server did not answer in time, or a general message.
 */
defineProps<{
  problem: ActionProblem | null
}>()

const {t} = useI18n()
</script>

<template>
  <NoticeBanner v-if="problem" tone="problem">
    <template v-if="problem.kind === 'refused'">{{ problem.text }}</template>
    <template v-else-if="problem.kind === 'uncertain'">{{ t('common.actionUncertain') }}</template>
    <template v-else>{{ t('common.actionFailed') }}</template>
  </NoticeBanner>
</template>
