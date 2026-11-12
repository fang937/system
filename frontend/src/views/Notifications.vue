<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>消息通知</h2>
        <p>Offer 发放、超时提醒、调剂结果都会在这里留痕。</p>
      </div>
      <el-button :disabled="!unread" @click="readAll">全部标为已读</el-button>
    </div>

    <el-card v-loading="loading" shadow="never" class="card card-pad">
      <div v-if="!list.length" class="empty">暂无通知</div>
      <div v-for="item in list" :key="item.id" class="notice" :class="{ unread: !item.isRead }" @click="read(item)">
        <div class="notice-dot" />
        <div class="notice-body">
          <div class="notice-title">{{ item.title }}</div>
          <div class="notice-content">{{ item.content }}</div>
          <div class="tiny">{{ item.createdAt }}</div>
        </div>
        <el-tag v-if="!item.isRead" size="small" type="warning" effect="plain">未读</el-tag>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '../api/index.js'

const loading = ref(false)
const list = ref([])
const unread = computed(() => list.value.filter((n) => !n.isRead).length)

async function load() {
  loading.value = true
  try {
    const data = await api.notifications()
    list.value = data.list || []
  } finally {
    loading.value = false
  }
}

async function read(item) {
  if (item.isRead) return
  await api.readNotification(item.id)
  item.isRead = 1
}

async function readAll() {
  await api.readAllNotifications()
  await load()
}

onMounted(load)
</script>

<style scoped>
.notice {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 4px;
  border-bottom: 1px solid var(--line);
  cursor: pointer;
}

.notice:last-child {
  border-bottom: none;
}

.notice-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--label-3);
  margin-top: 7px;
  flex: none;
}

.notice.unread .notice-dot {
  background: var(--el-color-primary);
  box-shadow: 0 0 0 4px var(--el-color-primary-light-9);
}

.notice-body {
  flex: 1;
  min-width: 0;
}

.notice-title {
  font-size: 14px;
  font-weight: 500;
}

.notice-content {
  font-size: 12.5px;
  color: var(--ink-3);
  line-height: 1.7;
  margin: 4px 0 6px;
}
</style>
