/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import BaseBadge from './BaseBadge.vue'

/** A badge in the accent colour, for what stands out of a list. */
</script>

<template>
  <BaseBadge box-class="border-accent/60 bg-accent/15" text-class="text-accent">
    <slot/>
  </BaseBadge>
</template>
