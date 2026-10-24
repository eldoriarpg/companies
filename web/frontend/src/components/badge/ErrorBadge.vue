/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import BaseBadge from './BaseBadge.vue'

/** A badge saying something is wrong. */
</script>

<template>
  <BaseBadge box-class="border-problem-line bg-problem" text-class="text-problem-ink">
    <slot/>
  </BaseBadge>
</template>
