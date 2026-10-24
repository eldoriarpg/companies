/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {VersionStatus} from '~/api/generated/protocol'
import Alert from './Alert.vue'

/**
 * What every page of a server shows once its plugin version is discontinued: the gateway's explanation, since when,
 * and what the server owner has to do. Nothing else of the dashboard opens.
 */
defineProps<{
  serverName?: string
  version: VersionStatus | null
  message?: string | null
}>()

const {t, d} = useI18n()
</script>

<template>
  <Alert variant="problem">
    <strong class="font-semibold">{{ serverName ? t('version.discontinuedFor', {server: serverName}) : t('version.discontinued') }}</strong>
    <span v-if="version?.message ?? message">{{ version?.message ?? message }}</span>
    <span v-if="version?.since">{{ t('version.since', {date: d(new Date(`${version.since}T00:00:00`), {year: 'numeric', month: 'long', day: 'numeric'})}) }}</span>
    <span>{{ version?.updateTo ? t('version.askOwnerTo', {version: version.updateTo}) : t('version.askOwner') }}</span>
  </Alert>
</template>
