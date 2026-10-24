/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import NoticeBanner from '@/components/feedback/NoticeBanner.vue'
import ToggleSwitch from '@/components/input/ToggleSwitch.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import ActivityPanel from './portfolioview/ActivityPanel.vue'
import OwnOrdersPanel from './portfolioview/OwnOrdersPanel.vue'
import PositionsPanel from './portfolioview/PositionsPanel.vue'
import StatTiles from './portfolioview/StatTiles.vue'
import TreasuryPanel from './portfolioview/TreasuryPanel.vue'

/**
 * The home view, made for a phone lying next to the game: the treasury, the key figures, the orders the company works
 * on, the player's own orders and the company's live feed, all updating in place. The screen can be kept on while it
 * is open.
 */
const {t} = useI18n()
const {snapshot, company, activity} = useDashboard()
const wakeLock = useWakeLock()
</script>

<template>
  <ViewContent :title="t('nav.portfolio')">
    <div v-if="wakeLock.supported.value" class="flex justify-end">
      <ToggleSwitch v-model="wakeLock.wanted.value" icon="screen" :label="t('portfolio.keepScreenOn')"/>
    </div>
    <NoticeBanner v-if="snapshot && !company">
      <span>{{ t('portfolio.noCompany') }}</span>
      <span v-for="invite in snapshot.player.invites" :key="invite.company">{{ t('portfolio.invited', {company: invite.name}) }}</span>
    </NoticeBanner>
    <div v-if="snapshot" class="grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
      <TreasuryPanel v-if="company" :company="company.name"/>
      <StatTiles :snapshot="snapshot"/>
    </div>
    <div v-if="company" class="grid items-start gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
      <PositionsPanel :orders="company.orders"/>
      <ActivityPanel :entries="activity"/>
    </div>
    <OwnOrdersPanel v-if="snapshot" :orders="snapshot.orders"/>
  </ViewContent>
</template>
