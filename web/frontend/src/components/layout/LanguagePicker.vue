/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import SelectInput from '@/components/input/SelectInput.vue'

/** Picks the dashboard's language, each named in its own language; the browser remembers the choice. */
const {t} = useI18n()
const {choices, picked} = useLanguage()
</script>

<template>
  <SelectInput v-model="picked" :label="t('footer.language')" :options="choices" hide-label/>
</template>
