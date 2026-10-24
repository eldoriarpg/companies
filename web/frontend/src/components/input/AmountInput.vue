/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'

/** An amount of money typed by the player, with its label above it; a phone opens its decimal keyboard. */
const model = defineModel<string>({default: ''})

defineProps<{
  label: string
}>()

const id = useId()
</script>

<template>
  <div class="flex flex-col gap-1">
    <label :for="id" class="text-xs font-semibold text-ink">{{ label }}</label>
    <input :id="id" v-model="model" inputmode="decimal" autocomplete="off" class="tabular min-h-11 w-36 border border-line bg-ground px-3 text-sm text-ink focus:border-accent focus:outline-none"/>
  </div>
</template>
