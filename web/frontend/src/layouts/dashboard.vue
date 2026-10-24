/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useEventListener} from '@vueuse/core'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DiscontinuedNotice from '@/components/feedback/DiscontinuedNotice.vue'
import NoticeBanner from '@/components/feedback/NoticeBanner.vue'
import AppFooter from '@/components/layout/AppFooter.vue'
import AppSidebar from '@/components/layout/AppSidebar.vue'
import BottomTabBar from '@/components/layout/BottomTabBar.vue'
import MenuSheet from '@/components/layout/MenuSheet.vue'
import PlayerCard from '@/components/layout/PlayerCard.vue'
import TickerStrip from '@/components/layout/TickerStrip.vue'
import TopBar from '@/components/layout/TopBar.vue'
import VersionBanner from '@/components/layout/VersionBanner.vue'

/**
 * The frame of the dashboard. On a phone: the bar with the server and the live state on top, the ticker below it, the
 * tab bar at the bottom and a menu sheet for the rest. From tablet width: the sidebar on the left and the ticker above
 * the page. It opens the session, sends a reader without one to the login, and shows only the explanation for a
 * discontinued server.
 */
const {t} = useI18n()
const dashboard = useDashboard()
const {status, server, snapshot, company, live, problem} = dashboard
const shell = useShell()
const market = useMarket()
const menuOpen = ref(false)

onMounted(() => dashboard.start())
useEventListener(document, 'visibilitychange', () => {
  if (document.visibilityState === 'visible') dashboard.wake()
})
useEventListener(window, 'online', () => dashboard.wake())
const {show} = useBranding()
watch(server, value => show(value?.server ?? null), {immediate: true})
watch(status, value => {
  if (value === 'gone') void navigateTo('/', {replace: true})
  if (value === 'ready') void market.loadTop()
}, {immediate: true})

/** Logs out and goes to the login. */
async function logout(): Promise<void> {
  menuOpen.value = false
  await dashboard.logout()
}
</script>

<template>
  <div class="flex min-h-dvh">
    <AppSidebar v-if="server" :server="server.server" :sections="shell.sections.value" :current="shell.current.value?.id" :badges="shell.badges.value">
      <PlayerCard v-if="snapshot" :name="snapshot.player.name" :company="company?.name" :role="shell.role.value" :live="live">
        <SecondaryButton icon="logout" @click="logout">{{ t('nav.logout') }}</SecondaryButton>
      </PlayerCard>
    </AppSidebar>
    <div class="flex min-w-0 grow flex-col">
      <TopBar v-if="server" :server="server.server" :title="shell.title.value" :live="live" @menu="menuOpen = true"/>
      <TickerStrip v-if="market.top.value.length > 0" :items="market.top.value"/>
      <main class="px-safe flex grow flex-col gap-4 pt-4 pb-28 md:px-6 md:pt-6 md:pb-8">
        <VersionBanner v-if="server && status !== 'discontinued'" :version="server.version" :deprecated="server.deprecated"/>
        <NoticeBanner v-if="server && !server.online && status === 'ready'" tone="problem">{{ t('dashboard.serverOffline') }}</NoticeBanner>
        <DiscontinuedNotice v-if="status === 'discontinued'" :server-name="server?.server.name" :version="server?.version ?? null" :message="problem"/>
        <NoticeBanner v-else-if="status === 'refused'" tone="problem">{{ problem ?? t('dashboard.refused') }}</NoticeBanner>
        <NoticeBanner v-else-if="status === 'offline'" tone="problem">{{ t('dashboard.offline') }}</NoticeBanner>
        <p v-else-if="!snapshot" class="m-0 text-sm text-ink-muted" role="status">{{ t('dashboard.loading') }}</p>
        <NoticeBanner v-else-if="shell.unavailable.value">{{ t('dashboard.unavailable') }}</NoticeBanner>
        <slot v-else/>
        <AppFooter class="mt-auto"/>
      </main>
    </div>
    <BottomTabBar v-if="status === 'ready'" :sections="shell.sections.value" :current="shell.current.value?.id" :badges="shell.badges.value"/>
    <MenuSheet v-model:open="menuOpen" :sections="shell.sections.value" @logout="logout"/>
  </div>
</template>
