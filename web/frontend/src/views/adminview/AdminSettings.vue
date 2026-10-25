/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminSetting, AdminSettingsChange, AdminSettingsFile, AdminSettingsRevision, SettingsFileName} from '~/api/gateway'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import NoticeBadge from '@/components/badge/NoticeBadge.vue'
import RowButton from '@/components/button/RowButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import SelectInput from '@/components/input/SelectInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {dateTime} from '~/util/format'
import {nodeLabel} from '~/util/nodes'

/**
 * The shared settings, as `/companyadmin settings` shows them in game: one file as its latest revision, grouped in
 * sections, with which settings need a restart and which this server sets for itself; how the servers stand on it;
 * and its history, where each revision shows what it changed. Secrets are never shown, only whether they are set.
 */
const FILES: SettingsFileName[] = ['config.yml', 'company.yml', 'order.yml', 'user.yml']

const {t, locale} = useI18n()
const {server} = useDashboard()
const loading = useAction()
const listing = useAction()
const opening = useAction()
const file = ref<SettingsFileName>('config.yml')
const settings = ref<AdminSettingsFile | null>(null)
const revisions = ref<AdminSettingsChange[]>([])
const page = ref(1)
const more = ref(false)
const opened = ref<AdminSettingsRevision | null>(null)
const choices = computed(() => FILES.map(name => ({value: name, label: name})))
const busy = computed(() => loading.busy.value || listing.busy.value || opening.busy.value)
const answering = computed(() => (settings.value ? serverName(settings.value.node) : ''))
const nodes = computed(() => new Map((server.value?.nodes ?? []).map(node => [node.uid, node])))
const sections = computed(() => {
  const grouped = new Map<string, AdminSetting[]>()
  for (const setting of settings.value?.settings ?? []) {
    grouped.set(setting.section, [...grouped.get(setting.section) ?? [], setting])
  }
  return [...grouped.entries()].map(([name, entries]) => ({name, entries}))
})

watch(file, load, {immediate: true})

/** Loads the chosen file and the first page of its history. */
async function load(): Promise<void> {
  const chosen = file.value
  settings.value = null
  opened.value = null
  revisions.value = []
  more.value = false
  page.value = 1
  const [current] = await Promise.all([loading.run('admin.settings.get', {file: chosen}), history()])
  if (current && file.value === chosen) settings.value = current
}

/** Loads the next page of the history. */
async function history(): Promise<void> {
  const chosen = file.value
  const result = await listing.run('admin.settings.history', {file: chosen, page: page.value})
  if (!result || file.value !== chosen) return
  revisions.value = [...revisions.value, ...result.revisions]
  more.value = result.revisions.length === result.pageSize
  page.value++
}

/**
 * Shows what a revision changed.
 *
 * @param revision the revision
 */
async function open(revision: AdminSettingsChange): Promise<void> {
  if (opened.value?.revision === revision.revision) {
    opened.value = null
    return
  }
  const chosen = file.value
  const result = await opening.run('admin.settings.revision', {file: chosen, revision: revision.revision})
  if (result && file.value === chosen) opened.value = result
}

/**
 * @param uid a server's uid
 * @returns its name for the admin
 */
function serverName(uid: string): string {
  const node = nodes.value.get(uid)
  return node ? nodeLabel(node, t(`admin.servers.role.${node.role}`)) : uid.slice(0, 8)
}

/**
 * @param setting a setting
 * @returns its value as the admin reads it
 */
function shown(setting: AdminSetting): string {
  if (setting.type === 'SECRET') {
    return setting.secret?.set ? t(`admin.settings.secret.${setting.secret.source}`, {server: answering.value}) : t('admin.settings.secret.none')
  }
  if (setting.type === 'FLAG_MAP') {
    const off = Object.entries((setting.value ?? {}) as Record<string, boolean>).filter(([, on]) => !on).map(([name]) => name)
    return off.length === 0 ? t('admin.settings.allOn') : t('admin.settings.off', {names: off.join(', ')})
  }
  return value(setting.value)
}

/**
 * @param raw a stored value
 * @returns it as the admin reads it
 */
