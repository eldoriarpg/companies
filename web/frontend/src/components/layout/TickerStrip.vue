/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {MarketItemView} from '~/api/gateway'
import TrendText from '@/components/display/TrendText.vue'
import {money, percentChange, trendOf} from '~/util/format'

/**
 * The price ticker across the top: the market's most ordered items with their average price and, once the plugin
 * keeps price history, their change. It scrolls sideways by touch; nothing moves on its own.
 */
defineProps<{
  items: readonly MarketItemView[]
}>()

const {t, locale} = useI18n()
</script>

<template>
  <section
      :aria-label="t('ticker.label')"
      class="tabular flex gap-6 overflow-x-auto whitespace-nowrap border-b border-line-soft bg-deep px-4 py-2 text-xs md:px-6"
  >
    <span v-for="entry in items" :key="entry.item.material" class="flex gap-1.5">
      <span class="text-ink-muted">{{ entry.item.name }}</span>
      <span>{{ entry.avg === undefined ? t('market.noPrice') : money(entry.avg, locale) }}</span>
      <TrendText v-if="entry.change !== undefined" :trend="trendOf(entry.change)">{{ percentChange(entry.change, locale) }}</TrendText>
    </span>
  </section>
</template>
