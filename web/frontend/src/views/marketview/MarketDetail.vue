/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {MarketItemView} from '~/api/gateway'
import ChipButton from '@/components/button/ChipButton.vue'
import FigureValue from '@/components/display/FigureValue.vue'
import LineChart from '@/components/display/LineChart.vue'
import TrendText from '@/components/display/TrendText.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {HISTORY_RANGES} from '~/composables/useMarketHistory'
import {compact, money, percentChange, trendOf} from '~/util/format'

/**
 * The figures of one material: the average price per item large with its change against the week before, the lowest
 * and highest price and the volume, then the chart of its average price per day over a week, a month or a quarter.
 * The days sit on a time axis; days without orders or with too few for a price leave a gap in the line.
 */
const props = defineProps<{
  entry: MarketItemView
  inSheet?: boolean
}>()

const {t, locale} = useI18n()
const history = useMarketHistory(computed(() => props.entry.item.material))
const trend = computed(() => {
  const prices = history.series.value.filter(value => value !== null)
  return prices.length < 2 ? 'flat' : trendOf((prices.at(-1) ?? 0) - (prices[0] ?? 0))
})
</script>

<template>
  <aside :aria-label="t('market.details')" :class="inSheet ? '' : 'border border-line bg-surface p-4'" class="flex min-w-0 flex-col gap-3">
    <SectionHeader v-if="!inSheet">{{ entry.item.name }}</SectionHeader>
    <div class="flex flex-wrap items-baseline gap-3">
      <span class="tabular text-3xl font-semibold">{{ entry.avg === undefined ? t('market.noPrice') : money(entry.avg, locale) }}</span>
      <TrendText v-if="entry.change !== undefined" :trend="trendOf(entry.change)" class="text-sm">{{ percentChange(entry.change, locale) }}</TrendText>
    </div>
    <MutedText>{{ t('market.averageNote') }}</MutedText>
    <div class="grid grid-cols-3 gap-2">
      <FigureValue :label="t('market.columnLow')">{{ entry.min === undefined ? '-' : money(entry.min, locale) }}</FigureValue>
      <FigureValue :label="t('market.columnHigh')">{{ entry.max === undefined ? '-' : money(entry.max, locale) }}</FigureValue>
      <FigureValue :label="t('market.columnVolume')">{{ entry.volume === undefined ? '-' : compact(entry.volume, locale) }}</FigureValue>
    </div>
    <div class="flex flex-col gap-2 border-t border-line-soft pt-3">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <span class="text-xs font-semibold">{{ t('market.history') }}</span>
        <div class="flex gap-1" role="group" :aria-label="t('market.historyRange')">
          <ChipButton
              v-for="days in HISTORY_RANGES"
              :key="days"
              :pressed="history.range.value === days"
              :aria-label="t('market.daysLong', {days})"
              @click="history.range.value = days"
          >{{ t('market.days', {days}) }}</ChipButton>
        </div>
      </div>
      <LineChart
          v-if="history.priced.value > 0"
          :values="history.series.value"
          :trend="trend"
          :width="300"
          :height="80"
          stretch
          :label="t('market.historyChart', {item: entry.item.name, days: history.detail.value?.days ?? history.range.value})"
      />
      <MutedText v-else-if="history.status.value === 'failed'" tag="p">{{ t('market.historyFailed') }}</MutedText>
      <MutedText v-else-if="history.status.value === 'ready'" tag="p">{{ t('market.historyEmpty') }}</MutedText>
    </div>
  </aside>
</template>
