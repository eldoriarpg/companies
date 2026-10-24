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
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import NoticeBanner from '@/components/feedback/NoticeBanner.vue'
import DecimalInput from '@/components/input/DecimalInput.vue'
import {money} from '~/util/format'

/**
 * What the owner can do with an order nobody accepted yet: give it more time, raise what it pays, or cancel it. Raising
 * asks the plugin for the fee first and charges only after the owner confirmed the total; cancelling asks once more,
 * and says when the order belongs to a repeating order that stops with it. Every step is checked again by the plugin.
 * Each confirmation takes the focus, so a keyboard or screen reader lands on it. A cancelled order leaves the list
 * through its patch, so the confirmation of the cancel is told to the panel, which outlives the row.
 */
const props = defineProps<{
  order: OwnOrderView
}>()

const emit = defineEmits<{
  cancelled: [name: string]
}>()

/** What raising costs, as the plugin quoted it. */
interface RaiseQuote {
  fee: number
  total: number
}

const {t, locale} = useI18n()
const action = useAction()
const step = ref<'idle' | 'raise' | 'raise-confirm' | 'cancel-confirm'>('idle')
const increase = ref<number>()
const quote = ref<RaiseQuote | null>(null)
const repeating = ref(false)
const done = ref('')
const cancelled = ref(false)
const confirm = useTemplateRef<HTMLElement>('confirm')

/** Gives the order more time. */
async function extend(): Promise<void> {
  const result = await action.run('orders.own.extend', {order: props.order.id})
  if (result) finish(t('own.extended', {left: result.left}))
}

/** Asks the plugin what raising by the entered amount costs. */
async function quoteRaise(): Promise<void> {
  const by = increase.value
  if (by === undefined) return
  const result = await action.run('orders.own.raise', {order: props.order.id, increase: by, quote: true})
  if (!result) return
  quote.value = {fee: result.fee, total: result.total}
  await ask('raise-confirm')
}

/** Raises the order by the quoted amount. */
async function raise(): Promise<void> {
  const by = increase.value
  if (by === undefined) return
  const result = await action.run('orders.own.raise', {order: props.order.id, increase: by})
  if (result) finish(t('own.raised', {total: money(result.total, locale.value)}))
}

/** Asks whether the order repeats, then for the confirmation. */
async function askCancel(): Promise<void> {
  const result = await action.run('orders.own.cancel', {order: props.order.id, quote: true})
  if (!result) return
  repeating.value = result.repeating === true
  await ask('cancel-confirm')
}

/** Cancels the order. */
async function cancel(): Promise<void> {
  if (!await action.run('orders.own.cancel', {order: props.order.id})) return
  cancelled.value = true
  emit('cancelled', props.order.name)
}

/**
 * Shows a step and moves the focus into it.
 *
 * @param next the step
 */
async function ask(next: 'raise' | 'raise-confirm' | 'cancel-confirm'): Promise<void> {
  done.value = ''
  step.value = next
  await nextTick()
  confirm.value?.querySelector<HTMLElement>('input, button')?.focus()
}

/**
 * Ends the steps with a message.
 *
 * @param text what happened
 */
function finish(text: string): void {
  done.value = text
  step.value = 'idle'
  quote.value = null
  increase.value = undefined
}
</script>

<template>
  <div v-if="!cancelled" class="flex flex-col gap-2">
    <NoticeBanner v-if="done">{{ done }}</NoticeBanner>
    <ActionNotice :problem="action.problem.value"/>
    <ButtonRow v-if="step === 'idle'">
      <SecondaryButton :disabled="action.busy.value" @click="extend">{{ t('own.extend') }}</SecondaryButton>
      <SecondaryButton :disabled="action.busy.value" @click="ask('raise')">{{ t('own.raise') }}</SecondaryButton>
      <SecondaryButton :disabled="action.busy.value" @click="askCancel">{{ t('own.cancel') }}</SecondaryButton>
    </ButtonRow>
    <div v-else ref="confirm" class="flex flex-col gap-2">
      <form v-if="step === 'raise'" class="flex flex-wrap items-end gap-2" @submit.prevent="quoteRaise">
        <DecimalInput v-model="increase" :label="t('own.raiseBy')" :min="0"/>
        <PrimaryButton type="submit" :disabled="action.busy.value || increase === undefined">{{ t('own.quote') }}</PrimaryButton>
        <SecondaryButton @click="step = 'idle'">{{ t('common.back') }}</SecondaryButton>
      </form>
      <template v-else-if="step === 'raise-confirm' && quote">
        <span class="text-sm">{{ t('own.raiseQuote', {fee: money(quote.fee, locale), total: money(quote.total, locale)}) }}</span>
        <ButtonRow>
          <PrimaryButton :disabled="action.busy.value" @click="raise">{{ t('own.raiseConfirm', {total: money(quote.total, locale)}) }}</PrimaryButton>
          <SecondaryButton @click="step = 'idle'">{{ t('common.back') }}</SecondaryButton>
        </ButtonRow>
      </template>
      <template v-else-if="step === 'cancel-confirm'">
        <span class="text-sm">{{ repeating ? t('own.cancelRepeating') : t('own.cancelQuestion') }}</span>
        <ButtonRow>
          <PrimaryButton :disabled="action.busy.value" @click="cancel">{{ t('own.cancelConfirm') }}</PrimaryButton>
          <SecondaryButton @click="step = 'idle'">{{ t('common.back') }}</SecondaryButton>
        </ButtonRow>
      </template>
    </div>
  </div>
</template>
