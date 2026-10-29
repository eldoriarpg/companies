/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminOffer} from '~/api/gateway'
import {dateTime, money} from '~/util/format'

/** The facts of an event offer's record: who created it, what it orders, its progress, money and times. */
defineProps<{
  offer: AdminOffer
}>()

const {t, locale} = useI18n()

/**
 * @param time a moment, or none
 * @returns the moment in full, or a dash
 */
function at(time: string | null | undefined): string {
  return time ? dateTime(time, locale.value) : '-'
}
</script>

<template>
  <dl class="m-0 grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-1">
    <dt class="text-ink-muted">{{ t('admin.offers.fields.created') }}</dt>
    <dd class="m-0">{{ t('admin.offers.createdBy', {by: offer.createdBy, at: at(offer.created)}) }}</dd>
    <dt class="text-ink-muted">{{ t('admin.offers.fields.item') }}</dt>
    <dd class="m-0 break-words">{{ offer.item.name }} ({{ t(`admin.offers.line.${offer.line}`) }})</dd>
    <dt class="text-ink-muted">{{ t('admin.offers.fields.progress') }}</dt>
    <dd class="tabular m-0">{{ offer.delivered }} / {{ offer.amount }} ({{ offer.percent }} %) · {{ money(offer.unitPrice, locale) }}</dd>
    <template v-if="offer.kind === 'goal'">
      <dt class="text-ink-muted">{{ t('admin.offers.fields.chunks') }}</dt>
      <dd class="tabular m-0">{{ t('admin.offers.chunks', {size: offer.chunkSize ?? 0, open: offer.openChunks ?? 0, waiting: offer.waiting, taken: offer.taken}) }}</dd>
    </template>
    <dt class="text-ink-muted">{{ t('admin.offers.fields.money') }}</dt>
    <dd class="tabular m-0">{{ offer.account ? t('admin.offers.account', {account: offer.account}) : t('admin.offers.minted') }}</dd>
    <dt class="text-ink-muted">{{ t('admin.offers.fields.budget') }}</dt>
    <dd class="tabular m-0">{{ t('admin.offers.budget', {budget: money(offer.budget, locale), spent: money(offer.spent, locale), left: money(offer.left, locale)}) }}</dd>
    <template v-if="offer.held !== null && offer.held !== undefined">
      <dt class="text-ink-muted">{{ t('admin.offers.fields.held') }}</dt>
      <dd class="tabular m-0">{{ money(offer.held, locale) }}</dd>
    </template>
    <dt class="text-ink-muted">{{ t('admin.offers.fields.time') }}</dt>
    <dd class="m-0">{{ t('admin.offers.time', {starts: at(offer.starts), ends: at(offer.ends)}) }}</dd>
    <template v-if="offer.closed">
      <dt class="text-ink-muted">{{ t('admin.offers.fields.closed') }}</dt>
      <dd class="m-0">{{ at(offer.closed) }}</dd>
    </template>
    <template v-if="offer.refund !== null && offer.refund !== undefined">
      <dt class="text-ink-muted">{{ t('admin.offers.fields.refund') }}</dt>
      <dd class="tabular m-0">{{ t('admin.offers.refund', {amount: money(offer.refund, locale), at: at(offer.refunded)}) }}</dd>
    </template>
    <template v-if="offer.kind === 'contest'">
      <dt class="text-ink-muted">{{ t('admin.offers.fields.prizes') }}</dt>
      <dd class="tabular m-0">{{ offer.prizeMoney.length === 0 ? t('admin.offers.noPrizes') : offer.prizeMoney.map(value => money(value, locale)).join(', ') }}</dd>
    </template>
  </dl>
</template>
