/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import LabelledField from './LabelledField.vue'
import {FIELD_CLASSES} from './inputClasses'

/**
 * A day picked by the player, as `yyyy-mm-dd`, with its label above it. The browser draws its own calendar, which on
 * a phone is the system's date wheel.
 */
const model = defineModel<string>({default: ''})

defineProps<{
  label: string
  hint?: string
  /** The first day that may be picked, as `yyyy-mm-dd`. */
  min?: string
  /** The last day that may be picked, as `yyyy-mm-dd`. */
  max?: string
  disabled?: boolean
}>()
</script>

<template>
  <LabelledField v-slot="{id, hintId}" :label="label" :hint="hint">
    <input
        :id="id"
        v-model="model"
        type="date"
        :min="min"
        :max="max"
        :disabled="disabled"
        :aria-describedby="hintId"
        :class="FIELD_CLASSES"
        class="tabular w-44"
    />
  </LabelledField>
</template>
