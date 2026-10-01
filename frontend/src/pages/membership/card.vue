<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import QrCode from '../../components/QrCode.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import LoadingState from '../../components/LoadingState.vue'
import { useAppStore } from '../../stores/app'

const store = useAppStore()
const seconds = ref(60)
let timer: number | undefined
async function refresh() { try { await store.generateQr(); seconds.value = store.qr?.expiresIn || 60 } catch { /* Store renders feedback. */ } }
onMounted(async () => { await store.bootstrap(); await refresh(); timer = setInterval(() => { seconds.value = Math.max(0, seconds.value - 1); if (seconds.value === 0) refresh() }, 1000) as unknown as number })
onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <view class="app-shell no-tabs member-page">
    <AppHeader title="成员身份" subtitle="动态身份码每 60 秒刷新" backable />
    <view class="member-brand-lockup"><image class="member-brand" src="/static/brand/brand-logo-mark-256.png" mode="aspectFit" /><text>开源乓协成员身份</text></view>
    <LoadingState v-if="store.loading && !store.qr" />
    <template v-else-if="store.qr">
      <view class="member-copy"><text class="member-name">{{ store.me?.realName || store.me?.nickname }}</text><StatusBadge label="正式成员" status="ACTIVE" /><text class="member-number">成员档案已认证</text></view>
      <view class="qr-wrap"><QrCode :value="store.qr.token" /></view>
      <text class="countdown">{{ String(Math.floor(seconds / 60)).padStart(2, '0') }}:{{ String(seconds % 60).padStart(2, '0') }} 后刷新</text>
      <text class="hint">向现场管理人员出示此动态身份码。请勿截图转发，身份码仅在 60 秒内有效。</text>
    </template>
    <view v-if="store.error" class="notice notice-error">{{ store.error }}</view>
  </view>
</template>

<style scoped>
.member-page { text-align: center; }
.member-brand-lockup { display: flex; align-items: center; justify-content: center; gap: .55rem; margin-top: 1rem; color: #52635d; font-size: .8125rem; font-weight: 700; }
.member-copy { display: flex; flex-direction: column; align-items: center; padding: 1.25rem 0 .75rem; }
.member-brand { width: 2rem; height: 2rem; }
.member-name { margin-bottom: .55rem; font-size: 1.25rem; font-weight: 800; }
.member-number { margin-top: .7rem; color: #66706b; font-size: .75rem; }
.qr-wrap { display: flex; justify-content: center; margin: .85rem auto; }
.countdown { display: block; color: #173f35; font-size: .875rem; font-weight: 700; }
.hint { display: block; max-width: 18rem; margin: .75rem auto; color: #66706b; font-size: .75rem; line-height: 1.6; }
</style>
