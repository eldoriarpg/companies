/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import BaseBadge from './BaseBadge.vue'

/** A quiet badge for a plain fact about an entry. */
</script>

<template>
  <BaseBadge box-class="border-line bg-ground" text-class="text-ink-muted">
    <slot/>
  </BaseBadge>
</template>
