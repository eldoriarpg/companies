/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {RenamePayment, RenameResult} from '~/api/gateway'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import TextInput from '@/components/input/TextInput.vue'
import {money} from '~/util/format'

/**
 * Renames the company, for the owner. The plugin names the price first, and whether the treasury may pay it; the
 * rename happens only once the owner picked who pays. The choice takes the focus.
 */
const emit = defineEmits<{
  renamed: [name: string]
  close: []
}>()

const {t, locale} = useI18n()
const action = useAction()
const name = ref('')
const quote = ref<RenameResult | null>(null)
const choice = useTemplateRef<HTMLElement>('choice')

/** Asks what the new name costs. */
async function ask(): Promise<void> {
  const result = await action.run('company.profile.rename', {name: name.value.trim(), quote: true})
  if (!result) return
  quote.value = result
  await nextTick()
  choice.value?.querySelector<HTMLElement>('button')?.focus()
}

/**
 * Renames the company.
 *
 * @param payment who pays
 */
async function rename(payment: RenamePayment): Promise<void> {
  if (await action.run('company.profile.rename', {name: name.value.trim(), payment})) emit('renamed', name.value.trim())
}
</script>

<template>
  <div class="flex flex-col gap-3">
    <ActionNotice :problem="action.problem.value"/>
    <div v-if="quote" ref="choice" class="flex flex-col gap-2">
      <span class="text-sm">{{ t('company.renameQuote', {name: name.trim(), price: money(quote.price ?? 0, locale)}) }}</span>
      <ButtonRow>
        <PrimaryButton :disabled="action.busy.value" @click="rename('PLAYER')">{{ t('company.renamePayPlayer', {price: money(quote.price ?? 0, locale)}) }}</PrimaryButton>
        <PrimaryButton v-if="quote.treasury !== undefined" :disabled="action.busy.value" @click="rename('TREASURY')">{{ t('company.renamePayTreasury') }}</PrimaryButton>
        <SecondaryButton @click="quote = null">{{ t('common.back') }}</SecondaryButton>
      </ButtonRow>
    </div>
    <form v-else class="flex flex-wrap items-end gap-2" @submit.prevent="ask">
      <TextInput v-model="name" :label="t('company.newName')"/>
      <PrimaryButton type="submit" :disabled="action.busy.value || name.trim() === ''">{{ t('own.quote') }}</PrimaryButton>
      <SecondaryButton @click="emit('close')">{{ t('common.back') }}</SecondaryButton>
    </form>
  </div>
</template>
