/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {DialogClose, DialogContent, DialogDescription, DialogOverlay, DialogPortal, DialogRoot, DialogTitle} from 'reka-ui'
import IconButton from '@/components/button/IconButton.vue'

/**
 * A sheet rising from the bottom of a phone over the page, for the details of an entry or a menu. It is a modal
 * dialog: focus stays inside, Escape and the close button close it, and so does the browser's back gesture where the
 * caller ties the sheet to the address.
 */
const open = defineModel<boolean>('open', {required: true})

defineProps<{
  title: string
  description?: string
}>()

const {t} = useI18n()
</script>

<template>
  <DialogRoot v-model:open="open">
    <DialogPortal>
      <DialogOverlay class="fixed inset-0 z-40 bg-deep/70"/>
      <DialogContent
          class="fixed inset-x-0 bottom-0 z-50 flex max-h-[92dvh] flex-col gap-4 overflow-y-auto border-t border-line bg-surface px-4 pt-3 pb-[max(1.75rem,env(safe-area-inset-bottom))] outline-none"
      >
        <span class="h-1 w-10 self-center bg-line" aria-hidden="true"/>
        <div class="flex items-start justify-between gap-3">
          <div class="flex min-w-0 flex-col gap-1">
            <DialogTitle class="m-0 text-lg font-bold text-ink">{{ title }}</DialogTitle>
            <DialogDescription v-if="description" class="m-0 text-xs text-ink-muted">{{ description }}</DialogDescription>
          </div>
          <DialogClose as-child>
            <IconButton icon="close" :label="t('common.close')"/>
          </DialogClose>
        </div>
        <slot/>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
