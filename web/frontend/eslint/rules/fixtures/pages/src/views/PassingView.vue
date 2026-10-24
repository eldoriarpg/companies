/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script setup lang="ts">
import OrderShell from './OrderShell.vue'

const {t} = useI18n()
</script>

<template>
  <OrderShell :heading="t('pages.order.title')">
    <p>{{ t('pages.order.intro') }}</p>
  </OrderShell>
</template>
