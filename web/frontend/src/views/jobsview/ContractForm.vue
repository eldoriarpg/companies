/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {JobDetails} from '~/api/gateway'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import DecimalInput from '@/components/input/DecimalInput.vue'
import NumberInput from '@/components/input/NumberInput.vue'
import TextAreaInput from '@/components/input/TextAreaInput.vue'
import TextInput from '@/components/input/TextInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

/**
 * Posts a contract: work described in words, which a company takes like an order and the owner approves when it is
 * done. The price and the order fee are taken at once. Area jobs need a selection in the world and are posted in game.
 */
const emit = defineEmits<{
  posted: [details: JobDetails]
  close: []
}>()

const {t} = useI18n()
const action = useAction()
const title = ref('')
const description = ref('')
const price = ref<number | undefined>()
const hours = ref<number | undefined>(24)
const ready = computed(() => title.value.trim().length > 0 && (price.value ?? 0) > 0 && (hours.value ?? 0) >= 1)

async function post(): Promise<void> {
  if (!ready.value) return
  const result = await action.run('jobs.contract', {
    title: title.value.trim(),
    description: description.value,
    price: price.value ?? 0,
    hours: hours.value ?? 0,
  })
  if (result) emit('posted', result)
}
</script>

<template>
  <section class="flex flex-col gap-3 border border-line bg-surface p-4" data-testid="contract-form">
    <SectionHeader>{{ t('jobs.contract.title') }}</SectionHeader>
    <MutedText tag="p">{{ t('jobs.contract.intro') }}</MutedText>
    <TextInput v-model="title" :label="t('jobs.contract.name')" :maxlength="64"/>
    <TextAreaInput v-model="description" :label="t('jobs.contract.description')" :maxlength="512" :rows="4"/>
    <div class="grid grid-cols-2 gap-3">
      <DecimalInput v-model="price" :label="t('jobs.contract.price')" :min="0.01" data-testid="contract-price"/>
      <NumberInput v-model="hours" :label="t('jobs.contract.hours')" :min="1"/>
    </div>
    <MutedText tag="p">{{ t('jobs.contract.charged') }}</MutedText>
    <ActionNotice :problem="action.problem.value"/>
    <ButtonRow>
      <PrimaryButton :disabled="!ready || action.busy.value" data-testid="contract-post" @click="post">{{ t('jobs.contract.post') }}</PrimaryButton>
      <SecondaryButton @click="emit('close')">{{ t('jobs.contract.cancel') }}</SecondaryButton>
    </ButtonRow>
  </section>
</template>
