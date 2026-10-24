/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'

/**
 * Leaves the company after asking once more. The owner cannot leave; they hand the company over in game first. The
 * company leaves the dashboard through its patch, so leaving is told to the page; the question takes the focus.
 */
defineProps<{
  company: string
}>()

const emit = defineEmits<{
  left: []
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

/** Leaves the company. */
async function leave(): Promise<void> {
  if (await action.run('company.members.leave', {})) emit('left')
}
</script>

<template>
  <div class="flex flex-col gap-2 border-t border-line-soft px-3 py-2.5 md:px-4">
    <ActionNotice :problem="action.problem.value"/>
    <div v-if="asking" ref="question" class="flex flex-col gap-2">
      <span class="text-sm">{{ t('company.leaveQuestion', {company}) }}</span>
      <ButtonRow>
        <PrimaryButton :disabled="action.busy.value" @click="leave">{{ t('company.leaveConfirm') }}</PrimaryButton>
        <SecondaryButton @click="asking = false">{{ t('common.back') }}</SecondaryButton>
      </ButtonRow>
    </div>
    <ButtonRow v-else>
      <SecondaryButton @click="ask">{{ t('company.leave') }}</SecondaryButton>
    </ButtonRow>
  </div>
</template>
