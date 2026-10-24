/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import AppIcon from '@/components/display/AppIcon.vue'
import type {IconName} from '~/util/icons'

/** A square button showing only an icon, 44 pixels for a thumb; the label is what a screen reader says. */
defineProps<{
  icon: IconName
  label: string
}>()

defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<template>
  <button
      type="button"
      :aria-label="label"
      :title="label"
      class="inline-flex size-11 shrink-0 items-center justify-center border border-line bg-ground text-ink transition-colors hover:border-ink-faint"
      @click="$emit('click', $event)"
  >
    <AppIcon :name="icon" :size="18"/>
  </button>
</template>
