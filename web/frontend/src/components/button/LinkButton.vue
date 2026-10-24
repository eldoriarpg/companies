/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
/** A button that reads as a link in running text, such as the name of an order in a table that opens its details. */
defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<template>
  <button
      type="button"
      class="min-h-11 bg-transparent p-0 text-left font-semibold text-ink underline-offset-4 hover:underline"
      @click="$emit('click', $event)"
  >
    <slot/>
  </button>
</template>
