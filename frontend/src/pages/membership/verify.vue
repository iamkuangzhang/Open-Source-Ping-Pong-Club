<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import AppIcon from '../../components/AppIcon.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { api } from '../../services/api'

const token = ref('')
const result = ref<{ name: string; memberNumber: string; role: string; status: string } | null>(null)
const error = ref('')
async function verify() { error.value = ''; try { result.value = await api.get(`/public/member-verifications/${encodeURIComponent(token.value)}`) } catch { result.value = null; error.value = '身份码无效或已过期' } }
onLoad((query) => { token.value = String(query?.token || ''); if (token.value) verify() })
</script>

<template>
  <view class="app-shell no-tabs verify-page">
    <AppHeader title="身份验证" backable />
    <view v-if="!result" class="verify-form"><text class="section-title">验证成员身份</text><text class="muted">扫描动态二维码后会自动验证，也可以粘贴身份码。</text><textarea v-model="token" class="textarea" placeholder="请输入身份码" /><view v-if="error" class="notice notice-error">{{ error }}</view><button class="btn btn-primary btn-block" @click="verify">验证</button></view>
    <view v-else class="success-result"><view class="verified-icon"><AppIcon name="member-qr" size="lg" /></view><text class="page-title">身份验证成功</text><text class="name">{{ result.name }}</text><StatusBadge label="正式成员" status="ACTIVE" /><text class="member-no">{{ result.memberNumber }}</text></view>
    <text class="legal">本软件为独立开发项目，并非学校相关学生社团官方专用软件。</text>
  </view>
</template>

<style scoped>
.verify-page { display: flex; min-height: 100vh; flex-direction: column; }
.verify-form { padding-top: 2rem; }
.verify-form .textarea { margin: 1.25rem 0; }
.success-result { display: flex; flex: 1; flex-direction: column; align-items: center; justify-content: center; }
.verified-icon { display: flex; width: 3.5rem; height: 3.5rem; align-items: center; justify-content: center; margin-bottom: 1.25rem; border-radius: 50%; color: #173f35; background: #eef3f0; }
.name { margin: 1.5rem 0 .6rem; font-size: 1.125rem; font-weight: 700; }
.member-no { margin-top: .8rem; color: #66706b; font-size: .75rem; }
.legal { margin-top: auto; padding: 2rem 1rem 1rem; color: #7d8681; text-align: center; font-size: .75rem; line-height: 1.6; }
</style>
