/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'

/** A labelled native choice, 44 pixels high, so phones open their own picker. */
const model = defineModel<string>({required: true})

defineProps<{
  label: string
  options: readonly {value: string, label: string}[]
  hideLabel?: boolean
}>()

const id = useId()
</script>

<template>
  <div class="flex min-w-0 items-center gap-2">
    <label :for="id" :class="hideLabel ? 'sr-only' : 'text-xs font-semibold text-ink-muted'">{{ label }}</label>
    <select
        :id="id"
        v-model="model"
        class="h-11 min-w-0 border border-line bg-ground px-2 text-sm text-ink outline-none focus:border-accent"
    >
      <option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option>
    </select>
  </div>
</template>