function value(raw: unknown): string {
  if (raw === undefined || raw === null) return '–'
  if (typeof raw === 'boolean') return raw ? t('admin.settings.on') : t('admin.settings.offValue')
  if (Array.isArray(raw)) return raw.length === 0 ? '–' : raw.join(', ')
  if (typeof raw === 'object') return JSON.stringify(raw)
  if (raw === '') return '–'
  return String(raw)
}
</script>

<template>
  <div class="flex min-w-0 flex-col gap-4">
    <SelectInput v-model="file" :label="t('admin.settings.file')" :options="choices" :disabled="busy"/>
    <ActionNotice :problem="loading.problem.value"/>
    <PanelBox v-if="settings" padded :aria-label="t('admin.settings.status')" data-testid="admin-settings-status">
      <SectionHeader>{{ t('admin.settings.status') }}</SectionHeader>
      <p class="text-sm">{{ t('admin.settings.revisions', {latest: settings.revision, server: answering, applied: settings.applied}) }}</p>
      <p v-if="settings.editedHere" class="text-sm text-ink-muted">{{ t('admin.settings.editedHere', {server: answering}) }}</p>
      <p v-if="settings.behind.length > 0" class="text-sm text-ink-muted">{{ t('admin.settings.behind', {servers: settings.behind.map(serverName).join(', ')}) }}</p>
    </PanelBox>
    <PanelBox v-for="section in sections" :key="section.name" :aria-label="t(`admin.settings.section.${section.name}`)">
      <template #head>
        <SectionHeader>{{ t(`admin.settings.section.${section.name}`) }}</SectionHeader>
      </template>
      <PlainList :data-testid="`admin-settings-${section.name}`">
        <li v-for="setting in section.entries" :key="setting.key" class="flex flex-col gap-1 border-t border-line-soft px-3 py-2.5 text-sm md:flex-row md:items-baseline md:justify-between md:px-4">
          <span class="flex flex-wrap items-center gap-2 font-mono text-xs">{{ setting.key }}<NoticeBadge v-if="setting.restart">{{ t('admin.settings.restart') }}</NoticeBadge><InfoBadge v-if="setting.overridden">{{ t('admin.settings.overridden', {server: answering}) }}</InfoBadge></span>
          <span class="min-w-0 break-words" :data-testid="`admin-setting-${setting.key}`">{{ shown(setting) }}</span>
        </li>
      </PlainList>
    </PanelBox>
    <PanelBox :aria-label="t('admin.settings.history')">
      <template #head>
        <SectionHeader>{{ t('admin.settings.history') }}</SectionHeader>
      </template>
      <ActionNotice :problem="listing.problem.value" class="mx-3 mt-3 md:mx-4"/>
      <PlainList data-testid="admin-settings-history">
        <li v-for="revision in revisions" :key="revision.revision" class="text-sm">
          <RowButton :selected="opened?.revision === revision.revision" :aria-expanded="opened?.revision === revision.revision" @click="open(revision)">
            {{ t('admin.settings.entry', {revision: revision.revision, actor: revision.actor, server: serverName(revision.node), time: dateTime(revision.changed, locale)}) }}
          </RowButton>
          <ul v-if="opened?.revision === revision.revision" class="flex flex-col gap-0.5 border-b border-line-soft px-3 py-2 text-xs md:px-4" :data-testid="`admin-settings-revision-${revision.revision}`">
            <li v-if="opened.first" class="text-ink-muted">{{ t('admin.settings.first') }}</li>
            <li v-else-if="opened.changes.length === 0" class="text-ink-muted">{{ t('admin.settings.noChanges') }}</li>
            <li v-for="change in opened.changes" :key="change.key">
              <span class="font-mono">{{ change.key }}</span>
              <span v-if="change.secret" class="text-ink-muted"> · {{ t('admin.settings.secretChanged') }}</span>
              <span v-else-if="change.fileOnly" class="text-ink-muted"> · {{ t('admin.settings.fileOnly') }}</span>
              <span v-else> · {{ value(change.before) }} → {{ value(change.after) }}</span>
            </li>
          </ul>
        </li>
      </PlainList>
      <ActionNotice :problem="opening.problem.value" class="mx-3 mb-3 md:mx-4"/>
      <div v-if="more" class="px-3 pb-3 md:px-4">
        <SecondaryButton :disabled="listing.busy.value" @click="history">{{ t('admin.settings.more') }}</SecondaryButton>
      </div>
    </PanelBox>
  </div>
</template>
