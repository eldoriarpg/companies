/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {DeprecatedPart, VersionStatus} from '~/api/generated/protocol'
import NoticeBanner from '@/components/feedback/NoticeBanner.vue'

/**
 * The warning on every page of a server whose plugin version is deprecated: what the gateway says about it, when it
 * stops working and the version to update to. The deprecated parts the server still uses follow for players with admin
 * rights, whom the gateway sends them to.
 */
const props = defineProps<{
  version: VersionStatus
  deprecated: readonly DeprecatedPart[]
}>()

const {t, d} = useI18n()

const shows = computed(() => props.version.status === 'DEPRECATED' || props.deprecated.length > 0)

/**
 * A date of the version policy in the reader's format.
 *
 * @param date the date, `yyyy-mm-dd`
 * @returns the date as text
 */
function day(date: string): string {
  return d(new Date(`${date}T00:00:00`), {year: 'numeric', month: 'long', day: 'numeric'})
}
</script>

<template>
  <NoticeBanner v-if="shows">
    <template v-if="version.status === 'DEPRECATED'">
      <strong class="font-semibold">{{ version.discontinuedAt ? t('version.deprecatedUntil', {date: day(version.discontinuedAt)}) : t('version.deprecated') }}</strong>
      <span v-if="version.message">{{ version.message }}</span>
      <span v-if="version.updateTo">{{ t('version.updateTo', {version: version.updateTo}) }}</span>
    </template>
    <ul v-if="deprecated.length > 0" class="m-0 list-none p-0">
      <li v-for="part in deprecated" :key="`${part.kind}:${part.name}`">
        {{ part.message ?? t('version.part', {name: part.name, date: day(part.removedAt)}) }}
      </li>
    </ul>
  </NoticeBanner>
</template>
