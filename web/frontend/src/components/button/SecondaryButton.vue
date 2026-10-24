/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import BaseButton from './BaseButton.vue'

/** Any other action: outlined on the ground colour. */
</script>

<template>
  <BaseButton fill="border-line bg-ground text-ink hover:border-ink-faint">
    <slot/>
  </BaseButton>
</template>
