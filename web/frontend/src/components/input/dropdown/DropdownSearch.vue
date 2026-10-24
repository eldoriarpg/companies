/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {ListboxFilter} from 'reka-ui'

/**
 * The search at the head of a dropdown's list. The focus stays here while the arrow keys move the
 * highlight through the list below, and typing puts the highlight back on the first match.
 */
const search = defineModel<string>({required: true})

defineProps<{
  placeholder?: string
}>()
</script>

<template>
  <div class="border-b border-line-soft p-2">
    <ListboxFilter
        v-model="search"
        :placeholder="placeholder"
        class="min-h-11 w-full border border-line bg-ground px-3 text-sm text-ink outline-none focus:border-accent"
    />
  </div>
</template>
