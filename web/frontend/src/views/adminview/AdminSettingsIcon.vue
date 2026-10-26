/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'

/**
 * The dashboard icon of every server. By default each server shows its own `server-icon.png`. An admin may upload one
 * PNG of at most 256×256 pixels and 40 KB instead, which is stored in the database and shown by every server; the icon
 * is checked here before it is sent and again on the server. Going back to the server icon is one click.
 */
const props = defineProps<{
  /** The revision of config.yml shown, which the upload is saved on top of. */
  base: number
  /** The icon setting as stored: a file name, or `shared:` with an uploaded icon. */
  current: string
}>()

const emit = defineEmits<{
  /** The icon changed: the sentence for the admin, or a refused reason key. */
  done: [result: {status: string, revision: number, refused?: string}]
}>()

const MAX_BYTES = 40 * 1024
const MAX_SIZE = 256
const SERVER_ICON = 'server-icon.png'

const {t} = useI18n()
const uploading = useAction()
const resetting = useAction()
const chosen = ref<string | null>(null)
const problem = ref('')
const shared = computed(() => props.current.startsWith('shared:'))

/**
 * Checks the chosen file and shows it.
 *
 * @param event the change of the file input
 */
async function choose(event: Event): Promise<void> {
  chosen.value = null
  problem.value = ''
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.type !== 'image/png') {
    problem.value = t('admin.settings.icon.notPng')
    return
  }
  if (file.size > MAX_BYTES) {
    problem.value = t('admin.settings.icon.tooLarge')
    return
  }
  const url = await dataUrl(file)
  const size = await dimensions(url)
  if (!size || size.width > MAX_SIZE || size.height > MAX_SIZE) {
    problem.value = t('admin.settings.icon.tooWide')
    return
  }
  chosen.value = url
}

/**
 * @param file a file
 * @returns it as a data URL
 */
function dataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(reader.error ?? new Error('unreadable'))
    reader.readAsDataURL(file)
  })
}

/**
 * @param url an image as a data URL
 * @returns its size, none when it cannot be shown
 */
function dimensions(url: string): Promise<{width: number, height: number} | null> {
  return new Promise(resolve => {
    const image = new Image()
    image.onload = () => resolve({width: image.naturalWidth, height: image.naturalHeight})
    image.onerror = () => resolve(null)
    image.src = url
  })
}

/** Uploads the chosen icon for every server. */
async function upload(): Promise<void> {
  const url = chosen.value
  if (!url) return
  const result = await uploading.run('admin.settings.icon', {file: 'config.yml', base: props.base, png: url.slice(url.indexOf(',') + 1)})
  if (!result) return
  chosen.value = null
  emit('done', {status: result.status, revision: result.revision, refused: result.fields['dashboard.branding.icon']})
}

/** Goes back to each server's own server icon. */
async function reset(): Promise<void> {
  const result = await resetting.run('admin.settings.save', {file: 'config.yml', base: props.base, values: {'dashboard.branding.icon': SERVER_ICON}})
  if (result) emit('done', {status: result.status, revision: result.revision})
}
</script>

<template>
  <div class="flex flex-col gap-2 text-sm" data-testid="admin-settings-icon">
    <p>{{ shared ? t('admin.settings.icon.shared') : t('admin.settings.icon.perServer', {file: current || SERVER_ICON}) }}</p>
    <label class="flex flex-col gap-1">
      <span class="text-xs font-semibold text-ink-muted">{{ t('admin.settings.icon.choose') }}</span>
      <input type="file" accept="image/png" class="text-sm" @change="choose"/>
    </label>
    <p class="text-xs text-ink-muted">{{ t('admin.settings.icon.limits') }}</p>
    <p v-if="problem" class="text-xs font-semibold text-problem-ink" role="alert">{{ problem }}</p>
    <img v-if="chosen" :src="chosen" :alt="t('admin.settings.icon.preview')" class="size-16 border border-line object-contain" data-testid="admin-settings-icon-preview"/>
    <ButtonRow>
      <PrimaryButton v-if="chosen" :disabled="uploading.busy.value" @click="upload">{{ t('admin.settings.icon.upload') }}</PrimaryButton>
      <SecondaryButton v-if="shared" :disabled="resetting.busy.value" @click="reset">{{ t('admin.settings.icon.reset') }}</SecondaryButton>
    </ButtonRow>
    <ActionNotice :problem="uploading.problem.value ?? resetting.problem.value"/>
  </div>
</template>
