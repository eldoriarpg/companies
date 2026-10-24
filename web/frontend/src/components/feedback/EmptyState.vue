/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import Spinner from './Spinner.vue'

/**
 * What a list shows when it holds nothing, or nothing matches its search. While the list is still loading it says so
 * with a spinner instead, and marks itself busy for a screen reader.
 */
defineProps<{
  loading?: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <p :aria-busy="loading ? 'true' : undefined" class="m-0 flex items-center justify-center gap-2 px-3 py-6 text-center text-sm text-ink-muted">
    <template v-if="loading">
      <Spinner size="sm"/>
      <span>{{ t('common.loading') }}</span>
    </template>
    <slot v-else/>
  </p>
</template>
