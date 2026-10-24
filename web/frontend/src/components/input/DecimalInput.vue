/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import NumericField from './NumericField.vue'
import {parseAmount} from '~/util/format'

/**
 * An amount with a fractional part typed by the player, such as money, read with either a point or a comma as the
 * decimal mark; a phone opens its decimal keyboard.
 */
const model = defineModel<number | undefined>()

defineProps<{
  label: string
  hint?: string
  min?: number
  max?: number
  placeholder?: string
  disabled?: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <NumericField
      v-model="model"
      :label="label"
      :hint="hint"
      :min="min"
      :max="max"
      :placeholder="placeholder"
      :disabled="disabled"
      :unreadable="t('input.amount')"
      :parse="parseAmount"
      inputmode="decimal"
  />
</template>
