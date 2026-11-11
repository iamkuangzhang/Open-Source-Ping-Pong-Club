<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import BottomAction from '../../components/BottomAction.vue'
import LoadingState from '../../components/LoadingState.vue'
import ErrorState from '../../components/ErrorState.vue'
import { FORUM_CATEGORIES, useForumStore } from '../../stores/forum'
import { useAppStore } from '../../stores/app'
import { back, go } from '../../utils/ui'

const forum = useForumStore()
const store = useAppStore()
const editId = ref('')
const loading = ref(false)
const loadFailed = ref(false)
const form = reactive({ category: FORUM_CATEGORIES[0], title: '', content: '' })

const isEdit = computed(() => Boolean(editId.value))
const canSubmit = computed(() => form.title.trim().length > 0 && form.content.trim().length > 0 && !forum.submitting)
const titleHint = computed(() => `${form.title.trim().length} / ${forum.titleLengthLimit}`)
const contentHint = computed(() => `${form.content.trim().length} / ${forum.contentLengthLimit}`)

async function loadExisting() {
  loading.value = true
  loadFailed.value = false
  try {
    const detail = await forum.loadPost(editId.value)
    if (detail) {
      form.category = detail.category || FORUM_CATEGORIES[0]
      form.title = detail.title
      form.content = detail.content
    }
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

onLoad((query) => {
  editId.value = String(query?.id || '')
  if (!store.signedIn) go('/pages/auth/login')
  forum.clearFeedback()
  if (editId.value) loadExisting()
})

async function submit() {
  if (!form.title.trim()) {
    uni.showToast({ title: '请填写标题', icon: 'none' })
    return
  }
  if (!form.content.trim()) {
    uni.showToast({ title: '请填写正文', icon: 'none' })
    return
  }
  try {
    if (isEdit.value) {
      await forum.updatePost(editId.value, { ...form })
    } else {
      await forum.createPost({ ...form })
    }
    back()
  } catch { /* 失败原因通过 error 提示块展示 */ }
}
</script>

<template>
  <view class="app-shell no-tabs compose-page">
    <AppHeader :title="isEdit ? '编辑帖子' : '发布帖子'" :subtitle="isEdit ? '仅作者本人可修改内容' : '发帖后可在详情页继续回帖'" backable />

    <LoadingState v-if="loading" variant="list" text="正在加载帖子" :rows="1" />
    <ErrorState v-else-if="loadFailed" title="暂时无法加载原帖" description="帖子可能已被删除或隐藏" @retry="loadExisting" />

    <template v-else>
      <view class="field">
        <text class="field-label">所属板块</text>
        <view class="segment category-segment" role="tablist">
          <button v-for="item in FORUM_CATEGORIES" :key="item" :class="['segment-item', { active: form.category === item }]" @click="form.category = item">{{ item }}</button>
        </view>
        <text class="form-hint">板块提交后按名称精确归类，请选择最贴近内容的一项。</text>
      </view>

      <view class="field">
        <view class="field-line"><text class="field-label">标题</text><text class="counter">{{ titleHint }}</text></view>
        <input v-model="form.title" class="input" maxlength="160" placeholder="一句话说明你想讨论的内容" />
      </view>

      <view class="field">
        <view class="field-line"><text class="field-label">正文</text><text class="counter">{{ contentHint }}</text></view>
        <textarea v-model="form.content" class="textarea body-input" :maxlength="5000" placeholder="补充背景、时间、联系方式或你的观点" />
      </view>

      <view v-if="!store.signedIn" class="notice notice-error">登录状态已失效，请先返回登录后再发帖。</view>
      <view v-if="forum.error" class="notice notice-error">{{ forum.error }}</view>

      <BottomAction>
        <button class="btn btn-primary btn-block" :disabled="!canSubmit" @click="submit">{{ forum.submitting ? '正在提交' : isEdit ? '保存修改' : '发布帖子' }}</button>
      </BottomAction>
    </template>
  </view>
</template>

<style scoped lang="scss">
.compose-page { padding-bottom: 6.5rem; }
.field { margin-top: 1rem; }
.field-line { display: flex; align-items: baseline; justify-content: space-between; gap: .75rem; }
.counter { color: #87908b; font-size: .75rem; }
.category-segment { border-bottom: 1px solid #e2e7e3; }
.body-input { min-height: 12rem; }
</style>
