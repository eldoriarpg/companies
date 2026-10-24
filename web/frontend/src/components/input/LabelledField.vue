/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'

/**
 * One field with its label above it and, where there is one, a line underneath saying what it is for. The slot is
 * handed the `id` the label points at and the `hintId` the field is described by; binding both on the field is what
 * ties the three together for a screen reader.
 */
defineProps<{
  label: string
  hint?: string
}>()

const id = useId()
const hintId = useId()
</script>

<template>
  <div class="flex min-w-0 flex-col gap-1">
    <label :for="id" class="text-xs font-semibold text-ink">{{ label }}</label>
    <slot :id="id" :hint-id="hint ? hintId : undefined"/>
    <span v-if="hint" :id="hintId" class="text-xs text-ink-muted">{{ hint }}</span>
  </div>
</template>
