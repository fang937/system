<template>
  <div class="page">
    <div class="page-title">导师与志愿填报</div>
    <div class="page-sub">
      最多选择 3 个平行志愿，不分先后。提交后本轮不可修改。
    </div>

    <el-alert
      v-if="locked"
      type="success"
      :closable="false"
      show-icon
      title="你已提交本轮志愿"
      :description="`志愿导师：${lockedNames}。如需调整请等待管理员开启下一轮。`"
      style="margin-bottom: 16px"
    />

    <div class="toolbar">
      <el-select v-model="labFilter" placeholder="全部实训室" clearable style="width: 220px">
        <el-option v-for="l in labs" :key="l.id" :label="l.name" :value="String(l.id)" />
      </el-select>
      <el-input v-model="keyword" placeholder="搜索导师 / 研究方向" style="width: 240px" clearable />
      <div style="flex: 1" />
      <span style="font-size: 13px; color: var(--muted)">已选 {{ picked.length }} / 3</span>
    </div>

    <div class="tutor-grid">
      <div
        v-for="t in filtered"
        :key="t.id"
        class="tutor-card"
        :class="{ picked: picked.includes(String(t.id)) }"
      >
        <div class="name">
          {{ t.tutorName }}
          <el-tag size="small" effect="plain">{{ t.title }}</el-tag>
          <span v-if="picked.includes(String(t.id))" class="badge-choice">{{ picked.indexOf(String(t.id)) + 1 }}</span>
        </div>
        <div class="meta">{{ t.labName }} · 名额 {{ t.remainingQuota }}/{{ t.quota }}</div>
        <div class="intro">{{ t.intro }}</div>
        <div class="meta">方向：{{ t.researchDirection }}</div>
        <el-button
          :type="picked.includes(String(t.id)) ? 'danger' : 'primary'"
          :plain="!picked.includes(String(t.id))"
          :disabled="locked || (!picked.includes(String(t.id)) && picked.length >= 3) || t.remainingQuota <= 0"
          @click="toggle(t)"
        >
          {{ picked.includes(String(t.id)) ? '取消选择' : (t.remainingQuota <= 0 ? '名额已满' : '加入志愿') }}
        </el-button>
      </div>
    </div>

    <div v-if="!filtered.length" class="empty">没有匹配的导师</div>

    <div class="card" style="margin-top: 18px" v-if="!locked">
      <div style="font-weight: 600; margin-bottom: 12px">提交志愿</div>
      <el-form label-width="120px">
        <el-form-item label="已选导师">
          <el-tag v-for="id in picked" :key="id" style="margin-right: 6px" closable @close="remove(id)">
            {{ nameOf(id) }}
          </el-tag>
          <span v-if="!picked.length" style="color: var(--muted); font-size: 13px">尚未选择</span>
        </el-form-item>
        <el-form-item label="服从实训室内调剂">
          <el-switch v-model="acceptLabAdjust" />
        </el-form-item>
        <el-form-item label="服从全院调剂">
          <el-switch v-model="acceptGlobalAdjust" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" :disabled="!picked.length" @click="submit">
            提交志愿
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'

const labs = ref([])
const tutors = ref([])
const labFilter = ref('')
const keyword = ref('')
const picked = ref([])
const acceptLabAdjust = ref(true)
const acceptGlobalAdjust = ref(true)
const submitting = ref(false)
const application = ref(null)

const locked = computed(() => !!application.value)
const lockedNames = computed(() => {
  if (!application.value) return ''
  return [application.value.tutor1Id, application.value.tutor2Id, application.value.tutor3Id]
    .filter(Boolean)
    .map(nameOf)
    .join('、')
})

const filtered = computed(() =>
  tutors.value.filter((t) => {
    const okLab = !labFilter.value || String(t.labId) === labFilter.value
    const k = keyword.value.trim()
    const okKw = !k || t.tutorName.includes(k) || (t.researchDirection || '').includes(k)
    return okLab && okKw
  })
)

function nameOf(id) {
  const t = tutors.value.find((x) => String(x.id) === String(id))
  return t ? t.tutorName : id
}

function toggle(t) {
  const id = String(t.id)
  const i = picked.value.indexOf(id)
  if (i >= 0) picked.value.splice(i, 1)
  else if (picked.value.length < 3) picked.value.push(id)
}

function remove(id) {
  const i = picked.value.indexOf(id)
  if (i >= 0) picked.value.splice(i, 1)
}

async function submit() {
  try {
    await ElMessageBox.confirm(
      `提交后本轮不可修改，确认提交 ${picked.value.length} 个志愿？`,
      '确认提交',
      { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '再看看' }
    )
  } catch {
    return
  }
  submitting.value = true
  try {
    await api.apply({
      tutorIds: picked.value.map(Number),
      acceptLabAdjust: acceptLabAdjust.value,
      acceptGlobalAdjust: acceptGlobalAdjust.value
    })
    ElMessage.success('志愿已提交')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}

async function load() {
  const [ls, ts, app] = await Promise.all([api.labs(), api.tutors(), api.myApplication()])
  labs.value = ls || []
  tutors.value = ts || []
  application.value = app?.application || null
  if (application.value) {
    picked.value = [application.value.tutor1Id, application.value.tutor2Id, application.value.tutor3Id]
      .filter(Boolean)
      .map(String)
  }
}

onMounted(load)
</script>
