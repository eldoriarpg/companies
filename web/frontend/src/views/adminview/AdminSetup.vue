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
import AdminSettingField from './AdminSettingField.vue'
import AdminSettingsIcon from './AdminSettingsIcon.vue'

/**
 * The guided setup: a few pages of the most important settings in a sensible order, each saved like the settings
 * editor saves (as the next revision, checked against the one shown) when the admin goes on, or skipped. How far it
 * got is kept for the whole network, so it opens at the first page not done, on any server and for any admin. Pages
 * without settings explain something or open another tool, such as the level editor.
 */
const emit = defineEmits<{
  /** Opens another admin tab, such as the level editor. */
  open: [tab: string]
  /** The setup's progress changed. */
  changed: [setup: AdminSetup]
}>()

const {t} = useI18n()
const loading = useAction()
const saving = useAction()
const marking = useAction()
const setup = ref<AdminSetup | null>(null)
const files = ref<Partial<Record<SettingsFileName, AdminSettingsFile>>>({})
const draft = ref<Record<string, unknown>>({})
const errors = ref<Record<string, string>>({})
const notice = ref('')
const restart = ref<string[]>([])
const current = ref('')
const pages = computed(() => setup.value?.pages ?? [])
const page = computed(() => pages.value.find(entry => entry.id === current.value) ?? null)
const editable = computed(() => Object.values(files.value).every(file => file?.editable === true))
const finished = computed(() => pages.value.length > 0 && pages.value.every(entry => entry.state !== 'open'))

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

