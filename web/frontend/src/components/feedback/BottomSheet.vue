/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {DialogClose, DialogContent, DialogDescription, DialogOverlay, DialogPortal, DialogRoot, DialogTitle} from 'reka-ui'
import IconButton from '@/components/button/IconButton.vue'
import {useDialogOverlay} from './dialogLayers'

/**
 * A sheet rising from the bottom of a phone over the page, for the details of an entry or a menu. It is a modal
 * dialog: focus stays inside, Escape and the close button close it, and so does the browser's back gesture where the
 * caller ties the sheet to the address. It takes its layer in opening order with the dialogs, so whichever was
 * opened last is in front, and only a press on the dimmed page closes it, so a panel opened from inside the sheet
 * keeps it open.
 */
const open = defineModel<boolean>('open', {required: true})

defineProps<{
  title: string
  description?: string
}>()

const {t} = useI18n()
const {overlay, layer, keepOpenUnlessOverlay} = useDialogOverlay(open)
</script>

<template>
  <DialogRoot v-model:open="open">
    <DialogPortal>
      <DialogOverlay ref="overlay" class="fixed inset-0 bg-deep/70" :style="{zIndex: layer}"/>
      <DialogContent
          :style="{zIndex: layer}"
          class="fixed inset-x-0 bottom-0 flex max-h-[92dvh] flex-col gap-4 overflow-y-auto border-t border-line bg-surface px-4 pt-3 pb-[max(1.75rem,env(safe-area-inset-bottom))] outline-none"
          @pointer-down-outside="keepOpenUnlessOverlay"
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
