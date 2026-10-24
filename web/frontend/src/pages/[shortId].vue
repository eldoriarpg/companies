/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script setup lang="ts">
import CodeView from '~/views/CodeView.vue'

const route = useRoute()

definePageMeta({
  name: 'code',
})
</script>

<template>
  <CodeView :short-id="String(route.params.shortId)"/>
</template>
