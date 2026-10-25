/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminLevel, AdminLevels} from '~/api/gateway'
import ButtonRow from '@/components/button/ButtonRow.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import AdminLevelForm from './AdminLevelForm.vue'
import {money} from '~/util/format'

/**
 * The company levels for an admin, as `/companyadmin level` and `calclevel` offer them: every level with what it
 * takes, one level edited in a form, levels added and removed, and every company's level recalculated. Only the
 * primary server makes changes; it writes them to the database for the other servers.
 */
const {t, locale} = useI18n()
const loading = useAction()
const changing = useAction()
const levels = ref<AdminLevels | null>(null)
const selected = ref<number | null>(null)
const done = ref('')
const revision = ref(0)
const notice = useTemplateRef<HTMLElement>('notice')
const shown = computed(() => levels.value?.levels.find(level => level.level === selected.value) ?? null)

onMounted(load)

/** Loads the levels. */
async function load(): Promise<void> {
  const result = await loading.run('admin.levels.list', {})
  if (result) levels.value = result
}

/** Adds a level at the end and opens it. */
async function add(): Promise<void> {
  done.value = ''
  const created = await changing.run('admin.levels.create', {})
  if (!created) return
  await load()
  selected.value = created.level
}

/** Recalculates the level of every company. */
async function recalculate(): Promise<void> {
  done.value = ''
  if (await changing.run('admin.levels.recalculate', {})) done.value = t('admin.levels.recalculating')
}

/**
 * Follows a saved level, which may have moved. The form is rebuilt, so the focus moves to what happened.
 *
 * @param level the level as saved
 */
async function saved(level: AdminLevel): Promise<void> {
  await load()
  revision.value++
  selected.value = level.level
  await tell(t('admin.levels.saved', {level: level.level}))
}

/**
 * Shows the levels left after a removal. The form is gone, so the focus moves to what happened.
 *
 * @param remaining the levels left
 */
async function removed(remaining: AdminLevels): Promise<void> {
  levels.value = remaining
  selected.value = null
  await tell(t('admin.levels.removed'))
}

/**
 * Shows what happened and hands it the focus.
 *
 * @param text what happened
 */
async function tell(text: string): Promise<void> {
  done.value = text
  await nextTick()
  notice.value?.focus()
}

/**
 * Opens a level.
 *
 * @param level the level's number
 */
function open(level: number): void {
  done.value = ''
  selected.value = level
}
</script>

<template>
  <div class="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
    <PanelBox :aria-label="t('admin.levels.title')">
      <template #head>
        <SectionHeader>{{ t('admin.levels.title') }}</SectionHeader>
      </template>
      <ActionNotice :problem="loading.problem.value" class="mx-3 mt-3 md:mx-4"/>
      <Spinner v-if="!levels && loading.busy.value" class="m-4"/>
      <template v-if="levels">
        <Alert v-if="!levels.editable" variant="notice" class="mx-3 mt-3 md:mx-4">{{ t('admin.levels.secondary') }}</Alert>
        <PlainList data-testid="admin-levels">
          <li v-for="level in levels.levels" :key="level.level" class="flex flex-col gap-0.5 border-t border-line-soft px-3 py-2.5 md:px-4">
            <LinkButton class="truncate text-sm" @click="open(level.level)">{{ level.level }} · {{ level.name }}</LinkButton>
            <span class="text-xs text-ink-muted">{{ t('admin.levels.summary', {orders: level.orderCount, members: level.memberCount, money: money(level.earnedMoney, locale), items: level.deliveredItems}) }}</span>
          </li>
        </PlainList>
      </template>
      <ButtonRow class="border-t border-line-soft px-3 py-2.5 md:px-4">
        <SecondaryButton v-if="levels?.editable" :disabled="changing.busy.value" @click="add">{{ t('admin.levels.add') }}</SecondaryButton>
        <SecondaryButton :disabled="changing.busy.value" @click="recalculate">{{ t('admin.levels.recalculate') }}</SecondaryButton>
      </ButtonRow>
      <ActionNotice :problem="changing.problem.value" class="mx-3 mb-3 md:mx-4"/>
    </PanelBox>
    <div class="flex min-w-0 flex-col gap-4">
      <div v-if="done" ref="notice" tabindex="-1" data-testid="admin-levels-done">
        <Alert variant="success">{{ done }}</Alert>
      </div>
      <AdminLevelForm
          v-if="shown && levels"
          :key="`${shown.level}-${revision}`"
          :level="shown"
          :count="levels.levels.length"
          :editable="levels.editable"
          @saved="saved"
          @removed="removed"
      />
    </div>
  </div>
</template>
