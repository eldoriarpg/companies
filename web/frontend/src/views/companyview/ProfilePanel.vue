/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useNow} from '@vueuse/core'
import type {CompanyView, RankingPage} from '~/api/generated/protocol'
import PanelBox from '@/components/container/PanelBox.vue'
import FigureValue from '@/components/display/FigureValue.vue'
import ProgressBar from '@/components/display/ProgressBar.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {count, money, when} from '~/util/format'
import {openLabel} from '~/util/labels'

/**
 * The company's profile: tag and name, description, level with its progress, rank, the orders it works on, how it
 * takes new members, and the treasury where the player may see it.
 */
const props = defineProps<{
  company: CompanyView
  ranking?: RankingPage
}>()

const {t, te, locale} = useI18n()
const now = useNow({interval: 60_000})
const progress = computed(() => Math.round((props.company.level.progress ?? 0) * 100))
</script>

<template>
  <div class="flex flex-col gap-4">
    <PanelBox padded>
      <div class="flex items-center gap-2">
        <span v-if="company.tag" class="tabular bg-accent px-1.5 py-0.5 text-xs font-semibold text-accent-contrast">{{ company.tag }}</span>
        <SectionHeader>{{ company.name }}</SectionHeader>
      </div>
      <MutedText v-if="company.description" tag="p">{{ company.description }}</MutedText>
      <div class="grid grid-cols-3 gap-2">
        <FigureValue :label="t('company.level')">{{ company.level.level }}</FigureValue>
        <FigureValue :label="t('company.rank')">{{ ranking?.own ? `#${ranking.own.place}` : '-' }}</FigureValue>
        <FigureValue :label="t('company.orders')">{{ count(company.orders.length, locale) }}</FigureValue>
      </div>
      <ProgressBar :value="progress" tone="accent" :label="t('company.levelProgress', {level: company.level.name})"/>
      <MutedText tag="p">{{ t('company.levelNote', {level: company.level.name, progress}) }}</MutedText>
      <MutedText tag="p">{{ openLabel({t, te}, `company.joinMode.${company.joinMode}`, company.joinMode) }} · {{ t('company.founded', {time: when(company.founded, now, locale)}) }}</MutedText>
    </PanelBox>
    <PanelBox v-if="company.treasury" padded>
      <FigureValue :label="t('company.treasury')" size="large">
        <span data-testid="company-treasury">{{ money(company.treasury.balance, locale) }}</span>
      </FigureValue>
    </PanelBox>
  </div>
</template>
