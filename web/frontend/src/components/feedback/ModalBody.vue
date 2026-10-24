/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {injectDialogRootContext} from 'reka-ui'

/**
 * The scrolling body of a dialog, which also names the dialog after its own heading.
 *
 * <p>A dialog's title is the first heading its content brings along, since every dialog here opens
 * with one. That heading is what the dialog is labelled by, so a screen reader announces it on
 * open. A heading without an id of its own takes the one the dialog already points at; a heading
 * that has one is pointed at instead, and a dialog without any heading is left unlabelled rather
 * than pointing at nothing.
 *
 * <p>The link is made as the content mounts, which is before the dialog itself finishes mounting,
 * so the dialog finds its title in place the moment it looks for one.
 */
const rootContext = injectDialogRootContext()
const body = ref<HTMLElement | null>(null)

function linkTitle(root: HTMLElement) {
  const dialog = root.closest<HTMLElement>('[role="dialog"]')
  const heading = root.querySelector<HTMLElement>('h1, h2, h3, h4, h5, h6')
  if (!dialog) return
  if (!heading) {
    dialog.removeAttribute('aria-labelledby')
    return
  }
  if (!heading.id) heading.id = rootContext.titleId
  dialog.setAttribute('aria-labelledby', heading.id)
}

onMounted(() => {
  if (body.value) linkTitle(body.value)
})
</script>

<template>
  <div ref="body" class="flex-1 min-h-0 overflow-y-auto">
    <slot/>
  </div>
</template>
