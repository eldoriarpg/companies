/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useNow} from '@vueuse/core'
import {Feature, MemberPermission, type ClaimedOrderView} from '~/api/generated/protocol'
import PanelBox from '@/components/container/PanelBox.vue'
import ProgressBar from '@/components/display/ProgressBar.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Alert from '@/components/feedback/Alert.vue'
import SearchInput from '@/components/input/SearchInput.vue'
import CapsLabel from '@/components/typography/CapsLabel.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PositionAbort from './PositionAbort.vue'
import PositionDeliver from './PositionDeliver.vue'
import {count, hoursLeft, money} from '~/util/format'
import {holds} from '~/util/permissions'
import {itemsText, totalsOf} from '~/util/orders'

/**
 * The positions: the orders the company is processing, with their progress, payout and the time left. They update in
 * place as members deliver. A member online on the server delivers from their inventory here, where the server
 * offers it, and members who may manage orders can give one back to the order book.
 */
const props = defineProps<{
  orders: readonly ClaimedOrderView[]
}>()

const {t, locale} = useI18n()
const {supports, snapshot} = useDashboard()
const manages = computed(() => {
  const permissions = snapshot.value?.player.permissions ?? []
  return supports(Feature['orders.accept'])
    && holds(permissions, MemberPermission.MANAGE_ORDERS)
})
const delivers = computed(() => supports(Feature['orders.deliver']))
const done = ref('')
const now = useNow({interval: 60_000})
const {query, found} = useListSearch(toRef(props, 'orders'), order => [order.name, ...order.lines.map(line => line.item.name)])

const rows = computed(() => found.value.map(order => ({
  order,
  totals: totalsOf(order.lines),
  items: itemsText(order.lines, value => count(value, locale.value)),
  left: order.deadline ? t('portfolio.hoursLeft', {hours: hoursLeft(order.deadline, now.value)}) : '',
})))
</script>

<template>
  <PanelBox :aria-label="t('portfolio.positions')">
    <template #head>
      <SectionHeader :note="t('portfolio.positionsNote')">{{ t('portfolio.positions') }}</SectionHeader>
      <div class="w-full md:w-56">
        <SearchInput v-model="query" :label="t('portfolio.searchPositions')"/>
      </div>
    </template>
    <Alert v-if="done" variant="success" class="m-3 md:mx-4">{{ done }}</Alert>
    <div v-if="rows.length > 0" class="hidden gap-4 border-b border-line-soft px-4 py-2 md:grid md:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_7rem_5rem]" aria-hidden="true">
      <CapsLabel>{{ t('portfolio.columnOrder') }}</CapsLabel>
      <CapsLabel>{{ t('portfolio.columnFilled') }}</CapsLabel>
      <CapsLabel class="text-right">{{ t('portfolio.columnPayout') }}</CapsLabel>
      <CapsLabel class="text-right">{{ t('portfolio.columnDue') }}</CapsLabel>
    </div>
    <EmptyState v-if="rows.length === 0">{{ orders.length === 0 ? t('portfolio.noPositions') : t('common.noMatch') }}</EmptyState>
    <ul v-else class="m-0 list-none p-0" data-testid="positions">
      <li v-for="row in rows" :key="row.order.id" class="flex flex-col gap-2 border-b border-line-soft px-3 py-3 last:border-b-0 md:grid md:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_7rem_5rem] md:items-center md:gap-4 md:px-4">
        <div class="flex min-w-0 justify-between gap-2 md:flex-col md:justify-start md:gap-0.5">
          <span class="truncate text-sm font-semibold">{{ row.order.name }}</span>
          <span class="tabular text-sm md:hidden">{{ money(row.totals.payout, locale) }}</span>
          <span class="hidden truncate text-xs text-ink-muted md:block">{{ row.items }}</span>
        </div>
        <div class="flex justify-between gap-2 text-xs text-ink-muted md:hidden">
          <span class="truncate">{{ row.items }}</span>
          <span class="tabular shrink-0">{{ row.left }}</span>
        </div>
        <div class="flex items-center gap-2">
          <ProgressBar :value="row.totals.progress" :label="t('portfolio.filled', {name: row.order.name})"/>
          <span class="tabular w-10 shrink-0 text-right text-xs">{{ row.totals.progress }}%</span>
        </div>
        <span class="tabular hidden text-right text-sm md:block">{{ money(row.totals.payout, locale) }}</span>
        <span class="tabular hidden text-right text-xs text-ink-muted md:block">{{ row.left }}</span>
        <PositionDeliver v-if="delivers" :order="row.order"/>
        <PositionAbort v-if="manages" :order="row.order.id" :name="row.order.name" @aborted="name => done = t('positions.aborted', {name})"/>
      </li>
    </ul>
  </PanelBox>
</template>
