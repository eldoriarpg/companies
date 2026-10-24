/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script setup lang="ts">
defineProps<{
  heading: string
}>()
</script>

<template>
  <ViewContent :title="heading">
    <slot/>
  </ViewContent>
</template>
