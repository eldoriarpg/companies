/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {computed, ref, useId, watch} from 'vue'
import LabelledField from './LabelledField.vue'
import {FIELD_CLASSES} from './inputClasses'

/**
 * A number typed as text and read by the caller's parser, so a German "1.000,50" means what the player meant rather
 * than what a browser's number field makes of it. The model holds the number, or nothing while the field is empty,
 * unreadable or outside its bounds; the field then shows itself invalid and says underneath what it expects. Setting
 * the model from outside rewrites the text, unless the text already reads as that number.
 */
const model = defineModel<number | undefined>()

const props = defineProps<{
  label: string
  hint?: string
  parse: (text: string) => number
  /** What the field says when the text is no number it can read. */
  unreadable: string
  inputmode: 'numeric' | 'decimal'
  min?: number
  max?: number
  placeholder?: string
  disabled?: boolean
}>()

const {t} = useI18n()
const errorId = useId()
const text = ref(shown(model.value))
const error = computed(() => text.value.trim() === '' ? '' : problemOf(props.parse(text.value)))

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
  return problemOf(number) === '' ? number : undefined
}

/**
 * What is wrong with a number the field read.
 *
 * @param number the number, NaN when the text was none
 * @returns what the field says, empty when the number is fine
 */
function problemOf(number: number): string {
  if (Number.isNaN(number)) return props.unreadable
  if (props.min !== undefined && number < props.min) return t('input.atLeast', {min: props.min})
  if (props.max !== undefined && number > props.max) return t('input.atMost', {max: props.max})
  return ''
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
        :aria-describedby="[hintId, error ? errorId : undefined].filter(Boolean).join(' ') || undefined"
        :aria-invalid="error ? 'true' : undefined"
        :aria-errormessage="error ? errorId : undefined"
        autocomplete="off"
        :class="FIELD_CLASSES"
        class="tabular w-36"
        @input="onInput"
    />
    <span v-if="error" :id="errorId" class="text-xs text-loss">{{ error }}</span>
  </LabelledField>
</template>
