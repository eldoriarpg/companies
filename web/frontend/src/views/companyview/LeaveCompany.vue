/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import ConfirmStep from '@/components/feedback/ConfirmStep.vue'

/**
 * Leaves the company after asking once more. The owner cannot leave; they hand the company over first. The
 * company leaves the dashboard through its patch, so leaving is told to the page.
 */
defineProps<{
  company: string
}>()

const emit = defineEmits<{
  left: []
}>()

const {t} = useI18n()
const action = useAction()

/** Leaves the company. */
async function leave(): Promise<void> {
  if (await action.run('company.members.leave', {})) emit('left')
}
</script>

<template>
  <div class="flex flex-col gap-2 border-t border-line-soft px-3 py-2.5 md:px-4">
    <ActionNotice :problem="action.problem.value"/>
    <ConfirmStep
        :trigger="t('company.leave')"
        :question="t('company.leaveQuestion', {company})"
        :confirm="t('company.leaveConfirm')"
        :busy="action.busy.value"
        @confirmed="leave"
    />
  </div>
</template>
