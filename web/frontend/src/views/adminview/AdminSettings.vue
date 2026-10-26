/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminSetting, AdminSettingsChange, AdminSettingsFile, AdminSettingsRevision, SettingsFileName} from '~/api/gateway'
import RowButton from '@/components/button/RowButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import Alert from '@/components/feedback/Alert.vue'
import ConfirmStep from '@/components/feedback/ConfirmStep.vue'
import SelectInput from '@/components/input/SelectInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {dateTime} from '~/util/format'
import {nodeLabel} from '~/util/nodes'
import {settingProblem} from '~/util/settings'
import AdminSettingField from './AdminSettingField.vue'
import AdminSettingsIcon from './AdminSettingsIcon.vue'

/**
 * The shared settings, as `/companyadmin settings` shows them in game: one file as its latest revision, grouped in
 * sections, with which settings need a restart and which this server sets for itself; how the servers stand on it;
 * and its history, where each revision shows what it changed. Secrets are never shown, only whether they are set.
 *
 * Every setting is an input. The changed ones are saved together, after asking once more, as the revision after the
 * one shown. A refused value is marked at its field. When someone saved the file meanwhile, nothing is saved: the
 * newer revision is loaded, the admin's changes are kept, and the fields the newer revision also changed show its
 * value, so the admin decides and saves again. An earlier revision can be made the newest again from the history.
 */
/** A revision saved after the one the admin edited, with what it holds of the admin's changes. */
interface NewerRevision {
  revision: number
  values: Record<string, unknown>
  changed: string[]
}

const FILES: SettingsFileName[] = ['config.yml', 'company.yml', 'order.yml', 'user.yml', 'system-orders.yml']

const {t, locale} = useI18n()
const {server} = useDashboard()
const loading = useAction()
const listing = useAction()
const opening = useAction()
const saving = useAction()
const rolling = useAction()
const draft = ref<Record<string, unknown>>({})
const errors = ref<Record<string, string>>({})
const newer = ref<NewerRevision | null>(null)
const done = ref('')
const saveRun = ref(0)
const file = ref<SettingsFileName>('config.yml')
const settings = ref<AdminSettingsFile | null>(null)
const revisions = ref<AdminSettingsChange[]>([])
const page = ref(1)
const more = ref(false)
const opened = ref<AdminSettingsRevision | null>(null)
const choices = computed(() => FILES.map(name => ({value: name, label: name})))
const busy = computed(() => loading.busy.value || listing.busy.value || opening.busy.value || saving.busy.value || rolling.busy.value)
const fileId = computed(() => file.value.replace('.yml', ''))
const editable = computed(() => settings.value?.editable === true)
const shownKeys = computed(() => new Set((settings.value?.settings ?? []).map(setting => setting.key)))
const unshownErrors = computed(() => Object.keys(errors.value).filter(key => key !== '' && !shownKeys.value.has(key)))
const changed = computed(() => (settings.value?.settings ?? []).filter(setting => JSON.stringify(draft.value[setting.key]) !== JSON.stringify(original(setting))))
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
  errors.value = {}
  newer.value = null
  settings.value = null
  opened.value = null
  revisions.value = []
  more.value = false
  page.value = 1
  const [current] = await Promise.all([loading.run('admin.settings.get', {file: chosen}), history()])
  if (!current || file.value !== chosen) return
  settings.value = current
  draft.value = Object.fromEntries(current.settings.map(setting => [setting.key, structuredClone(original(setting))]))
}

/**
 * @param setting a setting
 * @returns the value it has in the revision shown; none for a secret, which is kept unless one is entered
 */
function original(setting: AdminSetting): unknown {
  return setting.type === 'SECRET' ? undefined : setting.value
}

/**
 * @param setting a setting
 * @returns its name for the admin
 */
function label(setting: AdminSetting): string {
  return t(`admin.settings.fields.${fileId.value}.${setting.key}`)
}

/**
 * @param setting a setting
 * @returns its key, and what to know about it, under its input
 */
function hint(setting: AdminSetting): string {
  const parts = [setting.key]
  if (setting.restart) parts.push(t('admin.settings.restart'))
  if (setting.overridden) parts.push(t('admin.settings.overridden', {server: answering.value}))
  return parts.join(' · ')
}

/** Saves the changed settings as the next revision. */
async function save(): Promise<void> {
  const current = settings.value
  if (!current || changed.value.length === 0) return
  done.value = ''
  errors.value = {}
  const values = Object.fromEntries(changed.value.map(setting => [setting.key, draft.value[setting.key] ?? (setting.type === 'SECRET' ? undefined : null)]))
  const result = await saving.run('admin.settings.save', {file: file.value, base: current.revision, values})
  saveRun.value++
  if (!result) return
  if (result.status === 'invalid') {
    errors.value = result.fields
    return
  }
  if (result.status === 'newer') {
    await load()
    for (const [key, value] of Object.entries(values)) draft.value[key] = value
    newer.value = {revision: result.revision, values: result.values, changed: result.changed}
    return
  }
  await load()
  done.value = saved(result.status, result.revision, result.restart, result.applied)
}

/**
 * @param status what saving did
 * @param revision the stored or latest revision
 * @param restart the saved settings that need a restart
 * @param applied whether the answering server uses the stored revision
 * @returns the sentence for the admin
 */
function saved(status: string, revision: number, restart: string[], applied: boolean): string {
  if (status === 'unchanged') return t('admin.settings.unchanged', {revision})
  const parts = [t('admin.settings.saved', {revision})]
  if (restart.length > 0) parts.push(t('admin.settings.savedRestart', {settings: restart.join(', ')}))
  if (!applied) parts.push(t('admin.settings.notApplied', {server: answering.value}))
  return parts.join(' ')
}

