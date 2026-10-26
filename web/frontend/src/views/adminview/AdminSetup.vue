/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminSetting, AdminSettingsFile, AdminSetup, AdminSetupPage, SettingsFileName} from '~/api/gateway'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import RowButton from '@/components/button/RowButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {Feature} from '~/api/generated/protocol'
import {changedValue, settingProblem} from '~/util/settings'
import AdminSettingField from './AdminSettingField.vue'
import AdminSettingsIcon from './AdminSettingsIcon.vue'

/**
 * The guided setup: a few pages of the most important settings in a sensible order, each saved like the settings
 * editor saves (as the next revision, checked against the one shown) when the admin goes on, or skipped. How far it
 * got is kept for the whole network, so it opens at the first page not done, on any server and for any admin. Pages
 * without settings explain something or open another tool, such as the level editor.
 *
 * A page may save to several files, one revision each. The files are loaded again after every save, upload or mark,
 * and the admin's unsaved edits are kept across all of them, so a refused or outdated file never costs the edits of
 * another. A page whose files could not be loaded cannot be saved.
 */
const emit = defineEmits<{
  /** Opens another admin tab, such as the level editor. */
  open: [tab: string]
  /** The setup's progress changed. */
  changed: [setup: AdminSetup]
}>()

/** A permission example of the permissions page: the command and what it does. */
interface PermissionExample {
  command: string
  note: string
}

const {t} = useI18n()
const {supports} = useDashboard()
const loading = useAction()
const fetching = useAction()
const saving = useAction()
const marking = useAction()
const setup = ref<AdminSetup | null>(null)
const files = ref<Partial<Record<SettingsFileName, AdminSettingsFile>>>({})
const failed = ref<SettingsFileName[]>([])
const draft = ref<Record<string, unknown>>({})
const errors = ref<Record<string, string>>({})
const notice = ref('')
const restart = ref<string[]>([])
const current = ref('')
const reloading = ref(false)
const pages = computed(() => setup.value?.pages ?? [])
const page = computed(() => pages.value.find(entry => entry.id === current.value) ?? null)
const needed = computed(() => [...new Set(pages.value.flatMap(entry => entry.fields.map(field => field.file as SettingsFileName)))])
const editable = computed(() => setup.value?.editable === true && failed.value.length === 0 && needed.value.every(name => files.value[name]?.editable === true))
const busy = computed(() => reloading.value || loading.busy.value || saving.busy.value || marking.busy.value)
const finished = computed(() => pages.value.length > 0 && pages.value.every(entry => entry.state !== 'open'))
const shownKeys = computed(() => new Set(page.value ? fields(page.value).map(({file, setting}) => id(file, setting.key)) : []))
const unshownErrors = computed(() => Object.keys(errors.value).filter(key => !shownKeys.value.has(key)).map(key => key.split(':').pop() || t('admin.setup.wholeFile')))
const permissions = computed<PermissionExample[]>(() => [
  {command: 'lp group default permission set companies.orders.limit.15 true', note: t('admin.setup.pages.permissions.limit')},
  {command: 'lp group default permission set companies.orders.repeat false', note: t('admin.setup.pages.permissions.repeat')},
  {command: 'lp group admin permission set companies.admin.* true', note: t('admin.setup.pages.permissions.admin')},
])

onMounted(load)

/** Loads the setup and the files its pages show, and opens the first page not done. */
async function load(): Promise<void> {
  const result = await loading.run('admin.setup.get', {})
  if (!result) return
  setup.value = result
  emit('changed', result)
  await loadFiles()
  current.value = result.pages.find(entry => entry.state === 'open')?.id ?? 'summary'
}

/**
 * Loads the settings files the pages show, keeping the admin's unsaved edits. A file that cannot be loaded keeps its
 * last answer and its edits; a file just saved starts from what is stored, so an entered secret is not sent again.
 *
 * @param saved the files just saved
 */
async function loadFiles(saved: ReadonlySet<SettingsFileName> = new Set()): Promise<void> {
  reloading.value = true
  const before = files.value
  const kept = draft.value
  const loaded: Partial<Record<SettingsFileName, AdminSettingsFile>> = {}
  const missing: SettingsFileName[] = []
  try {
    for (const name of needed.value) {
      const result = await fetching.run('admin.settings.get', {file: name})
      if (result) {
        loaded[name] = result
        continue
      }
      missing.push(name)
      if (before[name]) loaded[name] = before[name]
    }
  } finally {
    reloading.value = false
  }
  const next: Record<string, unknown> = {}
  for (const file of Object.values(loaded)) {
    for (const setting of file?.settings ?? []) {
      const key = id(file!.file, setting.key)
      const old = before[file!.file]?.settings.find(entry => entry.key === setting.key)
      const dirty = !saved.has(file!.file) && old !== undefined && key in kept && changedValue(kept[key], original(old))
      next[key] = dirty ? kept[key] : structuredClone(original(setting))
    }
  }
  files.value = loaded
  failed.value = missing
  draft.value = next
}

