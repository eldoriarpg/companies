/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useNow} from '@vueuse/core'
import PanelBox from '@/components/container/PanelBox.vue'
import LineChart from '@/components/display/LineChart.vue'
import TrendText from '@/components/display/TrendText.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {money, moneyChange, signedMoney, trendOf, when} from '~/util/format'
import {openLabel} from '~/util/labels'

/**
 * The company's treasury as a trading app shows a balance: the figure large, its change since the first value known,
 * and the line it took, then its latest movements with the member who made them. Until the log or a change brings a
 * second value, the line is flat.
 */
defineProps<{
  company: string
}>()

const {t, te, locale} = useI18n()
const treasury = useTreasury()
const now = useNow({interval: 60_000})
onMounted(() => void treasury.loadLog())
</script>

<template>
  <PanelBox v-if="treasury.balance.value !== null" padded>
    <MutedText>{{ t('portfolio.treasuryOf', {company}) }}</MutedText>
    <div class="flex flex-wrap items-baseline gap-x-3 gap-y-1">
      <span class="tabular text-3xl font-semibold" data-testid="treasury-balance">{{ money(treasury.balance.value, locale) }}</span>
      <TrendText :trend="trendOf(treasury.change.value)" class="text-sm">{{ moneyChange(treasury.change.value, treasury.start.value, locale) }}</TrendText>
    </div>
    <LineChart
        :values="treasury.series.value"
        :trend="trendOf(treasury.change.value)"
        :width="600"
        :height="100"
        stretch
        :label="t('portfolio.treasuryChart')"
    />
    <ol v-if="treasury.recent.value.length > 0" class="m-0 list-none border-t border-line-soft p-0 pt-1" :aria-label="t('portfolio.movements')">
      <li v-for="entry in treasury.recent.value" :key="entry.id" class="flex items-baseline gap-2.5 py-1.5 text-xs">
        <span class="tabular w-12 shrink-0 text-ink-muted">{{ when(entry.time, now, locale) }}</span>
        <span class="min-w-0 grow truncate">
          <span v-if="entry.actor" class="font-semibold text-accent">{{ entry.actor.name }}</span>
          {{ openLabel({t, te}, `treasury.kind.${entry.kind}`, entry.kind) }}
        </span>
        <TrendText :trend="trendOf(entry.amount)" class="shrink-0">{{ signedMoney(entry.amount, locale) }}</TrendText>
      </li>
    </ol>
  </PanelBox>
</template>
