/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'

/**
 * A labelled text field written large in the number face, for the short codes of the login: the short id and the
 * six letters. Letters are kept upper case as they are typed.
 */
const model = defineModel<string>({default: ''})

const props = defineProps<{
  label: string
  hint?: string
  maxlength: number
  placeholder?: string
  autocomplete?: string
  invalid?: boolean
}>()

const id = useId()
const hintId = useId()

/**
 * Takes what was typed, upper case and cut to the field's length.
 *
 * @param event the input event
 */
function onInput(event: Event): void {
  const field = event.target as HTMLInputElement
  const value = field.value.toUpperCase().replace(/\s+/g, '').slice(0, props.maxlength)
  field.value = value
  model.value = value
}
</script>

<template>
  <div class="flex flex-col gap-2">
    <label :for="id" class="text-sm font-semibold text-ink-muted">{{ label }}</label>
    <input
        :id="id"
        :value="model"
        :maxlength="maxlength"
        :placeholder="placeholder"
        :autocomplete="autocomplete ?? 'off'"
        :aria-invalid="invalid ? 'true' : undefined"
        :aria-describedby="hint ? hintId : undefined"
        autocapitalize="characters"
        spellcheck="false"
        :class="invalid ? 'border-loss' : 'border-line'"
        class="tabular h-16 w-full border bg-surface px-4 text-3xl uppercase tracking-[0.4em] text-ink outline-none focus:border-accent"
        @input="onInput"
    />
    <span v-if="hint" :id="hintId" class="text-xs text-ink-muted">{{ hint }}</span>
  </div>
</template>
