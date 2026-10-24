/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useNow} from '@vueuse/core'
import type {OrderListing} from '~/api/gateway'
import FigureValue from '@/components/display/FigureValue.vue'
import ItemMark from '@/components/display/ItemMark.vue'
import TrendText from '@/components/display/TrendText.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {count, hoursLeft, money, percentChange, trendOf, when} from '~/util/format'
import {totalsOf, versusAverage} from '~/util/orders'

/**
 * The details of an order: what it pays in total and per item, how that compares with the market, and every line
 * with its item and amount. Who placed it is never shown. Accepting from the web comes with the next step; until then
 * the order is accepted in game.
 */
const props = defineProps<{
  order: OrderListing
  averages: ReadonlyMap<string, number>
  inSheet?: boolean
}>()

const {t, locale} = useI18n()
const now = useNow({interval: 60_000})
const totals = computed(() => totalsOf(props.order.lines))
const versus = computed(() => versusAverage(props.order.lines, props.averages))
</script>

<template>
  <aside :aria-label="t('orders.details')" :class="inSheet ? '' : 'border border-line bg-surface p-4'" class="flex min-w-0 flex-col gap-4">
    <div v-if="!inSheet" class="flex flex-col gap-1">
      <SectionHeader>{{ order.name }}</SectionHeader>
      <MutedText>{{ t('orders.anonymous') }}</MutedText>
    </div>
    <div class="grid grid-cols-3 gap-2">
      <FigureValue :label="t('orders.columnPayout')">{{ money(totals.payout, locale) }}</FigureValue>
      <FigureValue :label="t('orders.columnPerItem')">{{ money(totals.perItem, locale) }}</FigureValue>
      <FigureValue :label="t('orders.columnVersus')">
        <TrendText v-if="versus !== null" :trend="trendOf(versus)">{{ percentChange(versus, locale) }}</TrendText>
        <span v-else class="text-ink-muted">-</span>
      </FigureValue>
    </div>
    <ul class="m-0 list-none border border-line bg-ground px-3 py-0.5">
      <li v-for="line in order.lines" :key="line.item.material + line.item.name" class="flex items-center justify-between gap-3 border-b border-line-soft py-2.5 text-sm last:border-b-0">
        <span class="flex min-w-0 items-center gap-2">
          <ItemMark :item="line.item" size="small"/>
          <span class="truncate">{{ line.item.name }}</span>
        </span>
        <span class="tabular">{{ count(line.item.amount, locale) }}</span>
      </li>
    </ul>
    <MutedText tag="p">{{ t('orders.created', {time: when(order.created, now, locale)}) }}</MutedText>
    <MutedText v-if="order.expires" tag="p">{{ t('orders.expires', {hours: hoursLeft(order.expires, now)}) }}</MutedText>
    <MutedText v-else-if="order.deadline" tag="p">{{ t('orders.due', {hours: hoursLeft(order.deadline, now)}) }}</MutedText>
    <MutedText tag="p">{{ t('orders.acceptInGame') }}</MutedText>
  </aside>
</template>
