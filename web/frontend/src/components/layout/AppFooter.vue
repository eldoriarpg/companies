/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import LanguagePicker from './LanguagePicker.vue'

/** The small print below every page: the Companies mark, kept small since the page is the server's, and the language. */
const {t} = useI18n()
</script>

<template>
  <footer class="flex flex-wrap items-center justify-between gap-3 border-t border-line-soft pt-4 text-xs text-ink-muted">
    <span class="tabular uppercase tracking-widest">{{ t('footer.mark') }}</span>
    <LanguagePicker/>
  </footer>
</template>
