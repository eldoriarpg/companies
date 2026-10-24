/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {Feature} from '~/api/generated/protocol'
import Alert from '@/components/feedback/Alert.vue'
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
 * is open. After an order was placed on the dashboard the view opens with its name and says so once; the name is
 * taken out of the address, so a reload or the back gesture does not say it again.
 */
const {t} = useI18n()
const route = useRoute()
const {snapshot, company, activity, supports} = useDashboard()
const wakeLock = useWakeLock()
const router = useRouter()
const placed = ref(typeof route.query.placed === 'string' ? route.query.placed : '')

onMounted(() => {
  if (route.query.placed !== undefined) void router.replace({query: {...route.query, placed: undefined}})
})
</script>

<template>
  <ViewContent :title="t('nav.portfolio')">
    <div v-if="wakeLock.supported.value" class="flex justify-end">
      <ToggleSwitch v-model="wakeLock.wanted.value" icon="screen" :label="t('portfolio.keepScreenOn')"/>
    </div>
    <Alert v-if="placed" variant="success">{{ t('builder.placed', {name: placed}) }}</Alert>
    <Alert v-if="snapshot && !company" variant="notice">
      <span>{{ t(supports(Feature['company.create']) ? 'company.noCompanyFound' : 'portfolio.noCompany') }}</span>
      <span v-for="invite in snapshot.player.invites" :key="invite.company">{{ t('portfolio.invited', {company: invite.name}) }}</span>
    </Alert>
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
