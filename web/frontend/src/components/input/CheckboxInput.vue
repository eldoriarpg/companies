/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {ref, useId, watchPostEffect} from 'vue'

/**
 * A native checkbox in the accent colour with its label beside it, both inside one 44 pixel target. It shows the
 * browser's mixed state for a box standing for partly selected entries.
 */
const model = defineModel<boolean>({default: false})

const props = defineProps<{
  label: string
  disabled?: boolean
  /** The mixed state, for a box standing for partly selected entries. Display only. */
  indeterminate?: boolean
}>()

const id = useId()
const box = ref<HTMLInputElement | null>(null)

watchPostEffect(() => {
  if (box.value) box.value.indeterminate = props.indeterminate ?? false
})
</script>

<template>
  <label :for="id" class="inline-flex min-h-11 cursor-pointer items-center gap-2 text-sm text-ink has-disabled:cursor-not-allowed has-disabled:opacity-50">
    <input
        :id="id"
        ref="box"
        v-model="model"
        type="checkbox"
        :disabled="disabled"
        class="size-4 cursor-pointer accent-accent disabled:cursor-not-allowed"
    />
    <span>{{ label }}</span>
  </label>
</template>
