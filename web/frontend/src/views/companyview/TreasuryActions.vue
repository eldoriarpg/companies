/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {TreasuryView} from '~/api/generated/protocol'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import NoticeBanner from '@/components/feedback/NoticeBanner.vue'
import AmountInput from '@/components/input/AmountInput.vue'
import TextInput from '@/components/input/TextInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {money, parseAmount} from '~/util/format'

/**
 * Pays money into the treasury or takes it out, and sets the share of every payout the company keeps, a whole percent
 * up to the server's cap. The plugin checks the smallest amount, the balances and the cap again; the balance follows
 * through its patch, and the share field follows it too until the player types into it.
 */
const props = defineProps<{
  treasury: TreasuryView
}>()

const {t, locale} = useI18n()
const action = useAction()
const amount = ref('')
const share = ref(shown(props.treasury.share))
const done = ref('')
const wholePercent = computed(() => /^\d{1,3}$/.test(share.value.trim()))

watch(() => props.treasury.share, (now, before) => {
  if (share.value === shown(before)) share.value = shown(now)
})

/**
 * A share as the field shows it.
 *
 * @param value the share
 * @returns the text, empty without a share
 */
function shown(value: number | undefined): string {
  return value === undefined ? '' : String(value)
}

/**
 * Moves money into or out of the treasury.
 *
 * @param direction in or out
 */
async function move(direction: 'deposit' | 'withdraw'): Promise<void> {
  done.value = ''
  const value = parseAmount(amount.value)
  const result = await action.run(`company.treasury.${direction}`, {amount: value})
  if (!result) return
  const key = direction === 'deposit' ? 'company.deposited' : 'company.withdrawn'
  done.value = t(key, {amount: money(value, locale.value), balance: money(result.balance, locale.value)})
  amount.value = ''
}

/** Sets the share. */
async function saveShare(): Promise<void> {
  done.value = ''
  const result = await action.run('company.treasury.share', {percent: Number(share.value.trim())})
  if (result) done.value = t('company.shareSaved', {share: result.share})
}
</script>

<template>
  <div class="flex flex-col gap-3 border-t border-line-soft pt-3">
    <NoticeBanner v-if="done">{{ done }}</NoticeBanner>
    <ActionNotice :problem="action.problem.value"/>
    <div class="flex flex-wrap items-end gap-2">
      <AmountInput v-model="amount" :label="t('company.amount')"/>
      <ButtonRow>
        <SecondaryButton :disabled="action.busy.value || amount.trim() === ''" @click="move('deposit')">{{ t('company.deposit') }}</SecondaryButton>
        <SecondaryButton :disabled="action.busy.value || amount.trim() === ''" @click="move('withdraw')">{{ t('company.withdraw') }}</SecondaryButton>
      </ButtonRow>
    </div>
    <MutedText v-if="treasury.minTransaction !== undefined" tag="p">{{ t('company.minTransaction', {amount: money(treasury.minTransaction, locale)}) }}</MutedText>
    <form v-if="treasury.maxShare !== undefined" class="flex flex-wrap items-end gap-2" @submit.prevent="saveShare">
      <div class="w-40">
        <TextInput v-model="share" :label="t('company.share', {max: treasury.maxShare})" :maxlength="3" inputmode="numeric"/>
      </div>
      <SecondaryButton type="submit" :disabled="action.busy.value || !wholePercent">{{ t('company.saveShare') }}</SecondaryButton>
    </form>
  </div>
</template>
