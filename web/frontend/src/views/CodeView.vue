/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {useNow} from '@vueuse/core'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DiscontinuedNotice from '@/components/feedback/DiscontinuedNotice.vue'
import NoticeBanner from '@/components/feedback/NoticeBanner.vue'
import TextField from '@/components/input/TextField.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {CODE_LETTERS, SHORT_ID} from '~/composables/useLogin'
import ServerHeading from './codeview/ServerHeading.vue'

/**
 * The code of a waiting session: which server it is for, the six letters from the chat, the attempts left and, where
 * the gateway asks for it, Cloudflare's challenge. The right code opens the dashboard.
 */
const props = defineProps<{
  shortId: string
}>()

const {t, locale} = useI18n()
const login = useLogin(props.shortId)
const dashboard = useDashboard()
const {show} = useBranding()
const code = ref('')
const challenge = useTemplateRef<HTMLElement>('challenge')
const turnstile = useTurnstile(computed(() => login.pending.value?.turnstileSiteKey), challenge, locale)
const now = useNow({interval: 30_000})

const known = SHORT_ID.test(props.shortId)
const ready = computed(() => CODE_LETTERS.test(code.value) && (!turnstile.needed.value || turnstile.token.value !== null))
const minutesLeft = computed(() => {
  const until = login.pending.value?.expiresAt
  return until ? Math.max(0, Math.round((new Date(until).getTime() - now.value.getTime()) / 60_000)) : 0
})
const title = computed(() => (login.pending.value ? t('login.waitingFor', {server: login.pending.value.server.name}) : t('login.title')))

onMounted(() => {
  if (known) void login.load()
})
watch(login.pending, pending => show(pending?.server ?? null))

/** Sends the code, and opens the dashboard when it is taken. */
async function submit(): Promise<void> {
  if (!ready.value) return
  if (await login.submit(code.value, turnstile.token.value ?? undefined)) {
    dashboard.forget()
    await navigateTo('/app', {replace: true})
  } else {
    turnstile.reset()
  }
}
</script>

<template>
  <ViewContent :title="title" standalone>
    <ServerHeading v-if="login.pending.value" :server="login.pending.value.server"/>
    <DiscontinuedNotice v-if="login.status.value === 'discontinued'" :server-name="login.pending.value?.server.name" :version="login.version.value"/>
    <NoticeBanner v-else-if="!known || login.status.value === 'unknown'" tone="problem">{{ t('login.unknown') }}</NoticeBanner>
    <NoticeBanner v-else-if="login.status.value === 'offline'" tone="problem">{{ t('login.offline') }}</NoticeBanner>
    <p v-else-if="login.status.value === 'loading'" class="m-0 text-sm text-ink-muted" role="status">{{ t('login.loading') }}</p>
    <form v-else class="flex flex-col gap-6" @submit.prevent="submit">
      <MutedText tag="p">{{ t('login.lead') }}</MutedText>
      <TextField
          v-model="code"
          :label="t('login.code')"
          :maxlength="6"
          placeholder="ABCDEF"
          autocomplete="one-time-code"
          :invalid="login.problem.value === 'wrong'"
          :hint="t('login.codeHint', {minutes: minutesLeft, attempts: login.attemptsLeft.value})"
      />
      <div v-if="turnstile.needed.value" ref="challenge" class="min-h-16"/>
      <NoticeBanner v-if="login.problem.value" tone="problem">{{ t(`login.problem.${login.problem.value}`) }}</NoticeBanner>
      <NoticeBanner v-if="turnstile.failed.value" tone="problem">{{ t('login.challengeFailed') }}</NoticeBanner>
      <PrimaryButton type="submit" :disabled="!ready || login.sending.value" full-width>{{ t('login.open') }}</PrimaryButton>
    </form>
  </ViewContent>
</template>
