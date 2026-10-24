/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import AppFooter from '@/components/layout/AppFooter.vue'

/** The frame of the pages before the dashboard opens: one narrow column in the middle, clear of the phone's edges. */
</script>

<template>
  <div class="px-safe pt-safe pb-safe flex min-h-dvh flex-col">
    <main class="mx-auto flex w-full max-w-md grow flex-col justify-center py-8">
      <slot/>
    </main>
    <div class="mx-auto w-full max-w-md pb-4">
      <AppFooter/>
    </div>
  </div>
</template>
