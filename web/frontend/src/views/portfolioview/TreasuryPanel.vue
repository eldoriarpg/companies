/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import PanelBox from '@/components/container/PanelBox.vue'
import LineChart from '@/components/display/LineChart.vue'
import TrendText from '@/components/display/TrendText.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {money, moneyChange, trendOf} from '~/util/format'

/**
 * The company's treasury as a trading app shows a balance: the figure large, its change since the first value known,
 * and the line it took. Until the log or a change brings a second value, the line is flat.
 */
defineProps<{
  company: string
}>()

const {t, locale} = useI18n()
const treasury = useTreasury()
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
  </PanelBox>
</template>
