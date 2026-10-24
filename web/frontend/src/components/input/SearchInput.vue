/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'
import AppIcon from '@/components/display/AppIcon.vue'

/**
 * The search bar of a list: a magnifier, a field 44 pixels high, and a label a screen reader reads while the eye reads
 * the placeholder. Every list that can hold more than ten entries has one.
 */
const model = defineModel<string>({default: ''})

defineProps<{
  label: string
  placeholder?: string
}>()

const id = useId()
</script>

<template>
  <div class="relative w-full min-w-0">
    <label :for="id" class="sr-only">{{ label }}</label>
    <span class="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-ink-faint">
      <AppIcon name="search" :size="16"/>
    </span>
    <input
        :id="id"
        v-model="model"
        type="search"
        :placeholder="placeholder ?? label"
        autocomplete="off"
        enterkeyhint="search"
        class="h-11 w-full border border-line bg-ground pl-9 pr-3 text-sm text-ink outline-none transition-colors hover:border-ink-faint focus:border-accent"
    />
  </div>
</template>
