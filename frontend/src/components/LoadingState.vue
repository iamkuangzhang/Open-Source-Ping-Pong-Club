<script setup lang="ts">
withDefaults(defineProps<{
  text?: string
  variant?: 'inline' | 'list' | 'ranking'
  rows?: number
}>(), { text: '正在加载', variant: 'inline', rows: 2 })
</script>

<template>
  <view v-if="variant === 'inline'" class="inline-loading" role="status">
    <view class="spinner" /><text>{{ text }}</text>
  </view>
  <view v-else :class="['skeleton-list', variant]" role="status" :aria-label="text">
    <view v-for="index in rows" :key="index" class="skeleton-row">
      <view v-if="variant === 'ranking'" class="skeleton-rank" />
      <view class="skeleton-copy">
        <view class="skeleton-line strong" />
        <view class="skeleton-line weak" />
      </view>
      <view class="skeleton-side" />
    </view>
  </view>
</template>

<style scoped>
.inline-loading { display: flex; min-height: 4.5rem; align-items: center; justify-content: center; gap: .625rem; color: #66706b; font-size: .8125rem; }
.spinner { width: 1rem; height: 1rem; border: 2px solid #dce3df; border-top-color: #173f35; border-radius: 50%; animation: spin .8s linear infinite; }
.skeleton-list { overflow: hidden; background: #fff; border: 1px solid #e2e7e3; border-radius: .875rem; }
.skeleton-row { display: flex; min-height: 5.5rem; align-items: center; gap: .75rem; padding: .875rem 1rem; border-bottom: 1px solid #e7eae8; }
.skeleton-row:last-child { border-bottom: 0; }
.skeleton-copy { min-width: 0; flex: 1; }
.skeleton-line, .skeleton-side, .skeleton-rank { border-radius: .35rem; background: #e8ece9; animation: pulse 1.2s ease-in-out infinite alternate; }
.skeleton-line.strong { width: 68%; height: .75rem; }
.skeleton-line.weak { width: 45%; height: .6rem; margin-top: .7rem; }
.skeleton-side { width: 3rem; height: 1.65rem; }
.skeleton-rank { width: 1.5rem; height: 1.5rem; border-radius: 50%; }
@keyframes spin { to { transform: rotate(360deg); } }
@keyframes pulse { to { opacity: .55; } }
@media (prefers-reduced-motion: reduce) { .spinner, .skeleton-line, .skeleton-side, .skeleton-rank { animation: none; } }
</style>
