/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import BaseBadge from './BaseBadge.vue'

/** A badge saying something wants attention soon, such as an order about to expire. */
</script>

<template>
  <BaseBadge box-class="border-notice-line bg-notice" text-class="text-notice-ink">
    <slot/>
  </BaseBadge>
</template>