/**
 * @param file a settings file
 * @param key a setting's key
 * @returns the draft's key of that setting
 */
function id(file: string, key: string): string {
  return `${file}:${key}`
}

/**
 * @param setting a setting
 * @returns its value as stored; none for a secret, which is kept unless one is entered
 */
function original(setting: AdminSetting): unknown {
  return setting.type === 'SECRET' ? undefined : setting.value
}

/**
 * @param target a page
 * @returns its settings, with the file each belongs to
 */
function fields(target: AdminSetupPage): {file: SettingsFileName, setting: AdminSetting}[] {
  return target.fields.flatMap(field => {
    const setting = files.value[field.file as SettingsFileName]?.settings.find(entry => entry.key === field.key)
    return setting ? [{file: field.file as SettingsFileName, setting}] : []
  })
}

/**
 * @param target a page
 * @returns whether every setting of the page could be loaded
 */
function complete(target: AdminSetupPage): boolean {
  return fields(target).length === target.fields.length
}

/**
 * @param file a settings file
 * @param setting one of its settings
 * @returns its name for the admin
 */
function label(file: string, setting: AdminSetting): string {
  return t(`admin.settings.fields.${file.replace('.yml', '')}.${setting.key}`)
}

/**
 * @param setting a setting
 * @returns what to know about it, under its input
 */
function hint(setting: AdminSetting): string {
  return setting.restart ? `${setting.key} · ${t('admin.settings.restart')}` : setting.key
}

/**
 * Saves the changed settings of the page, one revision per file, notes the page as done and goes on. Stops at the
 * first file that is refused or was saved meanwhile; the files are loaded again and every unsaved edit is kept.
 *
 * @param target the page
 */
async function next(target: AdminSetupPage): Promise<void> {
  notice.value = ''
  errors.value = {}
  const byFile = new Map<SettingsFileName, Record<string, unknown>>()
  for (const {file, setting} of fields(target)) {
    const value = draft.value[id(file, setting.key)]
    if (!changedValue(value, original(setting))) continue
    byFile.set(file, {...byFile.get(file), [setting.key]: value ?? (setting.type === 'SECRET' ? undefined : null)})
  }
  const saved = new Set<SettingsFileName>()
  for (const [file, values] of byFile) {
    const base = files.value[file]?.revision ?? 0
    const result = await saving.run('admin.settings.save', {file, base, values})
    if (!result || result.status === 'invalid' || result.status === 'newer') {
      if (result?.status === 'invalid') errors.value = Object.fromEntries(Object.entries(result.fields).map(([key, reason]) => [id(file, key), reason]))
      if (result?.status === 'newer') notice.value = t('admin.setup.newer', {file})
      await loadFiles(saved)
      return
    }
    saved.add(file)
    restart.value = [...new Set([...restart.value, ...result.restart])]
  }
  await mark(target, 'done', saved)
}

/**
 * Notes a page and goes to the next one not done.
 *
 * @param target the page
 * @param state done or skipped
 * @param saved the files the page just saved
 */
async function mark(target: AdminSetupPage, state: 'done' | 'skipped', saved: ReadonlySet<SettingsFileName> = new Set()): Promise<void> {
  const result = await marking.run('admin.setup.mark', {page: target.id, state})
  if (!result) return
  setup.value = result
  emit('changed', result)
  const index = result.pages.findIndex(entry => entry.id === target.id)
  current.value = result.pages.slice(index + 1).find(entry => entry.state === 'open')?.id
    ?? result.pages.find(entry => entry.state === 'open')?.id
    ?? 'summary'
  await loadFiles(saved)
}

/**
 * Opens a page again.
 *
 * @param target the page
 */
function go(target: string): void {
  notice.value = ''
  errors.value = {}
  current.value = target
}

/**
 * Shows what an icon upload did and loads the files again, keeping the other edits of the page.
 *
 * @param result what the server answered
 * @param result.status what saving did
 * @param result.revision the stored or latest revision
 * @param result.refused why the icon was refused, if it was
 */
async function iconDone(result: {status: string, revision: number, refused?: string}): Promise<void> {
  notice.value = ''
  if (result.refused) {
    errors.value = {[id('config.yml', 'dashboard.branding.icon')]: result.refused}
    return
  }
  const iconKey = id('config.yml', 'dashboard.branding.icon')
  errors.value = Object.fromEntries(Object.entries(errors.value).filter(([key]) => key !== iconKey))
  if (result.status === 'newer') notice.value = t('admin.setup.newer', {file: 'config.yml'})
  await loadFiles()
}
</script>

