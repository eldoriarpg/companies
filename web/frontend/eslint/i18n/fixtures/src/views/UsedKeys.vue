/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script setup lang="ts">
const {t, tm} = useI18n()
const page = 'one'
const subtree = tm('subtree.group')
const key = `pages.${page}.title`
</script>

<template>
  <PrefixedForm i18n-prefix="storage"/>
  <i18n-t keypath="keypath.text"/>
  <p>{{ t('common.save') }} {{ t('help.title') }} {{ key }} {{ subtree }}</p>
</template>
