/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {OrderListing} from '~/api/gateway'
import RowButton from '@/components/button/RowButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import ItemMark from '@/components/display/ItemMark.vue'
import TrendText from '@/components/display/TrendText.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import CapsLabel from '@/components/typography/CapsLabel.vue'
import {count, money, trendOf} from '~/util/format'
import {totalsOf, versusAverage} from '~/util/orders'

/**
 * The orders of the book's page as rows of a ticker: the item, the amount, the payout, the price per item and how it
 * compares with the market's average. On a phone the row keeps what matters; a row opens the order's details.
 */
const props = defineProps<{
  orders: readonly OrderListing[]
  loading: boolean
  averages: ReadonlyMap<string, number>
  selected: number | null
}>()

defineEmits<{
  open: [id: number]
}>()

const {t, locale} = useI18n()
const COLUMNS = 'md:grid md:grid-cols-[minmax(0,2fr)_minmax(0,1.5fr)_5rem_5rem_5rem_6rem] md:gap-3'

const rows = computed(() => props.orders.map(order => {
  const totals = totalsOf(order.lines)
  const versus = versusAverage(order.lines, props.averages)
  const first = order.lines[0]?.item
  return {
    order,
    totals,
    item: first,
    items: order.lines.length > 1 ? t('orders.lines', {count: order.lines.length}) : first?.name ?? '',
    versus: versus === null ? null : {trend: trendOf(versus), text: `${{up: '+', down: '−', flat: ''}[trendOf(versus)]}${Math.abs(versus).toFixed(1)}%`},
  }
}))
</script>

<template>
  <PanelBox tag="div">
    <div :class="COLUMNS" class="hidden border-b md:grid border-line-soft px-3 py-2" aria-hidden="true">
      <CapsLabel>{{ t('orders.columnOrder') }}</CapsLabel>
      <CapsLabel>{{ t('orders.columnItem') }}</CapsLabel>
      <CapsLabel class="text-right">{{ t('orders.columnQuantity') }}</CapsLabel>
      <CapsLabel class="text-right">{{ t('orders.columnPerItem') }}</CapsLabel>
      <CapsLabel class="text-right">{{ t('orders.columnVersus') }}</CapsLabel>
      <CapsLabel class="text-right">{{ t('orders.columnPayout') }}</CapsLabel>
    </div>
    <EmptyState v-if="rows.length === 0">{{ loading ? t('common.loading') : t('orders.none') }}</EmptyState>
    <ul v-else :aria-busy="loading" class="m-0 list-none p-0" data-testid="order-book">
      <li v-for="row in rows" :key="row.order.id">
        <RowButton :selected="selected === row.order.id" :class="COLUMNS" @click="$emit('open', row.order.id)">
          <span class="flex min-w-0 grow items-center gap-2.5">
            <ItemMark v-if="row.item" :item="row.item" class="md:hidden"/>
            <span class="flex min-w-0 flex-col gap-0.5">
              <span class="truncate text-sm font-semibold">{{ row.order.name }}</span>
              <span class="tabular truncate text-[0.6875rem] text-ink-muted md:hidden">{{ row.items }} ×{{ count(row.totals.amount, locale) }} · {{ money(row.totals.payout, locale) }}</span>
            </span>
          </span>
          <span class="hidden min-w-0 items-center gap-2 text-sm text-ink-soft md:flex">
            <ItemMark v-if="row.item" :item="row.item" size="small"/>
            <span class="truncate">{{ row.items }}</span>
          </span>
          <span class="tabular hidden text-right text-sm md:block">{{ count(row.totals.amount, locale) }}</span>
          <span class="tabular w-16 shrink-0 text-right text-sm md:w-auto">{{ money(row.totals.perItem, locale) }}</span>
          <TrendText v-if="row.versus" :trend="row.versus.trend" class="w-14 shrink-0 text-right text-xs md:w-auto md:text-sm">{{ row.versus.text }}</TrendText>
          <span v-else class="tabular w-14 shrink-0 text-right text-xs text-ink-muted md:w-auto">-</span>
          <span class="tabular hidden text-right text-sm font-semibold md:block">{{ money(row.totals.payout, locale) }}</span>
        </RowButton>
      </li>
    </ul>
  </PanelBox>
</template>
