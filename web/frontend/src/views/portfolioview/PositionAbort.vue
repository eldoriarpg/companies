/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NoticeBanner from '@/components/feedback/NoticeBanner.vue'

/**
 * Gives an order the company works on back to the order book, after asking once more. Items already delivered go back
 * to the members who delivered them, as in game; the plugin checks the permission again.
 */
const props = defineProps<{
  order: number
  name: string
}>()

const {t} = useI18n()
const action = useAction()
const asking = ref(false)
const aborted = ref(false)

/** Aborts the order. */
async function abort(): Promise<void> {
  if (await action.run('orders.accept.abort', {order: props.order})) {
    aborted.value = true
    asking.value = false
  }
}
</script>

<template>
  <div class="flex flex-col gap-2 md:col-span-4">
    <NoticeBanner v-if="aborted">{{ t('positions.aborted', {name}) }}</NoticeBanner>
    <NoticeBanner v-if="action.problem.value !== null" tone="problem">{{ action.problem.value || t('common.actionFailed') }}</NoticeBanner>
    <template v-if="!aborted">
      <div v-if="asking" class="flex flex-col gap-2">
        <span class="text-sm">{{ t('positions.abortQuestion', {name}) }}</span>
        <ButtonRow>
          <PrimaryButton :disabled="action.busy.value" @click="abort">{{ t('positions.abortConfirm') }}</PrimaryButton>
          <SecondaryButton @click="asking = false">{{ t('common.back') }}</SecondaryButton>
        </ButtonRow>
      </div>
      <ButtonRow v-else>
        <SecondaryButton @click="asking = true">{{ t('positions.abort') }}</SecondaryButton>
      </ButtonRow>
    </template>
  </div>
</template>
