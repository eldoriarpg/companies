/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import AppIcon from '@/components/display/AppIcon.vue'
import {FIELD_CLASSES} from '../inputClasses'

/**
 * The button a dropdown opens from where it fills its field: it looks like the field's other inputs,
 * reads the current choice from its slot and points down, or up while the list is open.
 */
defineProps<{
  open?: boolean
  disabled?: boolean
}>()
</script>

<template>
  <button type="button" aria-haspopup="listbox" :disabled="disabled"
          :class="[FIELD_CLASSES, 'flex w-full min-w-0 items-center gap-2 text-left']">
    <span class="min-w-0 flex-1 truncate"><slot/></span>
    <AppIcon name="chevronDown" :size="14" :class="['text-ink-muted transition-transform duration-150', open ? 'rotate-180' : '']"/>
  </button>
</template>
