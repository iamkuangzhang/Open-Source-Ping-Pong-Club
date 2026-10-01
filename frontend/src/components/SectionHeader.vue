<script setup lang="ts">
import AppIcon from './AppIcon.vue'
defineProps<{ title: string; subtitle?: string; action?: string }>()
defineEmits<{ action: [] }>()
</script>

<template>
  <view class="section-head">
    <view class="section-copy">
      <text class="section-title">{{ title }}</text>
      <text v-if="subtitle" class="section-subtitle">{{ subtitle }}</text>
    </view>
    <button v-if="action" class="section-action" @click="$emit('action')">
      <text>{{ action }}</text><AppIcon name="chevron-right" size="sm" />
    </button>
  </view>
</template>

<style scoped>
.section-head { display: flex; min-height: 2.75rem; align-items: center; justify-content: space-between; gap: .75rem; }
.section-copy { min-width: 0; flex: 1; }
.section-title { display: block; color: #18201d; font-size: 1.0625rem; font-weight: 700; line-height: 1.35; }
.section-subtitle { display: block; margin-top: .15rem; color: #66706b; font-size: .75rem; line-height: 1.4; }
.section-action { display: flex; min-height: 2.75rem; align-items: center; gap: .1rem; padding: 0; color: #173f35; background: transparent !important; font-size: .8125rem; font-weight: 700; }
</style>
