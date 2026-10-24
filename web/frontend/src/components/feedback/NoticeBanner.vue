/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import AppIcon from '@/components/display/AppIcon.vue'

/** A message across the page: amber for a notice the reader should act on, red for a problem. */
withDefaults(defineProps<{
  tone?: 'notice' | 'problem'
}>(), {tone: 'notice'})
</script>

<template>
  <div
      :role="tone === 'problem' ? 'alert' : 'status'"
      :class="tone === 'problem' ? 'border-problem-line bg-problem text-problem-ink' : 'border-notice-line bg-notice text-notice-ink'"
      class="flex items-start gap-2 border px-3 py-2.5 text-sm leading-snug"
  >
    <AppIcon name="warning" :size="18"/>
    <div class="flex min-w-0 flex-col gap-1">
      <slot/>
    </div>
  </div>
</template>
