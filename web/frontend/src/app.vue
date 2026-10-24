/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script setup lang="ts">
/**
 * The root of the application: the language of the page, and the browser tab named and drawn after the server the
 * page belongs to, once its branding is known.
 */
const {t, locale} = useI18n()
const {branding} = useBranding()

useHead(() => ({
  titleTemplate: (title?: string) => [title, branding.value?.name ?? t('app.name')].filter(Boolean).join(' - '),
  htmlAttrs: {lang: locale.value},
  link: [{key: 'icon', rel: 'icon', href: branding.value?.iconUrl ?? '/icon.svg'}],
}))
</script>

<template>
  <NuxtLayout>
    <NuxtPage/>
  </NuxtLayout>
</template>
