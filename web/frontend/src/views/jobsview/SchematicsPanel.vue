/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {SchematicList, SchematicUpload} from '~/api/gateway'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import TextInput from '@/components/input/TextInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

/**
 * The schematics the player uploaded for build jobs: upload a `.schem` or `.schematic` file under a name, see what
 * cleaning removed from it, and delete one. The panel shows only while the server takes uploads or the player still
 * keeps some. Uploaded schematics are used in game by their name with `/order job blueprint` and `/order job build`.
 */
const {t} = useI18n()
const loading = useAction()
const action = useAction()
const list = ref<SchematicList | null>(null)
const name = ref('')
const file = ref<File | null>(null)
const problem = ref('')
const result = ref<SchematicUpload | null>(null)
const shown = computed(() => list.value !== null && (list.value.open || list.value.names.length > 0))

onMounted(async () => {
  list.value = await loading.run('jobs.schematics.list', {})
})

/**
 * Takes the chosen file and suggests its name.
 */
function choose(event: Event): void {
  const input = event.target as HTMLInputElement
  const chosen = input.files?.[0] ?? null
  input.value = ''
  problem.value = ''
  result.value = null
  file.value = null
  if (!chosen || !list.value) return
  if (chosen.size > list.value.maxBytes) {
    problem.value = t('jobs.schematics.tooLarge', {size: Math.floor(list.value.maxBytes / 1024)})
    return
  }
  file.value = chosen
  if (name.value.trim() === '') {
    name.value = chosen.name.replace(/\.[^.]+$/, '').toLowerCase().replace(/[^a-z0-9_-]/g, '_').slice(0, 32)
  }
}

/**
 * @param chosen a file
 * @returns its contents as base64
 */
function base64(chosen: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result).replace(/^data:[^,]*,/, ''))
    reader.onerror = () => reject(reader.error ?? new Error('unreadable'))
    reader.readAsDataURL(chosen)
  })
}

async function upload(): Promise<void> {
  if (!file.value) return
  let data: string
  try {
    data = await base64(file.value)
  } catch {
    problem.value = t('jobs.schematics.unreadable')
    return
  }
  const uploaded = await action.run('jobs.schematics.upload', {name: name.value.trim(), data})
  if (!uploaded) return
  result.value = uploaded
  list.value = uploaded.list
  file.value = null
  name.value = ''
}

async function remove(schematic: string): Promise<void> {
  const changed = await action.run('jobs.schematics.delete', {name: schematic})
  if (changed) list.value = changed
}
</script>

<template>
  <PanelBox v-if="shown && list" :aria-label="t('jobs.schematics.title')" data-testid="job-schematics">
    <template #head>
      <SectionHeader>{{ t('jobs.schematics.title') }}</SectionHeader>
    </template>
    <div class="flex flex-col gap-3 p-3 text-sm">
      <MutedText tag="p">{{ t('jobs.schematics.intro', {max: list.max}) }}</MutedText>
      <template v-if="list.open">
        <label class="flex flex-col gap-1">
          <span class="text-xs font-semibold text-ink-muted">{{ t('jobs.schematics.choose') }}</span>
          <input type="file" accept=".schem,.schematic" class="text-sm" data-testid="job-schematics-file" @change="choose"/>
        </label>
        <TextInput v-model="name" :label="t('jobs.schematics.name')" :maxlength="32" data-testid="job-schematics-name"/>
        <p v-if="problem" class="text-xs font-semibold text-problem-ink" role="alert">{{ problem }}</p>
        <ButtonRow>
          <PrimaryButton :disabled="!file || name.trim() === '' || action.busy.value" data-testid="job-schematics-upload" @click="upload">{{ t('jobs.schematics.upload') }}</PrimaryButton>
        </ButtonRow>
      </template>
      <MutedText v-else tag="p">{{ t('jobs.schematics.closed') }}</MutedText>
      <p v-if="result" data-testid="job-schematics-result">{{ t('jobs.schematics.stored', {name: result.name, blocks: result.blocks, removed: result.removed, data: result.data, entities: result.entities}) }}</p>
      <ActionNotice :problem="action.problem.value"/>
      <PlainList v-if="list.names.length > 0">
        <li v-for="schematic in list.names" :key="schematic" class="flex items-center justify-between gap-3 py-1">
          <span class="truncate">{{ schematic }}</span>
          <SecondaryButton :disabled="action.busy.value" :data-testid="`job-schematics-delete-${schematic}`" @click="remove(schematic)">{{ t('jobs.schematics.delete') }}</SecondaryButton>
        </li>
      </PlainList>
    </div>
  </PanelBox>
</template>
