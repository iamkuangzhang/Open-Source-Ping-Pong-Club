<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(defineProps<{ name?: string; src?: string; size?: 'sm' | 'md' | 'lg'; placeholder?: boolean }>(), { name: '球友', size: 'md', placeholder: false })
const imageFailed = ref(false)
const resolvedSrc = computed(() => props.src?.trim() && !imageFailed.value ? props.src.trim() : '/static/avatar/default-avatar-256.png')
const isDefault = computed(() => !props.src?.trim() || imageFailed.value)

watch(() => props.src, () => { imageFailed.value = false })
</script>

<template>
  <view :class="['avatar', size, { placeholder, default: isDefault }]" :aria-label="`${name || '用户'}头像`">
    <image :class="['avatar-image', { 'default-image': isDefault }]" :src="resolvedSrc" :mode="isDefault ? 'aspectFit' : 'aspectFill'" @error="imageFailed = true" />
  </view>
</template>

<style scoped>
.avatar { display: flex; flex: 0 0 auto; align-items: center; justify-content: center; overflow: hidden; border-radius: 50%; background: #eef3f0; }
.avatar.default { background: #edf5ff; border: 1px solid #dbe7f5; }
.avatar-image { width: 100%; height: 100%; border-radius: 50%; }
.default-image { box-sizing: border-box; padding: 3%; }
.sm { width: 2rem; height: 2rem; font-size: .75rem; }
.md { width: 2.75rem; height: 2.75rem; font-size: .9rem; }
.lg { width: 4rem; height: 4rem; font-size: 1.25rem; }
</style>
