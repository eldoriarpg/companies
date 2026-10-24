/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script setup lang="ts">
import CatalogShell from './CatalogShell.vue'

const {t} = useI18n()
</script>

<template>
  <CatalogShell :heading="t('pages.catalog.title')">
    <p>{{ t('pages.catalog.intro') }}</p>
  </CatalogShell>
</template>
