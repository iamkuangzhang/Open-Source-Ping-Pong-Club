<script setup lang="ts">
import { switchRoot } from '../utils/ui'

type Tab = 'competition' | 'play' | 'community' | 'profile'
defineProps<{ active: Tab }>()

const tabs: Array<{ key: Tab; label: string; path: string }> = [
  { key: 'competition', label: '比赛', path: '/pages/competition/index' },
  { key: 'play', label: '约球', path: '/pages/play/index' },
  { key: 'community', label: '社区', path: '/pages/community/index' },
  { key: 'profile', label: '我的', path: '/pages/profile/index' }
]
const icon = (key: Tab) => `/static/icons/tabs/tab-${key}-96.png`
</script>

<template>
  <view class="tabbar">
    <view class="tabbar-inner">
      <button v-for="tab in tabs" :key="tab.key" :class="['tab', { active: active === tab.key }]" :aria-label="tab.label" @click="switchRoot(tab.path)">
        <view class="tab-icon-wrap"><image class="tab-icon" :src="icon(tab.key)" mode="aspectFit" /></view>
        <text>{{ tab.label }}</text>
      </button>
    </view>
  </view>
</template>

<style scoped lang="scss">
.tabbar { position: fixed; right: 0; bottom: 0; left: 0; z-index: 30; width: 100%; padding-bottom: env(safe-area-inset-bottom); pointer-events: none; }
.tabbar-inner { display: flex; box-sizing: border-box; width: 100%; max-width: 30rem; height: 4.75rem; margin: 0 auto; background: rgba(255,255,255,.98); border-top: 1px solid #dfe4e0; box-shadow: 0 -.35rem 1.2rem rgba(24,32,29,.04); pointer-events: auto; }
.tab { position: relative; display: flex !important; box-sizing: border-box !important; width: 25% !important; min-width: 25% !important; min-height: 4.5rem; flex: 0 0 25% !important; flex-direction: column; align-items: center; justify-content: center; gap: .2rem; margin: 0 !important; padding: 0 !important; color: #707975; background: transparent !important; border: 0 !important; border-radius: 0 !important; box-shadow: none !important; font-size: .75rem; font-weight: 600; line-height: 1.2; }
.tab::after { display: none !important; border: 0 !important; }
.tab.active { color: #173f35; font-weight: 700; }
.tab-icon-wrap { display: flex; width: 2.25rem; height: 1.75rem; align-items: center; justify-content: center; border-radius: .625rem; }
.tab-icon { width: 1.5rem; height: 1.5rem; opacity: .42; filter: grayscale(1); transition: opacity .16s ease; }
.tab.active .tab-icon { opacity: 1; filter: none; }
@media (min-width: 480px) { .tabbar-inner { border-right: 1px solid rgba(23,63,53,.09); border-left: 1px solid rgba(23,63,53,.09); } }
</style>
