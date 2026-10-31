/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {AreaSourceView, type AreaView, type HomeView} from '~/api/generated/protocol'
import {dateTime, money} from '~/util/format'

/**
 * The company's areas, plots and regions its members build in, with how it got each and until when a rent is paid,
 * and its home. Areas are linked, bought and rented in game with `/company area`, the home set with
 * `/company sethome`.
 */
defineProps<{
  areas: AreaView[]
  home?: HomeView
}>()

const {t, locale} = useI18n()

/**
 * The line under an area: how the company got it and, for a rent, until when it is paid and whether it renews.
 *
 * @param area the area
 * @return the text
 */
function detail(area: AreaView): string {
  switch (area.source) {
    case AreaSourceView.BOUGHT:
      return t('company.areas.bought', {price: money(area.price, locale.value)})
    case AreaSourceView.RENTED:
      return t(area.renews ? 'company.areas.rentRenews' : 'company.areas.rentEnds', {
        price: money(area.price, locale.value),
        until: area.rentUntil ? dateTime(area.rentUntil, locale.value) : '',
      })
    case AreaSourceView.LINKED:
      return t('company.areas.linked')
    default:
      return ''
  }
}
</script>

<template>
  <PanelBox :aria-label="t('company.areas.title')">
    <template #head>
      <SectionHeader :note="String(areas.length)">{{ t('company.areas.title') }}</SectionHeader>
    </template>
    <div class="border-b border-line-soft px-3 py-3 md:px-4" data-testid="home">
      <span class="text-sm font-semibold">{{ t('company.areas.home') }}</span>
      <MutedText v-if="home" tag="p">
        {{ t(home.server ? 'company.areas.homeOn' : 'company.areas.homeAt', {world: home.world, x: home.x, y: home.y, z: home.z, server: home.server ?? ''}) }}
      </MutedText>
      <MutedText v-else tag="p">{{ t('company.areas.noHome') }}</MutedText>
    </div>
    <EmptyState v-if="areas.length === 0">{{ t('company.areas.none') }}</EmptyState>
    <PlainList v-else data-testid="areas">
      <li v-for="area in areas" :key="area.id" class="flex flex-col gap-0.5 border-b border-line-soft px-3 py-2.5 last:border-b-0 md:px-4">
        <span class="flex justify-between gap-2">
          <span class="truncate text-sm font-semibold">#{{ area.id }} {{ area.ref }}</span>
          <span class="shrink-0 text-xs text-ink-muted">{{ area.world }}</span>
        </span>
        <MutedText tag="span">{{ detail(area) }}</MutedText>
      </li>
    </PlainList>
  </PanelBox>
</template>
