<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import { back } from '../utils/ui'

withDefaults(defineProps<{
  title: string
  subtitle?: string
  backable?: boolean
  action?: string
  actionIcon?: string
  actionLabel?: string
  branded?: boolean
}>(), { backable: false, branded: false })

defineEmits<{ action: [] }>()
</script>

<template>
  <view :class="['app-header', { branded, backable }]">
    <button v-if="backable" class="header-icon-button header-back" aria-label="返回" @click="back">
      <AppIcon name="back" />
    </button>
    <view v-if="branded" class="brand-seal" aria-hidden="true">
      <image class="brand-logo" src="/static/brand/brand-logo-mark-256.png" mode="aspectFit" />
    </view>
    <view class="header-copy">
      <text v-if="branded" class="header-brand">开源乓协</text>
      <text class="header-title">{{ title }}</text>
      <text v-if="subtitle" class="header-subtitle">{{ subtitle }}</text>
    </view>
    <button
      v-if="action || actionIcon"
      class="header-icon-button header-action"
      :aria-label="actionLabel || action || '页面操作'"
      @click="$emit('action')"
    >
      <AppIcon v-if="actionIcon" :name="actionIcon" />
      <text v-else class="header-action-text">{{ action }}</text>
    </button>
    <view v-else-if="backable" class="header-spacer" />
  </view>
</template>

<style scoped lang="scss">
.app-header {
  display: flex;
  min-height: 4.75rem;
  align-items: center;
  gap: .75rem;
  padding: calc(.5rem + env(safe-area-inset-top)) 0 .55rem;
}
.app-header.branded { min-height: 6rem; padding-top: calc(.75rem + env(safe-area-inset-top)); }
.brand-seal {
  display: flex;
  width: 2.125rem;
  height: 2.125rem;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: .6875rem;
  background: #eef3f0;
}
.brand-logo { width: 1.75rem; height: 1.75rem; }
.header-copy { min-width: 0; flex: 1; }
.header-brand { display: block; margin-bottom: .25rem; color: #66706b; font-size: .75rem; font-weight: 700; letter-spacing: .08em; }
.header-title { display: block; color: #18201d; font-size: 1.375rem; font-weight: 800; line-height: 1.2; letter-spacing: -.02em; }
.header-subtitle { display: block; margin-top: .25rem; color: #66706b; font-size: .8125rem; line-height: 1.45; }
.header-icon-button, .header-spacer { width: 2.75rem; min-width: 2.75rem; height: 2.75rem; flex: 0 0 auto; padding: 0; background: transparent !important; border-radius: .75rem; }
.header-icon-button { display: flex; align-items: center; justify-content: center; }
.header-icon-button:active { background: #e8eeea !important; }
.header-back { margin-left: -.55rem; }
.header-action { margin-right: -.55rem; }
.header-action-text { color: #173f35; font-size: .875rem; font-weight: 700; }
</style>
