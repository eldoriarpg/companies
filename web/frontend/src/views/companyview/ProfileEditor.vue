/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {Feature, type CompanyView, type JoinMode} from '~/api/generated/protocol'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import SelectInput from '@/components/input/SelectInput.vue'
import TextInput from '@/components/input/TextInput.vue'

/**
 * Edits the company's description, tag, colour and how players join. The colour is one of the server's palette.
 * Only the fields the player changed since opening the editor are sent, one after the other, so a change another
 * member made meanwhile is kept; the plugin checks each against the server's limits, and the first refusal stops the
 * rest. An empty field removes the value.
 */
const props = defineProps<{
  company: CompanyView
}>()

const emit = defineEmits<{
  saved: []
  close: []
}>()

const MODES: readonly JoinMode[] = ['OPEN', 'APPLICATIONS', 'INVITE_ONLY']

const {t} = useI18n()
const {supports} = useDashboard()
const action = useAction()
const opened = {
  description: props.company.description ?? '',
  tag: props.company.tag ?? '',
  color: props.company.colorName ?? '',
  mode: props.company.joinMode as string,
}
const description = ref(opened.description)
const tag = ref(opened.tag)
const color = ref(opened.color)
const mode = ref(opened.mode)
const joining = computed(() => supports(Feature['company.applications']))
const colors = computed(() => [
  {value: '', label: t('company.noColor')},
  ...(props.company.palette ?? []).map(entry => ({value: entry.name, label: entry.name.replaceAll('_', ' ')})),
])
const modes = computed(() => MODES.map(value => ({value, label: t(`company.joinMode.${value}`)})))
const changed = computed(() => description.value !== opened.description
    || tag.value !== opened.tag
    || color.value !== opened.color
    || mode.value !== opened.mode)

/** Saves the fields that changed, stopping at the first refusal. */
async function save(): Promise<void> {
  if (description.value !== opened.description
      && !await action.run('company.profile.description', {description: description.value})) return
  if (tag.value !== opened.tag && !await action.run('company.profile.tag', {tag: tag.value})) return
  if (color.value !== opened.color && !await action.run('company.profile.color', {color: color.value})) return
  if (mode.value !== opened.mode && !await action.run('company.applications.join-mode', {mode: mode.value as JoinMode})) return
  emit('saved')
}
</script>

<template>
  <form class="flex flex-col gap-3" @submit.prevent="save">
    <ActionNotice :problem="action.problem.value"/>
    <TextInput v-model="description" :label="t('company.description')"/>
    <div class="flex flex-wrap items-end gap-3">
      <TextInput v-model="tag" :label="t('company.tag')" :hint="t('company.tagHint')"/>
      <SelectInput v-if="(company.palette ?? []).length > 0" v-model="color" :label="t('company.color')" :options="colors"/>
    </div>
    <SelectInput v-if="joining" v-model="mode" :label="t('company.joinModeLabel')" :options="modes"/>
    <ButtonRow>
      <PrimaryButton type="submit" :disabled="action.busy.value || !changed">{{ t('company.saveProfile') }}</PrimaryButton>
      <SecondaryButton @click="emit('close')">{{ t('common.back') }}</SecondaryButton>
    </ButtonRow>
  </form>
</template>
