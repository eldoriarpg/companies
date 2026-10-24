/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script setup lang="ts">
const {t} = useI18n()
const order = ref<{name: string} | null>(null)
</script>

<template>
  <ViewContent :title="order?.name || t('pages.order.title')">
    <p>{{ t('pages.order.intro') }}</p>
  </ViewContent>
</template>
