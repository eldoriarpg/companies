/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useNow} from '@vueuse/core'
import PanelBox from '@/components/container/PanelBox.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NoticeBanner from '@/components/feedback/NoticeBanner.vue'
import SearchInput from '@/components/input/SearchInput.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import PageSwitcher from '@/components/navigation/PageSwitcher.vue'
import {when} from '~/util/format'
import {openLabel} from '~/util/labels'

/**
 * The inbox: what the server told the player, newest first, unread in bold, searched by the plugin. Marking read,
 * clearing and muting come with the inbox's actions.
 */
const {t, te, locale} = useI18n()
const inbox = useInbox()
const now = useNow({interval: 60_000})

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
      <NoticeBanner v-if="inbox.status.value === 'failed'" tone="problem">{{ t('inbox.failed') }}</NoticeBanner>
      <EmptyState v-else-if="!inbox.result.value?.entries.length">{{ inbox.status.value === 'loading' ? t('common.loading') : t('inbox.empty') }}</EmptyState>
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
