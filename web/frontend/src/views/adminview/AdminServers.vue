/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminNode, AdminPriceSource} from '~/api/gateway'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import NoticeBadge from '@/components/badge/NoticeBadge.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import Alert from '@/components/feedback/Alert.vue'
import ConfirmStep from '@/components/feedback/ConfirmStep.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {Feature} from '~/api/generated/protocol'
import {dateTime} from '~/util/format'
import {nodeLabel} from '~/util/nodes'

/**
 * The servers of the network, as `/companyadmin node` and `syncconfiguration` offer them: every server registered in
 * the database with its role, version, when it was last seen and how it exchanges messages, an old one removed after
 * asking once more, the configuration synced from the primary to every server, and the shop plugins prices are read
 * from with what each gave, whether it runs on this server and how `prices.yml` treats it.
 */
const {t, locale} = useI18n()
const {server, supports} = useDashboard()
const loading = useAction()
const pricing = useAction()
const sources = ref<AdminPriceSource[] | null>(null)
const changing = useAction()
const syncing = useAction()
const nodes = ref<AdminNode[] | null>(null)
const done = ref('')
const syncRun = ref(0)
const names = computed(() => new Map((server.value?.nodes ?? []).map(node => [node.uid, node.name])))

onMounted(() => {
  if (supports(Feature['admin.nodes'])) {
    void load()
    void loadPrices()
  }
})

/** Loads the shop plugins prices are read from. */
async function loadPrices(): Promise<void> {
  const result = await pricing.run('admin.nodes.prices', {})
  if (result) sources.value = result.sources
}

/** Loads the servers. */
async function load(): Promise<void> {
  const result = await loading.run('admin.nodes.list', {})
  if (result) nodes.value = result.nodes
}

/**
 * @param node a server
 * @returns its name for the admin
 */
function label(node: AdminNode): string {
  return nodeLabel({uid: node.uid, name: names.value.get(node.uid)}, t(`admin.servers.role.${node.role}`))
}

/**
 * Removes an old server.
 *
 * @param node the server
 */
async function remove(node: AdminNode): Promise<void> {
  done.value = ''
  const result = await changing.run('admin.nodes.remove', {uid: node.uid})
  if (!result) return
  nodes.value = result.nodes
  done.value = t('admin.servers.removed', {server: label(node)})
}

/** Syncs the configuration. */
async function sync(): Promise<void> {
  done.value = ''
  if (!await syncing.run('admin.config.sync', {})) return
  syncRun.value++
  done.value = t('admin.servers.synced')
}
</script>

<template>
  <div class="flex min-w-0 flex-col gap-4">
    <Alert v-if="done" variant="success">{{ done }}</Alert>
    <PanelBox v-if="supports(Feature['admin.nodes'])" :aria-label="t('admin.servers.title')">
      <template #head>
        <SectionHeader>{{ t('admin.servers.title') }}</SectionHeader>
      </template>
      <ActionNotice :problem="loading.problem.value" class="mx-3 mt-3 md:mx-4"/>
      <PlainList v-if="nodes" data-testid="admin-servers">
        <li v-for="node in nodes" :key="node.uid" class="flex flex-col gap-1 border-t border-line-soft px-3 py-2.5 text-sm md:px-4">
          <span class="flex flex-wrap items-center gap-2 font-semibold">{{ label(node) }}<InfoBadge v-if="node.current">{{ t('admin.servers.current') }}</InfoBadge></span>
          <span class="text-xs text-ink-muted">{{ t('admin.servers.facts', {role: t(`admin.servers.role.${node.role}`), version: node.version, seen: node.lastSeen ? dateTime(node.lastSeen, locale) : t('admin.servers.never'), bus: node.bus}) }}</span>
          <span v-if="node.behind || node.restart" class="flex flex-wrap gap-2 text-xs">
            <NoticeBadge v-if="node.behind">{{ t('admin.servers.behind') }}</NoticeBadge>
            <NoticeBadge v-if="node.restart">{{ t('admin.servers.restart', {settings: node.restart}) }}</NoticeBadge>
          </span>
          <ConfirmStep
              v-if="!node.current"
              :trigger="t('admin.servers.remove')"
              :question="t('admin.servers.removeQuestion', {server: label(node)})"
              :confirm="t('admin.servers.removeConfirm')"
              :busy="changing.busy.value"
              @confirmed="remove(node)"
          />
        </li>
      </PlainList>
      <ActionNotice :problem="changing.problem.value" class="mx-3 mb-3 md:mx-4"/>
    </PanelBox>
    <PanelBox v-if="supports(Feature['admin.nodes'])" :aria-label="t('admin.servers.prices.title')">
      <template #head>
        <SectionHeader>{{ t('admin.servers.prices.title') }}</SectionHeader>
      </template>
      <ActionNotice :problem="pricing.problem.value" class="mx-3 mt-3 md:mx-4"/>
      <p v-if="sources && sources.length === 0" class="px-3 py-2.5 text-sm text-ink-muted md:px-4">{{ t('admin.servers.prices.none') }}</p>
      <PlainList v-else-if="sources" data-testid="admin-price-sources">
        <li v-for="source in sources" :key="source.id" class="flex flex-col gap-1 border-t border-line-soft px-3 py-2.5 text-sm md:px-4">
          <span class="flex flex-wrap items-center gap-2 font-semibold">
            {{ source.id }}
            <InfoBadge v-if="source.here">{{ t('admin.servers.prices.here') }}</InfoBadge>
            <NoticeBadge v-if="!source.enabled">{{ t('admin.servers.prices.off') }}</NoticeBadge>
            <InfoBadge v-if="source.adminOnly">{{ t('admin.servers.prices.adminOnly') }}</InfoBadge>
          </span>
          <span class="text-xs text-ink-muted">{{ t('admin.servers.prices.facts', {servers: source.servers, offers: source.offers, offered: source.offeredItems, trades: source.trades, traded: source.tradedItems, baselines: source.baselines}) }}</span>
        </li>
      </PlainList>
    </PanelBox>
    <PanelBox v-if="supports(Feature['admin.config'])" padded :aria-label="t('admin.servers.config')">
      <SectionHeader>{{ t('admin.servers.config') }}</SectionHeader>
      <ConfirmStep :key="syncRun" :trigger="t('admin.servers.sync')" :question="t('admin.servers.syncQuestion')" :confirm="t('admin.servers.syncConfirm')" :busy="syncing.busy.value" @confirmed="sync"/>
      <ActionNotice :problem="syncing.problem.value"/>
    </PanelBox>
  </div>
</template>
