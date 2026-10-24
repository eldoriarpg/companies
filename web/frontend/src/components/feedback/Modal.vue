/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {computed, ref, type ComponentPublicInstance} from 'vue'
import {DialogContent, DialogOverlay, DialogPortal, DialogRoot} from 'reka-ui'
import IconButton from '@/components/button/IconButton.vue'
import ModalBody from '@/components/feedback/ModalBody.vue'
import {useDialogOverlay} from '@/components/feedback/dialogLayers'

/**
 * The dialog behind every screen that asks something on top of a page.
 *
 * <p>A sheet rising from the bottom suits the details of an entry on a phone; this dialog is for a
 * question or a form that needs the reader's answer before the page goes on. It is a modal dialog in
 * the full sense: Escape and the close button close it, so does a press
 * on the dimmed page around it, the focus is held inside while it is open and goes back to where
 * it came from once it closes, and the page underneath neither scrolls nor reaches a screen reader.
 * The dialog is labelled by the first heading of its content.
 *
 * <p>Which of two open dialogs is in front is decided by the order they were opened in, through
 * the layers in `dialogLayers`, since the order they were mounted in says nothing about it.
 *
 * <p>Shift and enter answer the dialog from anywhere inside it, including from a text field. The
 * button it is answered with is the one marked `data-confirm`, and otherwise the last enabled
 * button that is not marked `data-cancel`: every footer reads cancel first and then the thing being
 * confirmed. The dialog opens with the focus on the field marked `data-autofocus`, and otherwise on
 * that button, so enter alone answers it.
 */
defineOptions({inheritAttrs: false})

const {t} = useI18n()

const model = defineModel<boolean>({default: false})

const props = withDefaults(defineProps<{
  size?: 'sm' | 'md' | 'lg' | 'xl' | '2xl' | 'full'
  mobileFull?: boolean
  /**
   * Takes the dialog off the screen without closing it, so that what is behind it can be seen or
   * photographed and the half-filled form inside is still there afterwards. Closing it would take
   * the form with it, and unmounting would take whatever it is in the middle of doing.
   */
  hidden?: boolean
}>(), {
  size: 'md',
  mobileFull: false,
  hidden: false,
})

const sizeClass = computed(() => {
  switch (props.size) {
    case 'sm': return 'max-w-md'
    case 'lg': return 'max-w-2xl'
    case 'xl': return 'max-w-5xl'
    case '2xl': return 'max-w-7xl'
    case 'full': return 'max-w-[95vw]'
    case 'md':
    default: return 'max-w-lg'
  }
})

const dialog = ref<ComponentPublicInstance | null>(null)
const {overlay, layer, keepOpenUnlessOverlay} = useDialogOverlay(model)

/**
 * The button this dialog is answered with.
 *
 * <p>Named outright where a dialog marks it, and otherwise the last button in it that is not a way
 * out. Every footer here reads the same way round, cancel and then the thing being confirmed, so
 * the last one is it. Guessing rather than requiring the mark is what makes the rule true in every
 * dialog on the first day instead of in the ones somebody remembered to go back to.
 */
function confirmButton(): HTMLElement | null {
  const root: HTMLElement | undefined = dialog.value?.$el
  if (!root) return null
  const named = root.querySelector<HTMLElement>('[data-confirm]')
  if (named) return named
  const answers = [...root.querySelectorAll<HTMLButtonElement>('button')]
      .filter(button => !button.disabled && !button.hasAttribute('data-cancel'))
  return answers.at(-1) ?? null
}

/**
 * The dialog opens with the focus on the field it marks with {@code data-autofocus}, so typing starts
 * there, and otherwise on the button it is answered with, so enter alone answers it.
 */
function focusAnswer(event: Event) {
  const root: HTMLElement | undefined = dialog.value?.$el
  const target: HTMLElement | null | undefined =
      root?.querySelector<HTMLElement>('[data-autofocus]') ?? confirmButton() ?? root
  if (!target) return
  event.preventDefault()
  target.focus()
}

/**
 * Shift and enter answer the dialog from anywhere inside it, including from a text field, where
 * the enter key belongs to the field itself.
 */
function onKeydown(e: KeyboardEvent) {
  if (e.key !== 'Enter' || !e.shiftKey) return
  const target = confirmButton()
  if (!target) return
  e.preventDefault()
  target.click()
}
</script>

<template>
  <DialogRoot v-model:open="model">
    <DialogPortal>
      <DialogOverlay
          ref="overlay"
          class="fixed inset-0 flex items-center justify-center bg-deep/70 data-[state=open]:animate-fade-in data-[state=closed]:animate-fade-out"
          :class="{'invisible opacity-0': props.hidden}"
          :style="{zIndex: layer}"
      >
        <DialogContent
            ref="dialog"
            data-testid="modal"
            :aria-describedby="undefined"
            :class="[
              'data-[state=open]:animate-fade-in data-[state=closed]:animate-fade-out relative w-full mx-4 border border-line bg-surface p-5',
              'flex flex-col max-h-[90dvh] outline-none',
              sizeClass,
              props.mobileFull ? 'max-sm:h-full max-sm:max-h-dvh max-sm:mx-0 max-sm:border-0' : '',
            ]"
            @keydown="onKeydown"
            @open-auto-focus="focusAnswer"
            @pointer-down-outside="keepOpenUnlessOverlay"
        >
          <IconButton
              icon="close"
              :label="t('common.close')"
              class="absolute top-2 right-2 z-10"
              data-cancel
              @click="model = false"
          />
          <ModalBody>
            <slot/>
          </ModalBody>
        </DialogContent>
      </DialogOverlay>
    </DialogPortal>
  </DialogRoot>
</template>
