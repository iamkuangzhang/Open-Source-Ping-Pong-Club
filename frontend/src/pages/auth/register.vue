<script setup lang="ts">
import { reactive } from 'vue'
import { useAppStore } from '../../stores/app'
import AppHeader from '../../components/AppHeader.vue'
import { switchRoot } from '../../utils/ui'

const store = useAppStore()
store.initApi()
const form = reactive({ account: '', nickname: '', password: '', confirm: '' })

async function submit() {
  if (!form.account || !form.nickname || !form.password) {
    uni.showToast({ title: '请完整填写信息', icon: 'none' })
    return
  }
  if (form.password.length < 8) {
    uni.showToast({ title: '密码至少 8 位', icon: 'none' })
    return
  }
  if (form.password !== form.confirm) {
    uni.showToast({ title: '两次密码不一致', icon: 'none' })
    return
  }
  try {
    await store.register(form.account.trim(), form.password, form.nickname.trim())
    switchRoot('/pages/profile/index')
  } catch {
    // Store renders the API error in context.
  }
}
</script>

<template>
  <view class="app-shell no-tabs auth-page">
    <AppHeader title="开源乓协" backable />
    <view class="auth-copy">
      <image class="auth-brand" src="/static/brand/brand-logo-mark-256.png" mode="aspectFit" />
      <view><text class="page-title">注册</text><text class="muted">创建账号，参与比赛与约球</text></view>
    </view>
    <view class="auth-form">
      <view class="field"><text class="field-label">账号</text><input v-model="form.account" class="input" placeholder="3–32 位字母、数字或下划线" /></view>
      <view class="field"><text class="field-label">昵称</text><input v-model="form.nickname" class="input" placeholder="公开展示的昵称" /></view>
      <view class="field"><text class="field-label">密码</text><input v-model="form.password" class="input" password placeholder="至少 8 位" /></view>
      <view class="field"><text class="field-label">确认密码</text><input v-model="form.confirm" class="input" password placeholder="再次输入密码" /></view>
      <view v-if="store.error" class="notice notice-error">{{ store.error }}</view>
      <button class="btn btn-primary btn-block" :disabled="store.loading" @click="submit">{{ store.loading ? '注册中…' : '注册' }}</button>
    </view>
    <text class="legal">注册即表示你知悉：本软件为独立开发项目，并非学校或相关学生组织官方专用软件。</text>
  </view>
</template>

<style scoped>
.auth-page { display: flex; min-height: 100vh; flex-direction: column; background: #fff; }
.auth-copy { display: flex; align-items: center; gap: .875rem; margin-top: 2rem; }
.auth-brand { width: 2.75rem; height: 2.75rem; flex: 0 0 auto; }
.auth-form { margin-top: 1.75rem; }
.legal { margin-top: auto; padding: 2rem 1rem 1rem; color: #7d8681; text-align: center; font-size: .75rem; line-height: 1.6; }
</style>
