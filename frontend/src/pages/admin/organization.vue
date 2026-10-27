<script setup lang="ts">
import { onMounted } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import EmptyState from '../../components/EmptyState.vue'
import LoadingState from '../../components/LoadingState.vue'
import { useAppStore, type RoleCode } from '../../stores/app'

const store = useAppStore()
const editableRoles: Array<{ code: RoleCode; label: string }> = [
  { code: 'ADMIN', label: '管理员' },
  { code: 'VICE_MINISTER', label: '副部长' },
  { code: 'MINISTER', label: '部长' },
  { code: 'VICE_PRESIDENT', label: '副会长' },
  { code: 'PRESIDENT', label: '会长' },
  { code: 'ADVISOR', label: '指导老师' }
]

onMounted(async () => {
  try {
    await store.loadOrganizationMembers()
  } catch {
    // The store exposes the load error to the page.
  }
})

async function toggleRole(userId: string, roleCode: RoleCode, active: boolean) {
  try {
    await store.setMemberRole(userId, roleCode, active)
  } catch {
    // The store exposes the API error to the page.
  }
}
</script>

<template>
  <view class="app-shell no-tabs">
    <AppHeader title="组织管理" backable />
    <view class="notice notice-info">仅会长和指导老师可以任命或撤销管理职务。管理员拥有日常管理权限，但不能任命其他职务。</view>
    <LoadingState v-if="store.organizationMembersStatus === 'loading'" text="正在加载正式成员" />
    <view v-else-if="store.organizationMembersStatus === 'error'" class="notice notice-error">无法加载成员列表，请确认当前账号具有组织管理权限。</view>
    <EmptyState v-else-if="!store.organizationMembers.length" title="暂无有效成员" />
    <view v-for="member in store.organizationMembers" :key="member.userId" v-else class="member-card">
      <view class="member-heading">
        <view>
          <text class="member-name">{{ member.nickname || '未设置昵称' }}</text>
          <text class="member-number">{{ member.memberNumber }}</text>
        </view>
      </view>
      <view class="role-list">
        <button
          v-for="role in editableRoles"
          :key="role.code"
          :class="['role-button', { active: member.roles.includes(role.code) }]"
          :disabled="store.loading"
          @click="toggleRole(member.userId, role.code, !member.roles.includes(role.code))"
        >
          {{ member.roles.includes(role.code) ? `撤销${role.label}` : `任命${role.label}` }}
        </button>
      </view>
    </view>
  </view>
</template>

<style scoped>
.member-card { margin-top: .75rem; padding: 1rem; background: #fff; border: 1px solid #e2e7e3; border-radius: .875rem; }
.member-heading { display: flex; align-items: center; justify-content: space-between; }
.member-name, .member-number { display: block; }
.member-name { color: #18201d; font-size: .9375rem; font-weight: 700; }
.member-number { margin-top: .2rem; color: #66706b; font-size: .75rem; }
.role-list { display: flex; flex-wrap: wrap; gap: .5rem; margin-top: .875rem; }
.role-button { min-height: 2rem; padding: 0 .625rem; color: #365148; background: #eef3f0 !important; border-radius: 999px; font-size: .75rem; font-weight: 600; }
.role-button.active { color: #fff; background: #365148 !important; }
</style>
