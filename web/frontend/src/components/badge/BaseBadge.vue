/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
/**
 * The square label every badge is built from. The colour variants name their edge, background and text classes; on
 * the fixed dark palette all of them are known up front, so nothing has to be measured.
 */
withDefaults(defineProps<{
  boxClass: string
  textClass?: string
}>(), {
  textClass: 'text-ink',
})
</script>

<template>
  <span class="inline-flex items-center gap-1 border px-2 py-0.5 text-xs font-semibold" :class="[boxClass, textClass]">
    <slot/>
  </span>
</template>
