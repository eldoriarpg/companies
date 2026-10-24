/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
/**
 * A row of buttons that becomes a column where a phone cannot hold it.
 *
 * <p>Several buttons on one narrow line run out of room, and each one then breaks its own label onto
 * a second line. Below the breakpoint this lays them out as a grid instead, so a button takes the
 * width of its column rather than the width of its words: alone on a line that is the full width,
 * and sharing one they are equal. From the breakpoint up it is the plain flex row it looks like.
 *
 * <p>Whether two short buttons share a mobile line is the caller's to say, because the length of a
 * label is not something this can measure. Stacking is the safe answer and the default.
 */
withDefaults(defineProps<{
  /** Puts two buttons on each mobile line, at equal width. For short labels such as yes and no. */
  pair?: boolean
  /** Where the row sits once it is a row again. */
  align?: 'start' | 'center' | 'end' | 'between'
}>(), {align: 'start'})

const alignments = {
  start: 'sm:justify-start',
  center: 'sm:justify-center',
  end: 'sm:justify-end',
  between: 'sm:justify-between',
} as const
</script>

<template>
  <div
      :class="[pair ? 'grid-cols-2' : 'grid-cols-1', alignments[align]]"
      class="grid gap-2 max-sm:[&>*]:justify-center sm:flex sm:flex-wrap sm:items-center"
  >
    <slot/>
  </div>
</template>
