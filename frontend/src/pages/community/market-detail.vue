<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import AppIcon from '../../components/AppIcon.vue'
import BottomAction from '../../components/BottomAction.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import LoadingState from '../../components/LoadingState.vue'
import { api } from '../../services/api'
import { useAppStore, type Post } from '../../stores/app'

const store = useAppStore()
const item = ref<Post | null>(null)
const imageFailed = ref(false)
const reportReasons = [
  { label: '疑似诈骗', value: 'SUSPECTED_FRAUD' },
  { label: '违禁或不适宜物品', value: 'PROHIBITED_GOODS' },
  { label: '虚假或误导信息', value: 'MISLEADING' },
  { label: '骚扰或不当内容', value: 'HARASSMENT' },
  { label: '泄露个人隐私', value: 'PRIVACY' },
  { label: '其他违规问题', value: 'OTHER' }
]
onLoad(async (query) => { store.initApi(); item.value = await api.get(`/market-posts/${query?.id}`); imageFailed.value = false })
const price = (value?: string) => value ? (value.startsWith('¥') ? value : `¥${value}`) : '价格面议'
function report() {
  uni.showActionSheet({
    itemList: reportReasons.map((reason) => reason.label),
    success: async ({ tapIndex }) => {
      const selected = reportReasons[tapIndex]
      if (!selected) return
      try {
        await store.reportMarketPost(item.value!.id, selected.value)
        uni.showToast({ title: '举报已提交，管理员将核查', icon: 'none' })
      } catch {
        // Store exposes the API error.
      }
    }
  })
}
</script>

<template>
  <view class="app-shell no-tabs market-detail">
    <AppHeader title="二手详情" backable />
    <LoadingState v-if="!item" />
    <template v-else>
      <view class="product-image"><image v-if="item.image_url && !imageFailed" :src="item.image_url" mode="aspectFill" @error="imageFailed = true" /><AppIcon v-else name="market" size="lg" muted /><StatusBadge v-if="item.status === 'SOLD'" class="sold-status" status="SOLD" label="已售" /></view>
      <view class="product-title"><view><text class="page-title">{{ item.title }}</text><text class="price">{{ price(item.price) }}</text></view><StatusBadge :status="item.status" :label="item.status === 'SOLD' ? '已售出' : '在售'" /></view>
      <view class="product-facts">
        <text>成色：{{ item.marketplace_condition || '未填写' }}</text>
        <text>交付：{{ item.delivery_method || '请与发布者确认' }}</text>
      </view>
      <view class="section"><text class="section-title">物品说明</text><text class="body-copy">{{ item.content }}</text></view>
      <view class="section contact"><text>联系方式</text><text>{{ item.contact || '请通过发布者联系' }}</text></view>
      <view class="trade-tip">开源乓协仅提供信息展示与交流，不提供支付、资金托管、退款或交易担保。请当面验货，勿提前转账；不要透露验证码、密码或证件信息。</view>
      <view v-if="store.signedIn && store.me?.id !== item.author_user_id" class="report-entry" @click="report">举报此信息</view>
      <BottomAction v-if="store.me?.id === item.author_user_id && item.status !== 'SOLD'"><button class="btn btn-danger btn-block" @click="store.markMarketSold(item.id)">标记已售出</button></BottomAction>
    </template>
  </view>
</template>

<style scoped>
.market-detail { padding-bottom: 6.5rem; }
.product-image { position: relative; display: flex; aspect-ratio: 4 / 3; align-items: center; justify-content: center; margin: 1rem -1rem 0; background: #e8ede9; }
.product-image > image:first-child { width: 100%; height: 100%; }
.sold-status { position: absolute; top: 1rem; right: 1rem; }
.product-title { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; padding: 1.25rem 0; border-bottom: 1px solid #e7eae8; }
.product-facts { display: flex; flex-direction: column; gap: .4rem; padding: .8rem 0; color: #59645f; font-size: .8125rem; }
.body-copy { display: block; margin-top: .75rem; }
.contact { display: flex; justify-content: space-between; gap: 1rem; font-size: .875rem; }
.contact text:last-child { color: #4c5853; text-align: right; }
.trade-tip { margin-top: 1.5rem; padding: .85rem; color: #674b1d; background: #fff3d9; border-radius: .6875rem; font-size: .75rem; line-height: 1.55; }
.report-entry { margin-top: 1rem; color: #a33636; font-size: .8125rem; text-align: center; }
</style>
