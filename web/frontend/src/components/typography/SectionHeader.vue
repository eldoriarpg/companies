/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
/** The heading of a part of a page, with an optional muted line beside it. */
defineProps<{
  note?: string
}>()
</script>

<template>
  <h2 class="m-0 text-sm font-bold text-ink md:text-base">
    <slot/>
    <span v-if="note" class="ml-2 text-xs font-normal text-ink-muted md:text-sm">{{ note }}</span>
  </h2>
</template>
