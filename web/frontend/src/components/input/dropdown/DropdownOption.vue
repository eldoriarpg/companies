/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {ListboxItem, type AcceptableValue} from 'reka-ui'

/**
 * One entry of a dropdown's list. It is highlighted as the keyboard or the pointer reaches it and
 * marked `data-state="checked"` while it is chosen, which is what a list styles its choice by.
 */
defineProps<{
  value: AcceptableValue
  disabled?: boolean
}>()

/**
 * The entry was taken. Preventing the event's default keeps the list's own choice as it is, for an
 * entry that is an action rather than a value.
 */
const emit = defineEmits<{
  select: [event: Event]
}>()
</script>

<template>
  <ListboxItem
      :value="value"
      :disabled="disabled"
      class="flex min-h-11 w-full cursor-pointer items-center gap-2 px-3 text-left text-sm outline-none transition-colors data-[highlighted]:bg-selected data-[state=checked]:text-accent data-[disabled]:cursor-not-allowed data-[disabled]:opacity-50"
      @select="emit('select', $event)"
  >
    <slot/>
  </ListboxItem>
</template>
