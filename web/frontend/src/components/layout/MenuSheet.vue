/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import AppIcon from '@/components/display/AppIcon.vue'
import BottomSheet from '@/components/feedback/BottomSheet.vue'
import type {Section} from '~/util/navigation'
import LanguagePicker from './LanguagePicker.vue'

/** The phone's menu: the sections the tab bar has no room for, the language and logging out. */
const open = defineModel<boolean>('open', {required: true})

const props = defineProps<{
  sections: readonly Section[]
}>()

defineEmits<{
  logout: []
}>()

const {t} = useI18n()
const extra = computed(() => props.sections.filter(section => !section.tab))
</script>

<template>
  <BottomSheet v-model:open="open" :title="t('nav.menu')">
    <nav v-if="extra.length > 0" :aria-label="t('nav.more')">
      <NuxtLink
          v-for="section in extra"
          :key="section.id"
          :to="section.path"
          class="flex min-h-11 items-center gap-3 border-b border-line-soft text-sm font-semibold text-ink no-underline"
          @click="open = false"
      >
        <AppIcon :name="section.icon" :size="18"/>
        {{ t(`nav.${section.id}`) }}
      </NuxtLink>
    </nav>
    <LanguagePicker/>
    <SecondaryButton icon="logout" full-width @click="$emit('logout')">{{ t('nav.logout') }}</SecondaryButton>
  </BottomSheet>
</template>
