/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import BaseBadge from './BaseBadge.vue'

/** A badge saying how something stands, neither good nor bad. */
</script>

<template>
  <BaseBadge box-class="border-progress/50 bg-progress/10" text-class="text-progress">
    <slot/>
  </BaseBadge>
</template>
