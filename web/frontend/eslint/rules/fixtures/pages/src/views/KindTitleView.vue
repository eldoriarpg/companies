/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script setup lang="ts">
const {t} = useI18n()
</script>

<template>
  <ViewContent :title="t('pages.order.title')">
    <p>{{ t('pages.order.intro') }}</p>
  </ViewContent>
</template>
