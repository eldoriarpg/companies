/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import BaseBadge from './BaseBadge.vue'

/** A badge saying something went well, such as an order delivered. */
</script>

<template>
  <BaseBadge box-class="border-gain/50 bg-gain/10" text-class="text-gain">
    <slot/>
  </BaseBadge>
</template>
