/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import LabelledField from './LabelledField.vue'
import {FIELD_CLASSES} from './inputClasses'

/**
 * A number typed as text and read by the caller's parser, so a German "1.000,50" means what the player meant rather
 * than what a browser's number field makes of it. The model holds the number, or nothing while the field is empty,
 * unreadable or outside its bounds; the field then shows itself invalid. Setting the model from outside rewrites the
 * text, unless the text already reads as that number.
 */
const model = defineModel<number | undefined>()

const props = defineProps<{
  label: string
  hint?: string
  parse: (text: string) => number
  inputmode: 'numeric' | 'decimal'
  min?: number
  max?: number
  placeholder?: string
  disabled?: boolean
}>()

const text = ref(shown(model.value))
const invalid = computed(() => text.value.trim() !== '' && read(text.value) === undefined)

watch(model, value => {
  if (value !== read(text.value)) text.value = shown(value)
})

/**
 * A number as the field shows it.
 *
 * @param value the number
 * @returns the text, empty without a number
 */
function shown(value: number | undefined): string {
  return value === undefined ? '' : String(value)
}

/**
 * The number a text stands for, within the bounds.
 *
 * @param value what was typed
 * @returns the number, or undefined when there is none or it is out of bounds
 */
function read(value: string): number | undefined {
  const number = props.parse(value)
  if (Number.isNaN(number)) return undefined
  if (props.min !== undefined && number < props.min) return undefined
  if (props.max !== undefined && number > props.max) return undefined
  return number
}

/**
 * Takes what was typed.
 *
 * @param event the input event
 */
function onInput(event: Event): void {
  text.value = (event.target as HTMLInputElement).value
  model.value = read(text.value)
}
</script>

<template>
  <LabelledField v-slot="{id, hintId}" :label="label" :hint="hint">
    <input
        :id="id"
        :value="text"
        :inputmode="inputmode"
        :placeholder="placeholder"
        :disabled="disabled"
        :aria-describedby="hintId"
        :aria-invalid="invalid ? 'true' : undefined"
        autocomplete="off"
        :class="FIELD_CLASSES"
        class="tabular w-36"
        @input="onInput"
    />
  </LabelledField>
</template>
