<script setup lang="ts">
import AppIcon from './AppIcon.vue'

withDefaults(defineProps<{
  title?: string
  description?: string
  retry?: boolean | string
  compact?: boolean
}>(), {
  title: '暂时无法加载',
  description: '请稍后重试',
  retry: true,
  compact: true
})

defineEmits<{ retry: [] }>()
</script>

<template>
  <view :class="['state error-state', { compact }]">
    <view class="state-copy">
      <text class="state-title">{{ title }}</text>
      <text v-if="description" class="state-description">{{ description }}</text>
    </view>
    <button v-if="retry" class="state-action" @click="$emit('retry')">
      <text>{{ typeof retry === 'string' ? retry : '重试' }}</text>
      <AppIcon name="chevron-right" size="sm" />
    </button>
  </view>
</template>

<style scoped>
.state { display: flex; min-height: 5rem; align-items: center; gap: .75rem; padding: .875rem 1rem; background: #fff; border: 1px solid #eadfdd; border-left: 3px solid #c64d47; border-radius: .875rem; text-align: left; }
.state.compact { min-height: 4.5rem; }
.state-copy { min-width: 0; flex: 1; }
.state-title { display: block; color: #252d29; font-size: .875rem; font-weight: 700; line-height: 1.4; }
.state-description { display: block; margin-top: .2rem; color: #66706b; font-size: .8125rem; line-height: 1.45; }
.state-action { display: flex; min-width: 3.5rem; min-height: 2.75rem; align-items: center; justify-content: flex-end; gap: .1rem; margin: 0; padding: 0; color: #173f35; background: transparent !important; font-size: .8125rem; font-weight: 700; white-space: nowrap; }
</style>
