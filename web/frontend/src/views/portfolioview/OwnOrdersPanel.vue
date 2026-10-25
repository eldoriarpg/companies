/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import PlainList from '@/components/container/PlainList.vue'
import {useNow} from '@vueuse/core'
import {Feature, OrderState, type OwnOrderView} from '~/api/generated/protocol'
import ButtonLink from '@/components/button/ButtonLink.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import ProgressBar from '@/components/display/ProgressBar.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Alert from '@/components/feedback/Alert.vue'
import SearchInput from '@/components/input/SearchInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import OwnOrderActions from './OwnOrderActions.vue'
import OwnOrderReceive from './OwnOrderReceive.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import {count, money, when} from '~/util/format'
import {openLabel} from '~/util/labels'
import {itemsText, totalsOf} from '~/util/orders'

/**
 * The orders the player placed: their state, how far they are, what they pay and what waits to be collected in game.
 * Who works on them stays unknown, as in game. An order nobody accepted yet can be extended, raised or cancelled
 * here where the server offers it; a delivered order is received into the inventory, and items waiting for the
 * player are collected, while the player is online on the server.
 */
const props = defineProps<{
  orders: readonly OwnOrderView[]
}>()

const {t, te, locale} = useI18n()
const {supports, playerOnline} = useDashboard()
const manageable = computed(() => supports(Feature['orders.own']))
const creatable = computed(() => supports(Feature['orders.create']))
const delivers = computed(() => supports(Feature['orders.deliver']))
const collecting = useAction()

/** Collects the items waiting for the player into the inventory. */
async function collect(): Promise<void> {
  done.value = ''
  const result = await collecting.run('orders.deliver.collect', {})
  if (!result) return
  if (result.received === 0 && result.remaining === 0) done.value = t('collect.none')
  else if (result.remaining > 0) done.value = t('collect.partly', {received: count(result.received, locale.value), remaining: count(result.remaining, locale.value)})
  else done.value = t('collect.done', {received: count(result.received, locale.value)})
}
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
      <div class="flex w-full flex-wrap gap-2 md:w-auto">
        <ButtonLink v-if="creatable" :to="{name: 'order-new'}" icon="plus">{{ t('builder.newOrder') }}</ButtonLink>
        <SecondaryButton v-if="delivers && playerOnline" :disabled="collecting.busy.value" @click="collect">{{ t('collect.button') }}</SecondaryButton>
        <div class="min-w-0 grow md:w-56">
          <SearchInput v-model="query" :label="t('portfolio.searchOwn')"/>
        </div>
      </div>
    </template>
    <Alert v-if="done" variant="success" class="m-3 md:mx-4">{{ done }}</Alert>
    <ActionNotice :problem="collecting.problem.value" class="m-3 md:mx-4"/>
    <EmptyState v-if="rows.length === 0">{{ orders.length === 0 ? t('portfolio.noOwn') : t('common.noMatch') }}</EmptyState>
    <PlainList v-else>
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
        <OwnOrderReceive v-if="delivers && row.order.state === OrderState.DELIVERED" :order="row.order.id" :name="row.order.name" @received="text => done = text"/>
        <OwnOrderActions v-if="manageable && row.order.state === OrderState.UNCLAIMED" :order="row.order" @cancelled="name => done = t('own.cancelled', {name})"/>
      </li>
    </PlainList>
  </PanelBox>
</template>
