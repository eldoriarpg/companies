/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
/** How many things wait behind an entry, such as unread messages: a small accent square with the number. */
defineProps<{
  count: number
}>()
</script>

<template>
  <span class="tabular inline-flex h-4 min-w-4 items-center justify-center bg-accent px-1 text-[0.625rem] font-semibold text-accent-contrast">{{ count }}</span>
</template>