<template>
  <div class="grid min-w-0 items-start gap-4 lg:grid-cols-[minmax(0,16rem)_minmax(0,1fr)]">
    <PanelBox :aria-label="t('admin.setup.steps')">
      <template #head>
        <SectionHeader>{{ t('admin.setup.steps') }}</SectionHeader>
      </template>
      <ActionNotice :problem="loading.problem.value" class="mx-3 mt-3 md:mx-4"/>
      <PlainList data-testid="admin-setup-steps">
        <li v-for="entry in pages" :key="entry.id">
          <RowButton :selected="current === entry.id" @click="go(entry.id)">
            <span class="flex-1">{{ t(`admin.setup.pages.${entry.id}.title`) }}</span>
            <span class="text-xs text-ink-muted">{{ t(`admin.setup.state.${entry.state}`) }}</span>
          </RowButton>
        </li>
        <li>
          <RowButton :selected="current === 'summary' || !page" @click="go('summary')">{{ t('admin.setup.summary') }}</RowButton>
        </li>
      </PlainList>
    </PanelBox>
    <PanelBox v-if="page" padded :aria-label="t(`admin.setup.pages.${page.id}.title`)" data-testid="admin-setup-page">
      <SectionHeader>{{ t(`admin.setup.pages.${page.id}.title`) }}</SectionHeader>
      <p class="text-sm">{{ t(`admin.setup.pages.${page.id}.intro`) }}</p>
      <Alert v-if="setup && !setup.editable" variant="notice">{{ t('admin.settings.readOnly') }}</Alert>
      <Alert v-if="failed.length > 0" variant="problem">{{ t('admin.setup.loadFailed', {files: failed.join(', ')}) }}</Alert>
      <Alert v-if="notice" variant="notice">{{ notice }}</Alert>
      <Alert v-if="unshownErrors.length > 0" variant="problem">{{ t('admin.settings.otherErrors', {settings: unshownErrors.join(', ')}) }}</Alert>
      <div v-for="{file, setting} in fields(page)" :key="id(file, setting.key)" class="flex flex-col gap-1" :data-testid="`admin-setup-field-${setting.key}`">
        <AdminSettingField v-model="draft[id(file, setting.key)]" :setting="setting" :label="label(file, setting)" :hint="hint(setting)" :disabled="!editable"/>
        <span v-if="setting.type === 'SECRET' && setting.secret?.source !== 'environment'" class="text-xs text-ink-muted">{{ setting.secret?.set ? t('admin.settings.secret.file') : t('admin.settings.secret.none') }}</span>
        <AdminSettingsIcon v-if="setting.key === 'dashboard.branding.icon' && editable" :base="files[file]?.revision ?? 0" :current="String(setting.value ?? '')" :overridden="setting.overridden" @done="iconDone"/>
        <span v-if="errors[id(file, setting.key)]" class="text-xs font-semibold text-problem-ink" role="alert">{{ settingProblem(t, setting, errors[id(file, setting.key)] ?? '') }}</span>
      </div>
      <SecondaryButton v-if="page.id === 'levels' && supports(Feature['admin.levels'])" @click="emit('open', 'levels')">{{ t('admin.setup.pages.levels.open') }}</SecondaryButton>
      <ul v-if="page.id === 'permissions'" class="flex flex-col gap-2 text-sm" data-testid="admin-setup-permissions">
        <li v-for="example in permissions" :key="example.command" class="flex flex-col gap-0.5">
          <code class="font-mono text-xs">{{ example.command }}</code>
          <span class="text-xs text-ink-muted">{{ example.note }}</span>
        </li>
      </ul>
      <ButtonRow v-if="setup?.editable">
        <PrimaryButton :disabled="busy || (page.fields.length > 0 && (!editable || !complete(page)))" @click="next(page)">{{ page.fields.length > 0 ? t('admin.setup.saveNext') : t('admin.setup.next') }}</PrimaryButton>
        <SecondaryButton :disabled="busy" @click="mark(page, 'skipped')">{{ t('admin.setup.skip') }}</SecondaryButton>
      </ButtonRow>
      <ActionNotice :problem="saving.problem.value ?? marking.problem.value"/>
    </PanelBox>
    <PanelBox v-else-if="current" padded :aria-label="t('admin.setup.summary')" data-testid="admin-setup-summary">
      <SectionHeader>{{ t('admin.setup.summary') }}</SectionHeader>
      <p class="text-sm">{{ finished ? t('admin.setup.finished') : t('admin.setup.unfinished') }}</p>
      <ul class="flex flex-col gap-1 text-sm">
        <li v-for="entry in pages" :key="entry.id">{{ t(`admin.setup.pages.${entry.id}.title`) }} · {{ t(`admin.setup.state.${entry.state}`) }}</li>
      </ul>
      <Alert v-if="restart.length > 0" variant="notice">{{ t('admin.setup.restart', {settings: restart.join(', ')}) }}</Alert>
    </PanelBox>
  </div>
</template>
