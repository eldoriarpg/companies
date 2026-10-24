/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

/**
 * Gives an order the company works on back to the order book, after asking once more. Items already delivered go back
 * to the members who delivered them, as in game; the plugin checks the permission again. The question takes the focus.
 * The order leaves the positions through its patch, so the confirmation is told to the panel, which outlives the row.
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
const asking = ref(false)
const question = useTemplateRef<HTMLElement>('question')

/** Asks once more and moves the focus to the question. */
async function ask(): Promise<void> {
  asking.value = true
  await nextTick()
  question.value?.querySelector<HTMLElement>('button')?.focus()
}

/** Aborts the order. */
async function abort(): Promise<void> {
  if (await action.run('orders.accept.abort', {order: props.order})) emit('aborted', props.name)
}
</script>

<template>
  <div class="flex flex-col gap-2 md:col-span-4">
    <ActionNotice :problem="action.problem.value"/>
    <div v-if="asking" ref="question" class="flex flex-col gap-2">
      <span class="text-sm">{{ t('positions.abortQuestion', {name}) }}</span>
      <ButtonRow>
        <PrimaryButton :disabled="action.busy.value" @click="abort">{{ t('positions.abortConfirm') }}</PrimaryButton>
        <SecondaryButton @click="asking = false">{{ t('common.back') }}</SecondaryButton>
      </ButtonRow>
    </div>
    <ButtonRow v-else>
      <SecondaryButton @click="ask">{{ t('positions.abort') }}</SecondaryButton>
    </ButtonRow>
  </div>
</template>
