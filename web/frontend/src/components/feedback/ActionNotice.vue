/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {ActionProblem} from '~/composables/useAction'
import {describeKind} from '~/util/failure'
import Alert from './Alert.vue'

/**
 * Says what went wrong with an action: the plugin's own sentence for a refusal, a warning to check before trying again
 * when the server did not answer in time, or what failed and what to do about it.
 */
const props = defineProps<{
  problem: ActionProblem | null
}>()

const {t} = useI18n()
const failure = computed(() => (props.problem?.kind === 'failed' ? describeKind(props.problem.cause, t) : null))
</script>

<template>
  <Alert v-if="problem" variant="problem">
    <template v-if="problem.kind === 'refused'">{{ problem.text }}</template>
    <template v-else-if="problem.kind === 'uncertain'">{{ t('common.actionUncertain') }}</template>
    <template v-else-if="failure">{{ failure.message }} {{ failure.guidance }}</template>
  </Alert>
</template>
