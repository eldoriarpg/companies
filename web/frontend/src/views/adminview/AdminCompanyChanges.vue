/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import ConfirmStep from '@/components/feedback/ConfirmStep.vue'
import TextInput from '@/components/input/TextInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

/**
 * What an admin changes about a company: a free rename, a member made owner, and the company dissolved after asking
 * once more, with its orders released, delivered items back to the workers and the treasury paid to the owner.
 */
const props = defineProps<{
  company: string
}>()

const emit = defineEmits<{
  changed: [text: string, renamed?: string]
  deleted: [text: string]
}>()

const {t} = useI18n()
const action = useAction()
const name = ref('')
const owner = ref('')

/** Renames the company. */
async function rename(): Promise<void> {
  const result = await action.run('admin.companies.rename', {company: props.company, name: name.value.trim()})
  if (!result) return
  name.value = ''
  emit('changed', t('admin.companies.renamed', {name: result.company}), result.company)
}

/** Makes the typed member the owner. */
async function transfer(): Promise<void> {
  const result = await action.run('admin.companies.transfer-owner', {company: props.company, player: owner.value.trim()})
  if (!result) return
  owner.value = ''
  emit('changed', t('admin.companies.transferred', {name: result.owner, company: result.company}))
}

/** Dissolves the company. */
async function remove(): Promise<void> {
  const result = await action.run('admin.companies.delete', {company: props.company})
  if (result) emit('deleted', t('admin.companies.deleted', {company: result.company}))
}
</script>

<template>
  <PanelBox padded :aria-label="t('admin.companies.changes', {company})" data-testid="admin-company">
    <SectionHeader>{{ t('admin.companies.changes', {company}) }}</SectionHeader>
    <ActionNotice :problem="action.problem.value"/>
    <form class="flex flex-wrap items-end gap-2" @submit.prevent="rename">
      <TextInput v-model="name" :label="t('admin.companies.newName')"/>
      <SecondaryButton type="submit" :disabled="name.trim() === '' || action.busy.value">{{ t('admin.companies.rename') }}</SecondaryButton>
    </form>
    <form class="flex flex-wrap items-end gap-2" @submit.prevent="transfer">
      <TextInput v-model="owner" :label="t('admin.companies.newOwner')"/>
      <SecondaryButton type="submit" :disabled="owner.trim() === '' || action.busy.value">{{ t('admin.companies.transfer') }}</SecondaryButton>
    </form>
    <ConfirmStep
        :trigger="t('admin.companies.delete')"
        :question="t('admin.companies.deleteQuestion', {company})"
        :confirm="t('admin.companies.deleteConfirm')"
        :busy="action.busy.value"
        @confirmed="remove"
    />
  </PanelBox>
</template>
