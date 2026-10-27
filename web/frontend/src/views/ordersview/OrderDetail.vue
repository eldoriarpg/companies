/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import {useNow} from '@vueuse/core'
import type {OrderListing} from '~/api/gateway'
import {Feature} from '~/api/generated/protocol'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import FigureValue from '@/components/display/FigureValue.vue'
import ItemMark from '@/components/display/ItemMark.vue'
import TrendText from '@/components/display/TrendText.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {count, hoursLeft, money, percentChange, trendOf, when} from '~/util/format'
import {totalsOf, versusAverage} from '~/util/orders'
import {viewConditions} from '~/util/orderDraft'
import type {CriteriaView} from '~/api/generated/protocol'

/**
 * The details of an order: what it pays in total and per item, how that compares with the market, and every line
 * with its item and amount. Who placed it is never shown. Where the server offers accepting and the player's company
 * may take the order, it is accepted for the company from here; the plugin checks the permission and the limits again
 * and its refusal is shown as it words it.
 */
const props = defineProps<{
  order: OrderListing
  averages: ReadonlyMap<string, number>
  inSheet?: boolean
  canAccept?: boolean
}>()

const emit = defineEmits<{
  accepted: [order: number]
}>()

const {t, locale} = useI18n()

/**
 * The conditions of a criteria line, in the reader's language.
 *
 * @param criteria the line's criteria
 * @returns the conditions, empty without any
 */
function conditions(criteria: CriteriaView): string[] {
  return viewConditions(criteria, value => t('builder.atLeastEnchantments', {count: value}))
}
const now = useNow({interval: 60_000})
const totals = computed(() => totalsOf(props.order.lines))
const versus = computed(() => versusAverage(props.order.lines, props.averages))
const {supports} = useDashboard()
const action = useAction()
const accepted = ref(false)
const offered = computed(() => supports(Feature['orders.accept']))

watch(() => props.order.id, () => {
  accepted.value = false
  action.reset()
})

/** Accepts the order for the player's company. */
async function accept(): Promise<void> {
  if (await action.run('orders.accept', {order: props.order.id})) {
    accepted.value = true
    emit('accepted', props.order.id)
  }
}
</script>

<template>
  <aside :aria-label="t('orders.details')" :class="inSheet ? '' : 'border border-line bg-surface p-4'" class="flex min-w-0 flex-col gap-4">
    <div v-if="!inSheet" class="flex flex-col gap-1">
      <SectionHeader>{{ order.name }}</SectionHeader>
      <MutedText>{{ order.server && supports(Feature['orders.servermarks']) ? t('orders.byServer') : t('orders.anonymous') }}</MutedText>
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
          <span class="flex min-w-0 flex-col">
            <span class="truncate">{{ line.item.name }}</span>
            <MutedText v-if="line.criteria && conditions(line.criteria).length > 0">{{ conditions(line.criteria).join(', ') }}</MutedText>
          </span>
        </span>
        <span class="tabular">{{ count(line.item.amount, locale) }}</span>
      </li>
    </ul>
    <MutedText tag="p">{{ t('orders.created', {time: when(order.created, now, locale)}) }}</MutedText>
    <MutedText v-if="order.expires" tag="p">{{ t('orders.expires', {hours: hoursLeft(order.expires, now)}) }}</MutedText>
    <MutedText v-else-if="order.deadline" tag="p">{{ t('orders.due', {hours: hoursLeft(order.deadline, now)}) }}</MutedText>
    <Alert v-if="accepted" variant="success">{{ t('orders.accepted') }}</Alert>
    <ActionNotice v-else :problem="action.problem.value"/>
    <PrimaryButton v-if="offered && canAccept && !accepted" :disabled="action.busy.value" full-width @click="accept">{{ t('orders.accept') }}</PrimaryButton>
    <MutedText v-else-if="!offered" tag="p">{{ t('orders.acceptInGame') }}</MutedText>
  </aside>
</template>
