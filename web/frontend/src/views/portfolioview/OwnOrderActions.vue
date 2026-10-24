/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {OwnOrderView} from '~/api/generated/protocol'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NoticeBanner from '@/components/feedback/NoticeBanner.vue'
import AmountInput from '@/components/input/AmountInput.vue'
import {money} from '~/util/format'

/**
 * What the owner can do with an order nobody accepted yet: give it more time, raise what it pays, or cancel it. Raising
 * asks the plugin for the fee first and charges only after the owner confirmed the total; cancelling asks once more,
 * and says when the order belongs to a repeating order that stops with it. Every step is checked again by the plugin.
 */
const props = defineProps<{
  order: OwnOrderView
}>()

/** What raising costs, as the plugin quoted it. */
interface RaiseQuote {
  fee: number
  total: number
}

const {t, locale} = useI18n()
const action = useAction()
const step = ref<'idle' | 'raise' | 'raise-confirm' | 'cancel-confirm' | 'done'>('idle')
const increase = ref('')
const quote = ref<RaiseQuote | null>(null)
const repeating = ref(false)
const done = ref('')

/** Gives the order more time. */
async function extend(): Promise<void> {
  const result = await action.run('orders.own.extend', {order: props.order.id})
  if (result) finish(t('own.extended', {left: result.left}))
}

/** Asks the plugin what raising by the entered amount costs. */
async function quoteRaise(): Promise<void> {
  const amount = Number(increase.value.replace(',', '.'))
  const result = await action.run('orders.own.raise', {order: props.order.id, increase: amount, quote: true})
  if (!result) return
  quote.value = {fee: result.fee, total: result.total}
  step.value = 'raise-confirm'
}

/** Raises the order by the quoted amount. */
async function raise(): Promise<void> {
  const amount = Number(increase.value.replace(',', '.'))
  const result = await action.run('orders.own.raise', {order: props.order.id, increase: amount})
  if (result) finish(t('own.raised', {total: money(result.total, locale.value)}))
}

/** Asks whether the order repeats, then for the confirmation. */
async function askCancel(): Promise<void> {
  const result = await action.run('orders.own.cancel', {order: props.order.id, quote: true})
  if (!result) return
  repeating.value = result.repeating === true
  step.value = 'cancel-confirm'
}

/** Cancels the order. */
async function cancel(): Promise<void> {
  if (await action.run('orders.own.cancel', {order: props.order.id})) finish(t('own.cancelled'))
}

/**
 * Ends the steps with a message.
 *
 * @param text what happened
 */
function finish(text: string): void {
  done.value = text
  step.value = 'done'
  quote.value = null
  increase.value = ''
}
</script>

<template>
  <div class="flex flex-col gap-2">
    <NoticeBanner v-if="step === 'done'">{{ done }}</NoticeBanner>
    <NoticeBanner v-if="action.problem.value !== null" tone="problem">{{ action.problem.value || t('common.actionFailed') }}</NoticeBanner>
    <ButtonRow v-if="step === 'idle' || step === 'done'">
      <SecondaryButton :disabled="action.busy.value" @click="extend">{{ t('own.extend') }}</SecondaryButton>
      <SecondaryButton :disabled="action.busy.value" @click="step = 'raise'">{{ t('own.raise') }}</SecondaryButton>
      <SecondaryButton :disabled="action.busy.value" @click="askCancel">{{ t('own.cancel') }}</SecondaryButton>
    </ButtonRow>
    <form v-else-if="step === 'raise'" class="flex flex-wrap items-end gap-2" @submit.prevent="quoteRaise">
      <AmountInput v-model="increase" :label="t('own.raiseBy')"/>
      <PrimaryButton type="submit" :disabled="action.busy.value || increase.trim() === ''">{{ t('own.quote') }}</PrimaryButton>
      <SecondaryButton @click="step = 'idle'">{{ t('common.back') }}</SecondaryButton>
    </form>
    <div v-else-if="step === 'raise-confirm' && quote" class="flex flex-col gap-2">
      <span class="text-sm">{{ t('own.raiseQuote', {fee: money(quote.fee, locale), total: money(quote.total, locale)}) }}</span>
      <ButtonRow>
        <PrimaryButton :disabled="action.busy.value" @click="raise">{{ t('own.raiseConfirm', {total: money(quote.total, locale)}) }}</PrimaryButton>
        <SecondaryButton @click="step = 'idle'">{{ t('common.back') }}</SecondaryButton>
      </ButtonRow>
    </div>
    <div v-else-if="step === 'cancel-confirm'" class="flex flex-col gap-2">
      <span class="text-sm">{{ repeating ? t('own.cancelRepeating') : t('own.cancelQuestion') }}</span>
      <ButtonRow>
        <PrimaryButton :disabled="action.busy.value" @click="cancel">{{ t('own.cancelConfirm') }}</PrimaryButton>
        <SecondaryButton @click="step = 'idle'">{{ t('common.back') }}</SecondaryButton>
      </ButtonRow>
    </div>
  </div>
</template>
