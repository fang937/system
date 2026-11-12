<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>录取结果</h2>
        <p>结果在「结果公示」阶段统一发布；公示前可先查看自己的确认状态。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-card v-loading="loading" shadow="never" class="card card-pad">
      <template v-if="result.matched">
        <div class="result-head">
          <div class="seal">已录取</div>
          <div>
            <div class="result-title">{{ detail.tutorName }}<span class="muted"> · {{ detail.title }}</span></div>
            <div class="muted">{{ detail.labName }}<span v-if="detail.labLocation"> · {{ detail.labLocation }}</span></div>
          </div>
          <div class="spacer" />
          <el-tag :type="result.published ? 'success' : 'warning'" effect="light" size="large">
            {{ result.published ? '已公示' : '待公示' }}
          </el-tag>
        </div>

        <el-descriptions :column="3" border class="result-desc">
          <el-descriptions-item label="轮次">{{ roundLabel(result.round) }}</el-descriptions-item>
          <el-descriptions-item label="研究方向">{{ detail.researchDirection || '—' }}</el-descriptions-item>
          <el-descriptions-item label="确认时间">{{ fmtTime(result.matchedAt) }}</el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="!result.published"
          class="hint"
          :closable="false"
          type="info"
          show-icon
          title="当前尚未到公示阶段，以上为你的系统内确认结果，最终名单以学院公示为准。"
        />
      </template>

      <el-empty v-else :description="result.message || '暂无录取记录'">
        <el-button @click="router.push('/student/offers')">查看我的 Offer</el-button>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { api } from '../../api/index.js'
import { fmtTime, roundLabel } from '../../utils/labels.js'

const router = useRouter()
const loading = ref(false)
const result = ref({ matched: false })

const detail = computed(() => result.value.detail || {})

async function load() {
  loading.value = true
  try {
    result.value = await api.myResult()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.result-head {
  display: flex;
  align-items: center;
  gap: 16px;
}

.seal {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: rgba(52, 199, 89, 0.12);
  color: var(--ios-green-dark);
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(52, 199, 89, 0.32);
}

.result-title {
  font-size: 18px;
  font-weight: 600;
}

.result-desc {
  margin-top: 20px;
}

.hint {
  margin-top: 16px;
}

.spacer {
  flex: 1;
}
</style>
