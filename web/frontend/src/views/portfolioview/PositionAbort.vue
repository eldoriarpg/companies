/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import ConfirmStep from '@/components/feedback/ConfirmStep.vue'

/**
 * Gives an order the company works on back to the order book, after asking once more. Items already delivered go back
 * to the members who delivered them, as in game; the plugin checks the permission again. The order leaves the
 * positions through its patch, so the confirmation is told to the panel, which outlives the row.
 */
const props = defineProps<{
  order: number
  name: string
}>()

const emit = defineEmits<{
  aborted: [name: string]
}>()

const {t} = useI18n()
const action = useAction()

/** Aborts the order. */
async function abort(): Promise<void> {
  if (await action.run('orders.accept.abort', {order: props.order})) emit('aborted', props.name)
}
</script>

<template>
  <div class="flex flex-col gap-2 md:col-span-4">
    <ActionNotice :problem="action.problem.value"/>
    <ConfirmStep
        :trigger="t('positions.abort')"
        :question="t('positions.abortQuestion', {name})"
        :confirm="t('positions.abortConfirm')"
        :busy="action.busy.value"
        @confirmed="abort"
    />
  </div>
</template>
