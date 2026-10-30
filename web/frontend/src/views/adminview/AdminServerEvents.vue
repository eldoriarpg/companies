/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {AdminEventRow} from '~/api/gateway'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import RowButton from '@/components/button/RowButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PanelBox from '@/components/container/PanelBox.vue'
import PlainList from '@/components/container/PlainList.vue'
import ActionNotice from '@/components/feedback/ActionNotice.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import AdminServerEventForm from './AdminServerEventForm.vue'
import AdminServerEventRecord from './AdminServerEventRecord.vue'
import {dateTime} from '~/util/format'

/**
 * The server events, as `/companyadmin event` offers them: the newest events with their effect, state and times, and
 * one event with its scope, money, offers and the changes it allows. New events are created in a form.
 */
const {t, locale} = useI18n()
const loading = useAction()
const events = ref<AdminEventRow[] | null>(null)
const chosenId = ref<number | null>(null)
const creating = ref(false)
const chosen = computed(() => events.value?.find(event => event.id === chosenId.value) ?? null)

onMounted(() => {
  void load()
})

/** Loads the newest events. */
async function load(): Promise<void> {
  const listed = await loading.run('admin.events.list', {})
  if (listed) events.value = listed.events
}

/**
 * Shows an event as an action answered it and lists the events again.
 *
 * @param event the event afterwards
 */
async function changed(event: AdminEventRow): Promise<void> {
  creating.value = false
  chosenId.value = event.id
  await load()
}
</script>

<template>
  <div class="grid min-w-0 items-start gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)]">
    <PanelBox :aria-label="t('admin.serverevents.title')">
      <template #head>
        <SectionHeader>{{ t('admin.serverevents.title') }}</SectionHeader>
        <ButtonRow>
          <SecondaryButton :disabled="loading.busy.value" @click="load">{{ t('admin.serverevents.refresh') }}</SecondaryButton>
          <SecondaryButton data-testid="admin-event-new" @click="creating = true">{{ t('admin.serverevents.new') }}</SecondaryButton>
        </ButtonRow>
      </template>
      <ActionNotice :problem="loading.problem.value" class="mx-3 mt-3 md:mx-4"/>
      <template v-if="events">
        <EmptyState v-if="events.length === 0">{{ t('admin.serverevents.none') }}</EmptyState>
        <PlainList v-else data-testid="admin-events">
          <li v-for="event in events" :key="event.id">
            <RowButton :selected="chosenId === event.id" class="flex-col items-stretch gap-1 text-sm" @click="creating = false; chosenId = event.id">
              <span class="flex items-center gap-2">
                <span class="font-semibold">#{{ event.id }} {{ event.name }}</span>
                <PrimaryBadge>{{ t(`events.server.effect.${event.kind}`, {value: event.value}) }}</PrimaryBadge>
                <InfoBadge>{{ t(`admin.serverevents.state.${event.state}`) }}</InfoBadge>
              </span>
              <span class="tabular text-ink-muted">
                {{ t('admin.serverevents.period', {starts: dateTime(event.starts, locale), ends: dateTime(event.ends, locale)}) }}
              </span>
            </RowButton>
          </li>
        </PlainList>
      </template>
    </PanelBox>
    <PanelBox :aria-label="creating ? t('admin.serverevents.new') : t('admin.serverevents.record')">
      <template #head>
        <SectionHeader>{{ creating ? t('admin.serverevents.new') : t('admin.serverevents.record') }}</SectionHeader>
      </template>
      <AdminServerEventForm v-if="creating" @created="changed" @cancel="creating = false"/>
      <template v-else>
        <EmptyState v-if="!chosen">{{ t('admin.serverevents.choose') }}</EmptyState>
        <AdminServerEventRecord v-else :event="chosen" @changed="changed"/>
      </template>
    </PanelBox>
  </div>
</template>