/**
 * Shows what an icon upload or the return to the server icon did.
 *
 * @param result what the server answered
 * @param result.status what saving did
 * @param result.revision the stored or latest revision
 * @param result.refused why the icon was refused, if it was
 */
async function iconDone(result: {status: string, revision: number, refused?: string}): Promise<void> {
  done.value = ''
  if (result.refused) {
    errors.value = {'dashboard.branding.icon': result.refused}
    return
  }
  await load()
  done.value = result.status === 'newer'
    ? t('admin.settings.rollbackNewer', {revision: result.revision})
    : saved(result.status, result.revision, [], true)
}

/** Drops the changes not saved yet. */
function discard(): void {
  const current = settings.value
  if (!current) return
  errors.value = {}
  newer.value = null
  draft.value = Object.fromEntries(current.settings.map(setting => [setting.key, structuredClone(original(setting))]))
}

/**
 * @param setting a setting
 * @param reason why its value was refused
 * @returns the reason for the admin
 */
function problem(setting: AdminSetting, reason: string): string {
  return settingProblem(t, setting, reason)
}

/**
 * Makes an earlier revision the newest again.
 *
 * @param revision the earlier revision
 */
async function rollback(revision: number): Promise<void> {
  const current = settings.value
  if (!current) return
  done.value = ''
  const result = await rolling.run('admin.settings.rollback', {file: file.value, revision, base: current.revision})
  if (!result) return
  await load()
  done.value = result.status === 'newer'
    ? t('admin.settings.rollbackNewer', {revision: result.revision})
    : saved(result.status, result.revision, [], result.applied)
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
    <SelectInput v-model="file" :label="t('admin.settings.file')" :options="choices" :disabled="busy || changed.length > 0"/>
    <Alert v-if="settings && !editable" variant="notice">{{ t('admin.settings.readOnly') }}</Alert>
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
        <li v-for="setting in section.entries" :key="setting.key" class="flex flex-col gap-1 border-t border-line-soft px-3 py-2.5 text-sm md:px-4" :data-testid="`admin-setting-${setting.key}`">
          <AdminSettingField v-model="draft[setting.key]" :setting="setting" :label="label(setting)" :hint="hint(setting)" :disabled="!editable"/>
          <AdminSettingsIcon v-if="setting.key === 'dashboard.branding.icon' && editable && changed.length === 0 && settings" :base="settings.revision" :current="String(setting.value ?? '')" :overridden="setting.overridden" @done="iconDone"/>
          <span v-if="setting.type === 'SECRET'" class="text-xs text-ink-muted" data-testid="admin-setting-secret">{{ shown(setting) }}</span>
          <span v-if="errors[setting.key]" class="text-xs font-semibold text-problem-ink" role="alert">{{ problem(setting, errors[setting.key] ?? '') }}</span>
          <span v-if="newer && newer.changed.includes(setting.key)" class="text-xs font-semibold" data-testid="admin-setting-newer">{{ t('admin.settings.newerValue', {revision: newer.revision, value: setting.type === 'SECRET' ? t('admin.settings.secretChanged') : value(newer.values[setting.key] ?? setting.value)}) }}</span>
        </li>
      </PlainList>
    </PanelBox>
    <PanelBox v-if="settings && editable" padded :aria-label="t('admin.settings.save')" data-testid="admin-settings-save">
      <Alert v-if="newer" variant="notice">{{ t('admin.settings.newer', {revision: newer.revision, settings: newer.changed.join(', ') || '–'}) }}</Alert>
      <Alert v-if="errors['']" variant="problem">{{ t('admin.settings.problem.invalid') }}</Alert>
      <Alert v-if="unshownErrors.length > 0" variant="problem">{{ t('admin.settings.otherErrors', {settings: unshownErrors.join(', ')}) }}</Alert>
      <p class="text-sm">{{ changed.length === 0 ? t('admin.settings.noChange') : t('admin.settings.changes', {settings: changed.map(label).join(', ')}) }}</p>
      <ConfirmStep
          v-if="changed.length > 0"
          :key="saveRun"
          :trigger="t('admin.settings.save')"
          :question="t('admin.settings.saveQuestion', {count: changed.length})"
          :confirm="t('admin.settings.saveConfirm')"
          :busy="saving.busy.value"
          @confirmed="save"
      />
      <SecondaryButton v-if="changed.length > 0" :disabled="busy" @click="discard">{{ t('admin.settings.discard') }}</SecondaryButton>
      <ActionNotice :problem="saving.problem.value"/>
    </PanelBox>
    <Alert v-if="done" variant="success" data-testid="admin-settings-done">{{ done }}</Alert>
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
          <div v-if="opened?.revision === revision.revision && settings && editable && changed.length === 0 && revision.revision < settings.revision" class="border-b border-line-soft px-3 py-2 md:px-4">
            <ConfirmStep
                :trigger="t('admin.settings.rollback')"
                :question="t('admin.settings.rollbackQuestion', {revision: revision.revision})"
                :confirm="t('admin.settings.rollbackConfirm')"
                :busy="rolling.busy.value"
                @confirmed="rollback(revision.revision)"
            />
            <ActionNotice :problem="rolling.problem.value"/>
          </div>
        </li>
      </PlainList>
      <ActionNotice :problem="opening.problem.value" class="mx-3 mb-3 md:mx-4"/>
      <div v-if="more" class="px-3 pb-3 md:px-4">
        <SecondaryButton :disabled="listing.busy.value" @click="history">{{ t('admin.settings.more') }}</SecondaryButton>
      </div>
    </PanelBox>
  </div>
</template>
