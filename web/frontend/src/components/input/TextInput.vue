/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'

/** A line of text typed by the player, with its label above it and an optional hint below. */
const model = defineModel<string>({default: ''})

const props = defineProps<{
  label: string
  hint?: string
  maxlength?: number
  inputmode?: 'text' | 'numeric' | 'decimal'
}>()

const id = useId()
const hintId = useId()
</script>

<template>
  <div class="flex min-w-0 grow flex-col gap-1">
    <label :for="id" class="text-xs font-semibold text-ink">{{ label }}</label>
    <input
        :id="id"
        v-model="model"
        :maxlength="props.maxlength"
        :inputmode="props.inputmode ?? 'text'"
        :aria-describedby="hint ? hintId : undefined"
        autocomplete="off"
        class="min-h-11 w-full border border-line bg-ground px-3 text-sm text-ink focus:border-accent focus:outline-none"
    />
    <span v-if="hint" :id="hintId" class="text-xs text-ink-muted">{{ hint }}</span>
  </div>
</template>
