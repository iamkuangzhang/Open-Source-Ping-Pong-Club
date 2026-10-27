<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import EmptyState from '../../components/EmptyState.vue'
import LoadingState from '../../components/LoadingState.vue'
import { useAppStore } from '../../stores/app'

const store = useAppStore()
const form = reactive({ code: '', name: '' })

onMounted(async () => {
  try {
    await store.loadSports()
  } catch {
    // The store exposes the load error to the page.
  }
})

async function submit() {
  if (!form.code.trim() || !form.name.trim()) {
    uni.showToast({ title: '请填写类型编码和名称', icon: 'none' })
    return
  }
  try {
    await store.createSport({ code: form.code.trim(), name: form.name.trim() })
    form.code = ''
    form.name = ''
  } catch {
    // The store exposes the API error to the page.
  }
}
</script>

<template>
  <view class="app-shell no-tabs">
    <AppHeader title="运动类型管理" backable />
    <view class="notice notice-info">运动类型可用于创建赛事。其他球类的专属计分和赛制规则尚未实现。</view>

    <view class="section">
      <text class="section-title">当前运动类型</text>
      <LoadingState v-if="store.sportsStatus === 'loading'" text="正在加载运动类型" />
      <view v-else-if="store.sportsStatus === 'error'" class="notice notice-error">暂时无法加载运动类型，请返回后重试。</view>
      <EmptyState v-else-if="!store.sports.length" title="暂无运动类型" />
      <view v-for="sport in store.sports" :key="sport.id" class="sport-row">
        <text class="sport-name">{{ sport.name }}</text>
        <text class="sport-code">{{ sport.code }}</text>
      </view>
    </view>

    <view class="section add-form">
      <text class="section-title">添加运动类型</text>
      <view class="field">
        <text class="field-label">类型编码</text>
        <input v-model="form.code" class="input" placeholder="例如：VOLLEYBALL" maxlength="40" />
        <text class="field-hint">使用英文字母开头，可包含数字和下划线。</text>
      </view>
      <view class="field">
        <text class="field-label">显示名称</text>
        <input v-model="form.name" class="input" placeholder="例如：排球" maxlength="40" />
      </view>
      <view v-if="store.error" class="notice notice-error">{{ store.error }}</view>
      <button class="btn btn-primary btn-block" :disabled="store.loading" @click="submit">
        {{ store.loading ? '添加中…' : '添加运动类型' }}
      </button>
    </view>
  </view>
</template>

<style scoped>
.sport-row { display: flex; min-height: 3.75rem; align-items: center; justify-content: space-between; padding: 0 .875rem; background: #fff; border-bottom: 1px solid #e7eae8; }
.sport-row:first-of-type { margin-top: .625rem; border-radius: .75rem .75rem 0 0; }
.sport-row:last-of-type { border-radius: 0 0 .75rem .75rem; }
.sport-name { color: #18201d; font-size: .9375rem; font-weight: 700; }
.sport-code, .field-hint { color: #66706b; font-size: .75rem; }
.add-form { padding: 1rem; background: #fff; border: 1px solid #e2e7e3; border-radius: .875rem; }
.field { margin-top: .875rem; }
.btn { margin-top: 1rem; }
</style>
