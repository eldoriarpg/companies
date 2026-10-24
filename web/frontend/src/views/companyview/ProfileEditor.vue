/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {CompanyView} from '~/api/generated/protocol'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import TextInput from '@/components/input/TextInput.vue'

/**
 * Edits the company's description, tag and colour. Only the fields that changed are sent, one after the other; the
 * plugin checks each against the server's limits and stops nothing else when one is refused. An empty field removes
 * the value.
 */
const props = defineProps<{
  company: CompanyView
}>()

const emit = defineEmits<{
  saved: []
  close: []
}>()

const {t} = useI18n()
const action = useAction()
const description = ref(props.company.description ?? '')
const tag = ref(props.company.tag ?? '')
const color = ref(props.company.color ?? '')
const changed = computed(() => description.value !== (props.company.description ?? '')
    || tag.value !== (props.company.tag ?? '')
    || color.value !== (props.company.color ?? ''))

/** Saves the fields that changed, stopping at the first refusal. */
async function save(): Promise<void> {
  if (description.value !== (props.company.description ?? '')
      && !await action.run('company.profile.description', {description: description.value})) return
  if (tag.value !== (props.company.tag ?? '') && !await action.run('company.profile.tag', {tag: tag.value})) return
  if (color.value !== (props.company.color ?? '') && !await action.run('company.profile.color', {color: color.value})) return
  emit('saved')
}
</script>

<template>
  <form class="flex flex-col gap-3" @submit.prevent="save">
    <ActionNotice :problem="action.problem.value"/>
    <TextInput v-model="description" :label="t('company.description')"/>
    <div class="flex flex-wrap gap-3">
      <TextInput v-model="tag" :label="t('company.tag')" :hint="t('company.tagHint')"/>
      <TextInput v-model="color" :label="t('company.color')" :hint="t('company.colorHint')"/>
    </div>
    <ButtonRow>
      <PrimaryButton type="submit" :disabled="action.busy.value || !changed">{{ t('company.saveProfile') }}</PrimaryButton>
      <SecondaryButton @click="emit('close')">{{ t('common.back') }}</SecondaryButton>
    </ButtonRow>
  </form>
</template>
