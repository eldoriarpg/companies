/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useNow} from '@vueuse/core'
import {Feature, OrderState, type OwnOrderView} from '~/api/generated/protocol'
import PanelBox from '@/components/container/PanelBox.vue'
import ProgressBar from '@/components/display/ProgressBar.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NoticeBanner from '@/components/feedback/NoticeBanner.vue'
import SearchInput from '@/components/input/SearchInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import OwnOrderActions from './OwnOrderActions.vue'
import {count, money, when} from '~/util/format'
import {openLabel} from '~/util/labels'
import {itemsText, totalsOf} from '~/util/orders'

/**
 * The orders the player placed: their state, how far they are, what they pay and what waits to be collected in game.
 * Who works on them stays unknown, as in game. An order nobody accepted yet can be extended, raised or cancelled
 * here where the server offers it.
 */
const props = defineProps<{
  orders: readonly OwnOrderView[]
}>()

const {t, te, locale} = useI18n()
const {supports} = useDashboard()
const manageable = computed(() => supports(Feature['orders.own']))
const done = ref('')
const now = useNow({interval: 60_000})
const {query, found} = useListSearch(toRef(props, 'orders'), order => [order.name, ...order.lines.map(line => line.item.name)])

const rows = computed(() => found.value.map(order => ({
  order,
  totals: totalsOf(order.lines),
  items: itemsText(order.lines, value => count(value, locale.value)),
  state: openLabel({t, te}, `orderState.${order.state}`, order.state),
  waiting: order.waiting.reduce((sum, item) => sum + item.amount, 0),
})))
</script>

<template>
  <PanelBox :aria-label="t('portfolio.own')">
    <template #head>
      <SectionHeader :note="t('portfolio.ownNote')">{{ t('portfolio.own') }}</SectionHeader>
      <div class="w-full md:w-56">
        <SearchInput v-model="query" :label="t('portfolio.searchOwn')"/>
      </div>
    </template>
    <NoticeBanner v-if="done" class="m-3 md:mx-4">{{ done }}</NoticeBanner>
    <EmptyState v-if="rows.length === 0">{{ orders.length === 0 ? t('portfolio.noOwn') : t('common.noMatch') }}</EmptyState>
    <ul v-else class="m-0 list-none p-0">
      <li v-for="row in rows" :key="row.order.id" class="flex flex-col gap-2 border-b border-line-soft px-3 py-3 last:border-b-0 md:px-4">
        <div class="flex justify-between gap-2">
          <span class="truncate text-sm font-semibold">{{ row.order.name }}</span>
          <span class="tabular text-sm">{{ money(row.totals.payout, locale) }}</span>
        </div>
        <div class="flex flex-wrap justify-between gap-x-3 gap-y-1 text-xs text-ink-muted">
          <span class="truncate">{{ row.items }}</span>
          <span class="tabular">{{ row.state }} · {{ when(row.order.created, now, locale) }}</span>
        </div>
        <ProgressBar :value="row.totals.progress" :label="t('portfolio.filled', {name: row.order.name})"/>
        <span v-if="row.waiting > 0" class="text-xs font-semibold text-accent">{{ t('portfolio.toCollect', {count: count(row.waiting, locale)}) }}</span>
        <OwnOrderActions v-if="manageable && row.order.state === OrderState.UNCLAIMED" :order="row.order" @cancelled="name => done = t('own.cancelled', {name})"/>
      </li>
    </ul>
  </PanelBox>
</template>
