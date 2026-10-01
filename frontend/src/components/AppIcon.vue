<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{ name: string; size?: 'sm' | 'md' | 'lg'; muted?: boolean }>(), { size: 'md', muted: false })
const brandedIcons: Record<string, string> = {
  announcement: '/static/icons/brand/icon-announcement-96.png',
  'member-qr': '/static/icons/brand/icon-member-qr-96.png',
  market: '/static/icons/brand/icon-secondhand-96.png',
  notification: '/static/icons/brand/icon-notification-96.png'
}
const source = computed(() => brandedIcons[props.name] || `/static/icons/${props.name}.svg`)
const branded = computed(() => Boolean(brandedIcons[props.name]))
</script>

<template>
  <image
    :class="['app-icon', size, { muted, branded }]"
    :src="source"
    mode="aspectFit"
  />
</template>

<style scoped>
.app-icon { display: block; flex: 0 0 auto; }
.sm { width: 1rem; height: 1rem; }
.md { width: 1.5rem; height: 1.5rem; }
.lg { width: 1.75rem; height: 1.75rem; }
.muted { opacity: .62; }
.branded { object-fit: contain; }
</style>
