/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextField from '@/components/input/TextField.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {SHORT_ID} from '~/composables/useLogin'

/**
 * The plain address without a short id. A browser that still has a session goes straight to the dashboard; any other
 * is asked for the short id the chat showed, which leads to the code.
 */
const {t} = useI18n()
const dashboard = useDashboard()
const shortId = ref('')
const valid = computed(() => SHORT_ID.test(shortId.value))

onMounted(() => dashboard.start())
watch(dashboard.status, status => {
  if (status === 'ready' || status === 'discontinued') void navigateTo('/app', {replace: true})
}, {immediate: true})

/** Goes on to the code of the session. */
async function proceed(): Promise<void> {
  if (valid.value) await navigateTo(`/${shortId.value}`)
}
</script>

<template>
  <ViewContent :title="t('login.shortIdTitle')" standalone>
    <MutedText tag="p">{{ t('login.shortIdLead') }}</MutedText>
    <form class="flex flex-col gap-6" @submit.prevent="proceed">
      <TextField v-model="shortId" :label="t('login.shortId')" :maxlength="6" placeholder="A1B2C3" :hint="t('login.shortIdHint')"/>
      <PrimaryButton type="submit" :disabled="!valid" full-width>{{ t('login.continue') }}</PrimaryButton>
    </form>
  </ViewContent>
</template>
