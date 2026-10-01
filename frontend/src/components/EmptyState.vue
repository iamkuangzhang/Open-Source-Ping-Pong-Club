<script setup lang="ts">
import AppIcon from './AppIcon.vue'

withDefaults(defineProps<{
  title?: string
  description?: string
  action?: string
  icon?: string
  compact?: boolean
}>(), { title: '暂无内容', compact: true })

defineEmits<{ action: [] }>()
</script>

<template>
  <view :class="['state empty-state', { compact }]">
    <AppIcon v-if="icon" :name="icon" muted />
    <view class="state-copy">
      <text class="state-title">{{ title }}</text>
      <text v-if="description" class="state-description">{{ description }}</text>
    </view>
    <button v-if="action" class="state-action" @click="$emit('action')">
      <text>{{ action }}</text><AppIcon name="chevron-right" size="sm" />
    </button>
  </view>
</template>

<style scoped>
.state { display: flex; min-height: 5rem; align-items: center; gap: .75rem; padding: .875rem 1rem; background: #fff; border: 1px solid #e2e7e3; border-radius: .875rem; text-align: left; }
.state.compact { min-height: 4.5rem; }
.state-copy { min-width: 0; flex: 1; }
.state-title { display: block; color: #3f4944; font-size: .875rem; font-weight: 700; line-height: 1.4; }
.state-description { display: block; margin-top: .2rem; color: #66706b; font-size: .8125rem; line-height: 1.45; }
.state-action { display: flex; min-height: 2.75rem; align-items: center; gap: .1rem; padding: 0; color: #173f35; background: transparent !important; font-size: .8125rem; font-weight: 700; white-space: nowrap; }
</style>
