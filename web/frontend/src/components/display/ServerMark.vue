/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import type {ServerBranding} from '~/api/gateway'
import {initials} from '~/util/text'

/**
 * The server's icon, square, or its initials on the brand green where it has none or the icon does not load. The
 * server's name is what a screen reader reads.
 */
const props = withDefaults(defineProps<{
  server: ServerBranding
  size?: 'small' | 'large'
}>(), {size: 'small'})

const failed = ref(false)
const showsIcon = computed(() => Boolean(props.server.iconUrl) && !failed.value)
const dimension = computed(() => (props.size === 'large' ? 'size-12 text-base' : 'size-9 text-xs'))
</script>

<template>
  <img
      v-if="showsIcon"
      :src="server.iconUrl"
      :alt="server.name"
      :class="dimension"
      class="shrink-0 border border-line object-cover [image-rendering:pixelated]"
      @error="failed = true"
  />
  <span
      v-else
      role="img"
      :aria-label="server.name"
      :class="dimension"
      class="tabular flex shrink-0 items-center justify-center border border-line bg-brand font-semibold text-white"
  >{{ initials(server.name) }}</span>
</template>
