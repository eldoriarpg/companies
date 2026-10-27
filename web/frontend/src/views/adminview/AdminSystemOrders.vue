/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminSystemDecision, AdminSystemLearned, AdminSystemOrder} from '~/api/gateway'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import RowButton from '@/components/button/RowButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import AdminSystemOrderRules from './AdminSystemOrderRules.vue'
import {dateTime} from '~/util/format'

/**
 * The orders the server places itself, as `/companyadmin systemorders` offers them: every system order open or in
 * work, why the chosen one was placed with the numbers its decision stood on, and what guessed orders learned per
 * material.
 */
const {t, te, locale} = useI18n()
const loading = useAction()
const opening = useAction()
const learning = useAction()
const orders = ref<AdminSystemOrder[] | null>(null)
const chosen = ref<AdminSystemDecision | null>(null)
const materials = ref<AdminSystemLearned[] | null>(null)

onMounted(() => {
  void load()
})

/** Loads the open system orders and the learning statistics. */
async function load(): Promise<void> {
  const [listed, learned] = await Promise.all([
    loading.run('admin.systemorders.list', {}),
    learning.run('admin.systemorders.stats', {}),
  ])
  if (listed) orders.value = listed.orders
  if (learned) materials.value = learned.materials
}

/**
 * Opens why an order was placed.
 *
 * @param order the order
 */
async function open(order: AdminSystemOrder): Promise<void> {
  const result = await opening.run('admin.systemorders.info', {order: order.order})
  if (result) chosen.value = result
}

/**
 * @param value a number
 * @param digits digits after the point
 * @returns the number in the viewer's language
 */
function number(value: number, digits = 2): string {
  return new Intl.NumberFormat(locale.value, {maximumFractionDigits: digits, minimumFractionDigits: 0}).format(value)
}

/**
 * The facts of a decision record the admin reads, nested learning flattened, each with a label when one is known.
 *
 * @param record the stored record
 * @returns label and value of each fact
 */
function facts(record: Record<string, unknown>): {key: string, label: string, value: string}[] {
  const entries: [string, unknown][] = []
  for (const [key, value] of Object.entries(record)) {
    if (value !== null && typeof value === 'object') {
      for (const [inner, innerValue] of Object.entries(value as Record<string, unknown>)) entries.push([`${key}.${inner}`, innerValue])
    } else {
      entries.push([key, value])
    }
  }
  return entries
    .filter(([, value]) => value !== null && value !== undefined)
    .map(([key, value]) => ({
      key,
      label: te(`admin.systemorders.fields.${key}`) ? t(`admin.systemorders.fields.${key}`) : key,
      value: typeof value === 'number' ? number(value, 4) : String(value),
    }))
}
</script>

<template>
  <div class="grid min-w-0 items-start gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)]">
    <PanelBox :aria-label="t('admin.systemorders.open')">
      <template #head>
        <SectionHeader>{{ t('admin.systemorders.open') }}</SectionHeader>
        <SecondaryButton :disabled="loading.busy.value" @click="load">{{ t('admin.systemorders.refresh') }}</SecondaryButton>
      </template>
      <ActionNotice :problem="loading.problem.value" class="mx-3 mt-3 md:mx-4"/>
      <template v-if="orders">
        <EmptyState v-if="orders.length === 0">{{ t('admin.systemorders.none') }}</EmptyState>
        <PlainList v-else data-testid="admin-system-orders">
          <li v-for="order in orders" :key="order.order">
            <RowButton :selected="chosen?.order === order.order" class="flex-col items-start text-sm" @click="open(order)">
              <span class="flex items-center gap-2">
                <span class="font-semibold">#{{ order.order }} {{ order.name }}</span>
                <InfoBadge>{{ t(`admin.systemorders.kind.${order.kind}`) }}</InfoBadge>
              </span>
              <span class="text-ink-muted">
                {{ t('admin.systemorders.row', {rule: order.rule, paid: number(order.paid), placed: dateTime(order.placed, locale), state: order.state ?? '-'}) }}
              </span>
            </RowButton>
          </li>
        </PlainList>
      </template>
    </PanelBox>
    <PanelBox :aria-label="t('admin.systemorders.why')">
      <template #head>
        <SectionHeader>{{ t('admin.systemorders.why') }}</SectionHeader>
      </template>
      <ActionNotice :problem="opening.problem.value" class="mx-3 mt-3 md:mx-4"/>
      <EmptyState v-if="!chosen">{{ t('admin.systemorders.choose') }}</EmptyState>
      <div v-else class="flex flex-col gap-2 px-3 py-3 text-sm md:px-4" data-testid="admin-system-decision">
        <p class="font-semibold">
          {{ t('admin.systemorders.decided', {order: chosen.order, rule: chosen.rule, placed: dateTime(chosen.placed, locale)}) }}
        </p>
        <p>{{ chosen.account ? t('admin.systemorders.account', {account: chosen.account, paid: number(chosen.paid)}) : t('admin.systemorders.minted', {paid: number(chosen.paid)}) }}</p>
        <p v-if="!chosen.record" class="text-ink-muted">{{ t('admin.systemorders.unreadable') }}</p>
        <dl v-else class="grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-1">
          <template v-for="fact in facts(chosen.record)" :key="fact.key">
            <dt class="text-ink-muted">{{ fact.label }}</dt>
            <dd class="tabular min-w-0 break-words">{{ fact.value }}</dd>
          </template>
        </dl>
      </div>
    </PanelBox>
    <PanelBox :aria-label="t('admin.systemorders.learned')" class="lg:col-span-2">
      <template #head>
        <SectionHeader>{{ t('admin.systemorders.learned') }}</SectionHeader>
      </template>
      <ActionNotice :problem="learning.problem.value" class="mx-3 mt-3 md:mx-4"/>
      <template v-if="materials">
        <EmptyState v-if="materials.length === 0">{{ t('admin.systemorders.nothingLearned') }}</EmptyState>
        <PlainList v-else data-testid="admin-system-learned">
          <li v-for="entry in materials" :key="entry.material" class="flex flex-wrap justify-between gap-2 border-t border-line-soft px-3 py-2.5 text-sm md:px-4">
            <span class="font-semibold">{{ entry.material }}</span>
            <span class="tabular text-ink-muted">
              {{ t('admin.systemorders.stats', {
                posted: entry.posted,
                taken: entry.taken,
                expired: entry.expired,
                minutes: entry.minutesToTake === null || entry.minutesToTake === undefined ? '-' : number(entry.minutesToTake, 0),
                factor: number(entry.factor),
                offset: number(entry.offset),
                rate: number(entry.takeRate * 100, 0),
              }) }}
            </span>
          </li>
        </PlainList>
      </template>
    </PanelBox>
    <div class="lg:col-span-2">
      <AdminSystemOrderRules/>
    </div>
  </div>
</template>
