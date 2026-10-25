/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminTreasury} from '~/api/gateway'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import DecimalInput from '@/components/input/DecimalInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {dateTime, money} from '~/util/format'
import {openLabel} from '~/util/labels'

/**
 * A company's treasury for an admin: the balance, the share of payouts it keeps, the latest movements, and money given
 * to it or taken from it, never below zero. The balance follows each change.
 */
const treasury = defineModel<AdminTreasury>({required: true})

const {t, te, locale} = useI18n()
const action = useAction()
const amount = ref<number>()

/**
 * Gives money to the treasury or takes it.
 *
 * @param give whether money is given
 */
async function adjust(give: boolean): Promise<void> {
  if (amount.value === undefined) return
  const result = await action.run(give ? 'admin.companies.treasury.give' : 'admin.companies.treasury.take', {company: treasury.value.company, amount: amount.value})
  if (!result) return
  amount.value = undefined
  const refreshed = await action.run('admin.companies.treasury', {company: treasury.value.company})
  treasury.value = refreshed ?? {...treasury.value, balance: result.balance}
}
</script>

<template>
  <PanelBox padded :aria-label="t('admin.companies.treasury')" data-testid="admin-treasury">
    <SectionHeader :note="t('admin.companies.share', {share: treasury.share})">{{ t('admin.companies.treasury') }}</SectionHeader>
    <p class="tabular m-0 text-2xl font-bold">{{ money(treasury.balance, locale) }}</p>
    <ActionNotice :problem="action.problem.value"/>
    <form class="flex flex-wrap items-end gap-2" @submit.prevent="adjust(true)">
      <DecimalInput v-model="amount" :label="t('admin.companies.amount')" :min="0.01"/>
      <ButtonRow>
        <SecondaryButton type="submit" :disabled="amount === undefined || action.busy.value">{{ t('admin.companies.give') }}</SecondaryButton>
        <SecondaryButton :disabled="amount === undefined || action.busy.value" @click="adjust(false)">{{ t('admin.companies.take') }}</SecondaryButton>
      </ButtonRow>
    </form>
    <MutedText v-if="treasury.entries.length === 0" tag="p">{{ t('admin.companies.noEntries') }}</MutedText>
    <ul v-else class="m-0 flex list-none flex-col p-0 text-sm">
      <li v-for="entry in treasury.entries" :key="entry.id" class="flex justify-between gap-2 border-t border-line-soft py-1.5">
        <span class="min-w-0 truncate">
          <span v-if="entry.actor" class="font-semibold">{{ entry.actor.name }}</span>
          {{ openLabel({t, te}, `treasury.kind.${entry.kind}`, entry.kind) }}
        </span>
        <span class="tabular shrink-0">{{ money(entry.amount, locale) }} · {{ dateTime(entry.time, locale) }}</span>
      </li>
    </ul>
  </PanelBox>
</template>