/** Loads the settings files the pages show and resets the draft to them. */
async function loadFiles(): Promise<void> {
  const names = [...new Set(pages.value.flatMap(entry => entry.fields.map(field => field.file as SettingsFileName)))]
  const loaded: Partial<Record<SettingsFileName, AdminSettingsFile>> = {}
  for (const name of names) {
    const result = await loading.run('admin.settings.get', {file: name})
    if (result) loaded[name] = result
  }
  files.value = loaded
  draft.value = Object.fromEntries(Object.values(loaded).flatMap(file => (file?.settings ?? []).map(setting => [id(file!.file, setting.key), structuredClone(original(setting))])))
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
 * Saves the changed settings of the page, one revision per file, notes the page as done and goes on.
 *
 * @param target the page
 */
async function next(target: AdminSetupPage): Promise<void> {
  notice.value = ''
  errors.value = {}
  const byFile = new Map<SettingsFileName, Record<string, unknown>>()
  for (const {file, setting} of fields(target)) {
    const value = draft.value[id(file, setting.key)]
    if (JSON.stringify(value) === JSON.stringify(original(setting))) continue
    byFile.set(file, {...byFile.get(file), [setting.key]: value ?? (setting.type === 'SECRET' ? undefined : null)})
  }
  for (const [file, values] of byFile) {
    const base = files.value[file]?.revision ?? 0
    const result = await saving.run('admin.settings.save', {file, base, values})
    if (!result) return
    if (result.status === 'invalid') {
      errors.value = Object.fromEntries(Object.entries(result.fields).map(([key, reason]) => [id(file, key), reason]))
      return
    }
    if (result.status === 'newer') {
      const kept = {...draft.value}
      await loadFiles()
      draft.value = {...draft.value, ...Object.fromEntries(Object.keys(values).map(key => [id(file, key), kept[id(file, key)]]))}
      notice.value = t('admin.setup.newer', {file})
      return
    }
    restart.value = [...new Set([...restart.value, ...result.restart])]
  }
  await mark(target, 'done')
}

/**
 * Notes a page and goes to the next one not done.
 *
 * @param target the page
 * @param state done or skipped
 */
async function mark(target: AdminSetupPage, state: 'done' | 'skipped'): Promise<void> {
  const result = await marking.run('admin.setup.mark', {page: target.id, state})
  if (!result) return
  setup.value = result
  emit('changed', result)
  await loadFiles()
  const index = result.pages.findIndex(entry => entry.id === target.id)
  current.value = result.pages.slice(index + 1).find(entry => entry.state === 'open')?.id
    ?? result.pages.find(entry => entry.state === 'open')?.id
    ?? 'summary'
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

/** Reloads the files after an icon upload, which saves on its own. */
async function iconDone(): Promise<void> {
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
          <RowButton :selected="current === 'summary'" @click="go('summary')">{{ t('admin.setup.summary') }}</RowButton>
        </li>
      </PlainList>
    </PanelBox>
    <PanelBox v-if="page" padded :aria-label="t(`admin.setup.pages.${page.id}.title`)" data-testid="admin-setup-page">
      <SectionHeader>{{ t(`admin.setup.pages.${page.id}.title`) }}</SectionHeader>
      <p class="text-sm">{{ t(`admin.setup.pages.${page.id}.intro`) }}</p>
      <Alert v-if="!editable" variant="notice">{{ t('admin.settings.readOnly') }}</Alert>
      <Alert v-if="notice" variant="notice">{{ notice }}</Alert>
      <div v-for="{file, setting} in fields(page)" :key="id(file, setting.key)" class="flex flex-col gap-1" :data-testid="`admin-setup-field-${setting.key}`">
        <AdminSettingField v-model="draft[id(file, setting.key)]" :setting="setting" :label="label(file, setting)" :hint="hint(setting)" :disabled="!editable"/>
        <AdminSettingsIcon v-if="setting.key === 'dashboard.branding.icon' && editable" :base="files[file]?.revision ?? 0" :current="String(setting.value ?? '')" :overridden="setting.overridden" @done="iconDone"/>
        <span v-if="errors[id(file, setting.key)]" class="text-xs font-semibold text-problem-ink" role="alert">{{ t(`admin.settings.problem.${errors[id(file, setting.key)]}`, {min: setting.min ?? 0, max: setting.max ?? 0}) }}</span>
      </div>
      <SecondaryButton v-if="page.id === 'levels'" @click="emit('open', 'levels')">{{ t('admin.setup.pages.levels.open') }}</SecondaryButton>
      <ul v-if="page.id === 'permissions'" class="flex list-disc flex-col gap-1 pl-5 font-mono text-xs">
        <li>lp group default permission set companies.orders.limit.5 true</li>
        <li>lp group default permission set companies.orders.repeat true</li>
        <li>lp group admin permission set companies.admin true</li>
        <li>lp group admin permission set companies.admin.reload true</li>
      </ul>
      <ButtonRow v-if="editable">
        <PrimaryButton :disabled="saving.busy.value || marking.busy.value" @click="next(page)">{{ page.fields.length > 0 ? t('admin.setup.saveNext') : t('admin.setup.next') }}</PrimaryButton>
        <SecondaryButton :disabled="saving.busy.value || marking.busy.value" @click="mark(page, 'skipped')">{{ t('admin.setup.skip') }}</SecondaryButton>
      </ButtonRow>
      <ActionNotice :problem="saving.problem.value ?? marking.problem.value"/>
    </PanelBox>
    <PanelBox v-else-if="current === 'summary'" padded :aria-label="t('admin.setup.summary')" data-testid="admin-setup-summary">
      <SectionHeader>{{ t('admin.setup.summary') }}</SectionHeader>
      <p class="text-sm">{{ finished ? t('admin.setup.finished') : t('admin.setup.unfinished') }}</p>
      <ul class="flex flex-col gap-1 text-sm">
        <li v-for="entry in pages" :key="entry.id">{{ t(`admin.setup.pages.${entry.id}.title`) }} · {{ t(`admin.setup.state.${entry.state}`) }}</li>
      </ul>
      <Alert v-if="restart.length > 0" variant="notice">{{ t('admin.setup.restart', {settings: restart.join(', ')}) }}</Alert>
    </PanelBox>
  </div>
</template>
