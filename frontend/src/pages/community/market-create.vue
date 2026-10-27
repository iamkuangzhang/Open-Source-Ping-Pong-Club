<script setup lang="ts">
import { reactive } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import BottomAction from '../../components/BottomAction.vue'
import { useAppStore } from '../../stores/app'
import { back } from '../../utils/ui'

const store = useAppStore()
const form = reactive({
  title: '',
  price: '',
  condition: '九成新',
  content: '',
  deliveryMethod: '',
  contact: '',
  rulesAccepted: false
})

async function submit() {
  if (!form.title || !form.content || !form.deliveryMethod || !form.contact) {
    uni.showToast({ title: '请完整填写物品信息', icon: 'none' })
    return
  }
  if (!form.rulesAccepted) {
    uni.showToast({ title: '请先确认二手交易规则', icon: 'none' })
    return
  }
  try {
    await store.createMarketPost({
      title: form.title,
      content: form.content,
      visibility: 'PUBLIC',
      price: form.price || '面议',
      contact: form.contact,
      marketplaceCondition: form.condition,
      deliveryMethod: form.deliveryMethod,
      rulesAccepted: form.rulesAccepted
    })
    back()
  } catch {
    // Store renders the API error in context.
  }
}
</script>

<template>
  <view class="app-shell no-tabs form-page">
    <AppHeader title="发布二手" backable />
    <view class="image-placeholder"><text>图片上传</text><text class="muted">当前 API 未提供可用文件地址时使用统一占位</text></view>
    <view class="field"><text class="field-label">标题</text><input v-model="form.title" class="input" placeholder="品牌、型号或物品名称" /></view>
    <view class="grid-two">
      <view class="field"><text class="field-label">价格</text><input v-model="form.price" class="input" placeholder="面议" /></view>
      <view class="field"><text class="field-label">成色</text><input v-model="form.condition" class="input" placeholder="请如实填写" /></view>
    </view>
    <view class="field"><text class="field-label">物品说明</text><textarea v-model="form.content" class="textarea" placeholder="请说明型号、使用情况和已知瑕疵" /></view>
    <view class="field"><text class="field-label">交付方式</text><input v-model="form.deliveryMethod" class="input" placeholder="例如：当面验货交付" /></view>
    <view class="field"><text class="field-label">联系方式</text><input v-model="form.contact" class="input" placeholder="请勿填写验证码、密码等敏感信息" /></view>
    <view class="trade-rules">
      <text class="rules-title">二手交易须知</text>
      <text class="rules-copy">开源乓协仅提供信息展示与交流，不提供支付、资金托管、退款或交易担保。请当面验货，勿提前转账；不要公开验证码、密码、证件或银行卡信息。发现疑似诈骗或违规信息可在详情页举报。</text>
      <view class="rules-confirm" @click="form.rulesAccepted = !form.rulesAccepted">
        <checkbox :checked="form.rulesAccepted" color="#173f35" />
        <text>我已阅读并同意按真实情况发布，且理解平台不提供交易担保</text>
      </view>
    </view>
    <view v-if="store.error" class="notice notice-error">{{ store.error }}</view>
    <BottomAction><button class="btn btn-primary btn-block" @click="submit">发布信息</button></BottomAction>
  </view>
</template>

<style scoped>
.form-page { padding-bottom: 6.5rem; }
.field { margin-top: 1rem; }
.image-placeholder { display: flex; min-height: 8rem; flex-direction: column; align-items: center; justify-content: center; margin-top: 1rem; color: #5f6864; background: #e9ece9; border-radius: .875rem; font-size: .875rem; font-weight: 700; }
.trade-rules { margin-top: 1.25rem; padding: 1rem; background: #eef3f0; border-radius: .75rem; }
.rules-title, .rules-copy { display: block; }
.rules-title { font-weight: 700; }
.rules-copy { margin-top: .5rem; color: #59645f; font-size: .8rem; line-height: 1.6; }
.rules-confirm { display: flex; align-items: center; gap: .35rem; margin-top: .75rem; font-size: .8rem; line-height: 1.5; }
</style>
