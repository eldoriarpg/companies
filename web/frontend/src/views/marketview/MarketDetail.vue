/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {MarketItemView} from '~/api/gateway'
import FigureValue from '@/components/display/FigureValue.vue'
import TrendText from '@/components/display/TrendText.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {compact, money, percentChange, trendOf} from '~/util/format'

/**
 * The figures of one material: the average price per item large with its change, the lowest and highest price and the
 * volume. The chart of its price over time comes once the plugin keeps price history.
 */
defineProps<{
  entry: MarketItemView
  inSheet?: boolean
}>()

const {t, locale} = useI18n()
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
    <MutedText tag="p">{{ t('market.historyLater') }}</MutedText>
  </aside>
</template>
