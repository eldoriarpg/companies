/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import {useNow} from '@vueuse/core'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Alert from '@/components/feedback/Alert.vue'
import SearchInput from '@/components/input/SearchInput.vue'
import ToggleSwitch from '@/components/input/ToggleSwitch.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import PageSwitcher from '@/components/navigation/PageSwitcher.vue'
import {when} from '~/util/format'
import {openLabel} from '~/util/labels'

/**
 * The inbox: what the server told the player, newest first, unread in bold, searched by the plugin. Everything can be
 * marked read, the read entries cleared, and each category that allows it muted, as with the in-game inbox.
 */
const {t, te, locale} = useI18n()
const inbox = useInbox()
const action = useAction()
const now = useNow({interval: 60_000})
const muted = computed(() => new Set(inbox.result.value?.muted ?? []))
const mutable = computed(() => inbox.result.value?.mutable ?? [])
const done = ref('')

/** Marks every entry read. */
async function readAll(): Promise<void> {
  const result = await action.run('inbox.read-all', {})
  if (!result) return
  done.value = t('inbox.markedRead', {count: result.marked})
  await inbox.load()
}

/** Deletes the read entries. */
async function clearRead(): Promise<void> {
  const result = await action.run('inbox.clear', {})
  if (!result) return
  done.value = t('inbox.cleared', {count: result.deleted})
  await inbox.load()
}

/**
 * Mutes or unmutes a category.
 *
 * @param category the category
 * @param mute whether to mute it
 */
async function setMuted(category: string, mute: boolean): Promise<void> {
  done.value = ''
  if (await action.run('inbox.mute', {category, mute})) await inbox.load()
}

onMounted(() => void inbox.load())
</script>

<template>
  <ViewContent :title="t('nav.inbox')">
    <PanelBox>
      <template #head>
        <div class="w-full md:w-80">
          <SearchInput v-model="inbox.query.value" :label="t('inbox.search')"/>
        </div>
      </template>
      <div class="flex flex-col gap-3 border-b border-line-soft px-3 py-3 md:px-4">
        <ButtonRow>
          <SecondaryButton :disabled="action.busy.value" @click="readAll">{{ t('inbox.readAll') }}</SecondaryButton>
          <SecondaryButton :disabled="action.busy.value" @click="clearRead">{{ t('inbox.clearRead') }}</SecondaryButton>
        </ButtonRow>
        <div v-if="mutable.length" class="flex flex-wrap gap-2" role="group" :aria-label="t('inbox.mutes')">
          <ToggleSwitch
              v-for="category in mutable"
              :key="category"
              :model-value="muted.has(category)"
              :label="t('inbox.mute', {category: openLabel({t, te}, `inbox.category.${category}`, category)})"
              :disabled="action.busy.value"
              @update:model-value="value => setMuted(category, value)"
          />
        </div>
        <Alert v-if="done && action.problem.value === null" variant="success">{{ done }}</Alert>
        <ActionNotice :problem="action.problem.value"/>
      </div>
      <Alert v-if="inbox.status.value === 'failed'" variant="problem">{{ t('inbox.failed') }}</Alert>
      <EmptyState v-else-if="!inbox.result.value?.entries.length" :loading="inbox.status.value === 'loading'">{{ t('inbox.empty') }}</EmptyState>
      <ol v-else class="m-0 list-none p-0" data-testid="inbox">
        <li v-for="entry in inbox.result.value.entries" :key="entry.id" class="flex flex-col gap-1 border-b border-line-soft px-3 py-3 last:border-b-0 md:flex-row md:gap-4 md:px-4">
          <span class="tabular shrink-0 text-[0.6875rem] text-ink-muted md:w-24 md:text-xs">{{ when(entry.created, now, locale) }}</span>
          <span class="flex min-w-0 flex-col gap-0.5">
            <span :class="entry.read ? 'font-normal' : 'font-bold'" class="text-sm">
              {{ openLabel({t, te}, `inbox.type.${entry.type}`, t('inbox.message')) }}
              <span v-if="!entry.read" class="sr-only">{{ t('inbox.unread') }}</span>
            </span>
            <span class="text-sm leading-snug text-ink-muted">{{ entry.text }}</span>
          </span>
        </li>
      </ol>
    </PanelBox>
    <PageSwitcher v-model:page="inbox.page.value" :pages="inbox.result.value?.pages ?? 1"/>
  </ViewContent>
</template>
