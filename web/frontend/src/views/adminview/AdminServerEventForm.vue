/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminEventRow, ServerEventKind} from '~/api/gateway'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import SelectInput from '@/components/input/SelectInput.vue'
import TextInput from '@/components/input/TextInput.vue'
import {eventDraftOf, type ServerEventForm} from '~/util/serverEvents'

/**
 * The form of a new server event, as `/companyadmin event create` takes it: its name, kind and value, the materials and
 * company levels it is limited to, its start and end, and for a reward multiplier where its money comes from and its
 * budget. The plugin reads it as the command's form and its refusal is shown as it words it.
 */
const emit = defineEmits<{
  created: [event: AdminEventRow]
  cancel: []
}>()

const {t} = useI18n()
const creating = useAction()
const form = reactive<ServerEventForm>({
  name: '',
  kind: 'tax_reduction',
  value: '',
  materials: '',
  levels: '',
  start: 'now',
  end: '1d',
  funding: 'MINTED',
  account: '',
  budget: '',
})
const kinds = computed(() => (['tax_reduction', 'reward_multiplier', 'fee_reduction'] as ServerEventKind[])
    .map(value => ({value, label: t(`admin.serverevents.kind.${value}`)})))
const fundings = computed(() => [
  {value: 'MINTED', label: t('admin.serverevents.form.minted')},
  {value: 'ACCOUNT', label: t('admin.serverevents.form.account')},
])
const kind = computed({
  get: () => form.kind as string,
  set: (value: string) => {
    form.kind = value as ServerEventKind
  },
})
const funding = computed({
  get: () => form.funding as string,
  set: (value: string) => {
    form.funding = value as ServerEventForm['funding']
  },
})

/** Sends the form; a created event is handed on. */
async function create(): Promise<void> {
  const created = await creating.run('admin.events.create', eventDraftOf(form))
  if (created) emit('created', created)
}
</script>

<template>
  <form class="flex flex-col gap-3 px-3 py-3 md:px-4" data-testid="admin-event-form" @submit.prevent="create">
    <div class="grid gap-3 md:grid-cols-2">
      <TextInput v-model="form.name" :label="t('admin.serverevents.form.name')" :maxlength="64"/>
      <SelectInput v-model="kind" :label="t('admin.serverevents.form.kind')" :options="kinds"/>
      <TextInput v-model="form.value" :label="t('admin.serverevents.form.value')" :hint="t('admin.serverevents.form.valueHint')" class="md:col-span-2"/>
      <TextInput v-model="form.materials" :label="t('admin.serverevents.form.materials')" :hint="t('admin.serverevents.form.materialsHint')"/>
      <TextInput v-if="form.kind !== 'fee_reduction'" v-model="form.levels" :label="t('admin.serverevents.form.levels')" :hint="t('admin.serverevents.form.levelsHint')"/>
      <TextInput v-model="form.start" :label="t('admin.serverevents.form.start')" :hint="t('admin.serverevents.form.startHint')"/>
      <TextInput v-model="form.end" :label="t('admin.serverevents.form.end')" :hint="t('admin.serverevents.form.endHint')"/>
      <template v-if="form.kind === 'reward_multiplier'">
        <SelectInput v-model="funding" :label="t('admin.serverevents.form.funding')" :options="fundings"/>
        <TextInput v-if="form.funding === 'ACCOUNT'" v-model="form.account" :label="t('admin.serverevents.form.account')" :hint="t('admin.serverevents.form.accountHint')"/>
        <TextInput v-model="form.budget" :label="t('admin.serverevents.form.budget')" :hint="t('admin.serverevents.form.budgetHint')"/>
      </template>
    </div>
    <ActionNotice :problem="creating.problem.value"/>
    <ButtonRow>
      <PrimaryButton type="submit" :disabled="creating.busy.value" data-testid="admin-event-create">{{ t('admin.serverevents.form.create') }}</PrimaryButton>
      <SecondaryButton @click="emit('cancel')">{{ t('common.back') }}</SecondaryButton>
    </ButtonRow>
  </form>
</template>
