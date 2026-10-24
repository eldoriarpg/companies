/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import LabelledField from './LabelledField.vue'
import {FIELD_CLASSES} from './inputClasses'

/** Several lines of text typed by the player, such as an item's lore, with its label above it. */
const model = defineModel<string>({default: ''})

defineProps<{
  label: string
  hint?: string
  rows?: number
  maxlength?: number
  disabled?: boolean
}>()
</script>

<template>
  <LabelledField v-slot="{id, hintId}" :label="label" :hint="hint">
    <textarea
        :id="id"
        v-model="model"
        :rows="rows ?? 3"
        :maxlength="maxlength"
        :disabled="disabled"
        :aria-describedby="hintId"
        :class="FIELD_CLASSES"
        class="w-full py-2"
    />
  </LabelledField>
</template>
