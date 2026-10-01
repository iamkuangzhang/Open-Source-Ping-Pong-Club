<script setup lang="ts">
import { reactive } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import BottomAction from '../../components/BottomAction.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { useAppStore } from '../../stores/app'

const store = useAppStore()
const form = reactive({ realName: store.me?.realName || '', studentNumber: '', college: store.me?.college || '', major: store.me?.major || '', grade: store.me?.grade || '', phone: '', reason: '' })
async function submit() {
  if (Object.entries(form).some(([key, value]) => key !== 'reason' && !value)) { uni.showToast({ title: '请完整填写申请信息', icon: 'none' }); return }
  try { await store.applyMembership(form) } catch { /* Store renders feedback. */ }
}
</script>

<template>
  <view class="app-shell no-tabs apply-page">
    <AppHeader title="申请加入开源乓协" subtitle="审核通过后成为正式成员" backable />
    <view v-if="store.application" class="application-status"><view><text class="section-title">申请状态</text><text class="muted">审核结果会通过通知告知</text></view><StatusBadge :status="store.application.status" /></view>
    <template v-if="!store.application || store.application.status === 'REJECTED'">
      <view class="form-section"><text class="section-title">基本信息</text><view class="field"><text class="field-label">真实姓名</text><input v-model="form.realName" class="input" /></view><view class="field"><text class="field-label">学号</text><input v-model="form.studentNumber" class="input" /></view><view class="field"><text class="field-label">学院</text><input v-model="form.college" class="input" /></view><view class="grid-two"><view class="field"><text class="field-label">专业</text><input v-model="form.major" class="input" /></view><view class="field"><text class="field-label">年级</text><input v-model="form.grade" class="input" /></view></view><view class="field"><text class="field-label">手机号</text><input v-model="form.phone" class="input" type="number" /></view></view>
      <view class="form-section"><text class="section-title">申请说明</text><view class="field"><textarea v-model="form.reason" class="textarea" placeholder="简单介绍乒乓球经历或加入原因（可选）" /></view></view>
      <view v-if="store.error" class="notice notice-error">{{ store.error }}</view>
      <BottomAction><button class="btn btn-primary btn-block" :disabled="store.loading" @click="submit">提交申请</button></BottomAction>
    </template>
  </view>
</template>

<style scoped>
.apply-page { padding-bottom: 6.5rem; }
.application-status { display: flex; align-items: center; justify-content: space-between; margin: 1rem 0 1.5rem; padding: 1rem; background: #fff; border: 1px solid #e2e7e3; border-radius: .875rem; }
.form-section { margin-top: 1.5rem; }
.form-section > .section-title { margin-bottom: 1rem; }
.field { margin-top: 1rem; }
</style>
