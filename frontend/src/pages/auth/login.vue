<script setup lang="ts">
import { reactive } from 'vue'
import { useAppStore } from '../../stores/app'
import AppHeader from '../../components/AppHeader.vue'
import { go, switchRoot } from '../../utils/ui'

const store = useAppStore()
store.initApi()
const form = reactive({ account: '', password: '' })

async function submit() {
  if (!form.account || !form.password) {
    uni.showToast({ title: '请填写账号和密码', icon: 'none' })
    return
  }
  try {
    await store.login(form.account.trim(), form.password)
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
      <view><text class="page-title">登录</text><text class="muted">使用你的账号继续</text></view>
    </view>
    <view class="auth-form">
      <view class="field">
        <text class="field-label">账号</text>
        <input v-model="form.account" class="input" placeholder="请输入账号" autocomplete="username" />
      </view>
      <view class="field">
        <text class="field-label">密码</text>
        <input v-model="form.password" class="input" password placeholder="请输入密码" autocomplete="current-password" />
      </view>
      <view v-if="store.error" class="notice notice-error">{{ store.error }}</view>
      <button class="btn btn-primary btn-block" :disabled="store.loading" @click="submit">
        {{ store.loading ? '登录中…' : '登录' }}
      </button>
      <view class="auth-link">
        <text>还没有账号？</text>
        <text class="link" @click="go('/pages/auth/register')">注册</text>
      </view>
    </view>
    <text class="legal">本软件为独立开发项目，并非学校或相关学生组织官方专用软件。</text>
  </view>
</template>

<style scoped>
.auth-page { display: flex; min-height: 100vh; flex-direction: column; background: #fff; }
.auth-copy { display: flex; align-items: center; gap: .875rem; margin-top: 3rem; }
.auth-brand { width: 2.75rem; height: 2.75rem; flex: 0 0 auto; }
.auth-form { margin-top: 2rem; }
.auth-link { display: flex; justify-content: center; gap: .4rem; margin-top: 1.2rem; color: #66706b; font-size: .875rem; }
.legal { margin-top: auto; padding: 2rem 1rem 1rem; color: #7d8681; text-align: center; font-size: .75rem; line-height: 1.6; }
</style>
