/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import LabelledField from './LabelledField.vue'
import {FIELD_CLASSES} from './inputClasses'

/** A line of text typed by the player, with its label above it and an optional hint below. */
const model = defineModel<string>({default: ''})

defineProps<{
  label: string
  hint?: string
  maxlength?: number
  placeholder?: string
  disabled?: boolean
}>()
</script>

<template>
  <LabelledField v-slot="{id, hintId}" :label="label" :hint="hint" class="grow">
    <input
        :id="id"
        v-model="model"
        :maxlength="maxlength"
        :placeholder="placeholder"
        :disabled="disabled"
        :aria-describedby="hintId"
        autocomplete="off"
        :class="FIELD_CLASSES"
        class="w-full"
    />
  </LabelledField>
</template>
