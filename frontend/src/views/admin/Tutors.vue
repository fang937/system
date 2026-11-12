<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>导师与实训室</h2>
        <p>导师账号即登录账号，新建导师默认初始密码 123456；名额调整不会挤占已锁定的名额。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openTutor()">新增导师</el-button>
    </div>

    <el-card shadow="never" class="card card-pad">
      <el-tabs v-model="tab">
        <el-tab-pane label="导师" name="tutors">
          <el-table v-loading="loading" :data="tutors" stripe>
            <el-table-column label="导师" min-width="128">
              <template #default="{ row }">
                <div class="cell-name">{{ row.tutorName }}</div>
                <div class="tiny">{{ row.username }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="labName" label="实训室" min-width="118" />
            <el-table-column label="职位" width="96" align="center">
              <template #default="{ row }">
                <el-tag
                  size="small"
                  :type="row.position === 'LEADER' ? 'warning' : 'info'"
                  effect="plain"
                >
                  {{ row.positionLabel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="职称" width="82" show-overflow-tooltip />
            <el-table-column prop="researchDirection" label="方向" min-width="128" show-overflow-tooltip />
            <el-table-column label="名额" width="104" align="center">
              <template #default="{ row }">
                <span class="mono">{{ row.quota - row.remainingQuota }}/{{ row.quota }}</span>
                <div class="tiny">报考 {{ row.applicantCount }}</div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="78" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="plain">
                  {{ row.status === 1 ? '招生中' : '已停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="196" align="right" fixed="right">
              <template #default="{ row }">
                <el-button size="small" text type="primary" @click="openTutor(row)">编辑</el-button>
                <el-button
                  v-if="row.position !== 'LEADER'"
                  size="small"
                  text
                  type="warning"
                  @click="changePosition(row, 'LEADER')"
                >
                  设为负责人
                </el-button>
                <el-button
                  v-else
                  size="small"
                  text
                  type="info"
                  @click="changePosition(row, 'TUTOR')"
                >
                  设为讲师
                </el-button>
                <el-button size="small" text :type="row.status === 1 ? 'danger' : 'success'" @click="toggle(row)">
                  {{ row.status === 1 ? '停用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="实训室" name="labs">
          <div class="lab-toolbar">
            <el-button size="small" :icon="Plus" @click="openLab()">新增实训室</el-button>
          </div>
          <el-table v-loading="loading" :data="labs" stripe>
            <el-table-column prop="name" label="实训室" min-width="160" />
            <el-table-column prop="code" label="编码" width="110" />
            <el-table-column prop="location" label="地点" width="140" />
            <el-table-column prop="leaderName" label="负责人" width="110" />
            <el-table-column label="导师 / 名额" width="130" align="center">
              <template #default="{ row }">{{ row.tutorCount }} 人 / {{ row.totalQuota }}</template>
            </el-table-column>
            <el-table-column prop="description" label="方向说明" min-width="220" show-overflow-tooltip />
            <el-table-column label="操作" width="90" align="right">
              <template #default="{ row }">
                <el-button size="small" text type="primary" @click="openLab(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="tutorDialog" :title="tutorForm.id ? '编辑导师' : '新增导师'" width="520px">
      <el-form label-position="top">
        <div class="grid-2">
          <el-form-item label="姓名">
            <el-input v-model="tutorForm.name" placeholder="导师姓名" />
          </el-form-item>
          <el-form-item label="登录账号">
            <el-input v-model="tutorForm.username" :disabled="Boolean(tutorForm.id)" placeholder="工号，如 tutor_wang" />
          </el-form-item>
        </div>
        <el-form-item label="所属实训室">
          <el-select v-model="tutorForm.labId" style="width: 100%" placeholder="选择实训室">
            <el-option v-for="lab in labs" :key="lab.id" :label="lab.name" :value="lab.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="实训室负责人">
          <div class="leader-field">
            <el-switch v-model="tutorForm.labLeader" active-text="授予实训室负责人身份" />
          </div>
          <div class="tiny leader-tip">
            负责人可在导师端使用「实训室事务」：本室招生进度、面试排期、室内调剂。每个实训室仅一位负责人，更换需先取消原负责人。
          </div>
        </el-form-item>
        <div class="grid-2">
          <el-form-item label="职称">
            <el-input v-model="tutorForm.title" placeholder="教授 / 副教授 / 讲师" />
          </el-form-item>
          <el-form-item label="招生名额">
            <el-input-number v-model="tutorForm.quota" :min="0" :max="50" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item label="研究方向">
          <el-input v-model="tutorForm.researchDirection" placeholder="如：机器学习 / 计算机视觉" />
        </el-form-item>
        <el-form-item label="导师介绍">
          <el-input v-model="tutorForm.intro" type="textarea" :rows="3" placeholder="项目方向、对学生的要求等" />
        </el-form-item>
        <div v-if="!tutorForm.id" class="tiny">初始密码为 123456，请提醒导师首次登录后自行联系管理员修改。</div>
      </el-form>
      <template #footer>
        <el-button @click="tutorDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTutor">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="labDialog" :title="labForm.id ? '编辑实训室' : '新增实训室'" width="620px" :close-on-click-modal="false">
      <el-form label-position="top" class="lab-form">
        <el-form-item label="名称">
          <el-input v-model="labForm.name" placeholder="如：202 实训室" />
        </el-form-item>
        <div class="grid-2">
          <el-form-item label="编码">
            <el-input v-model="labForm.code" placeholder="如：202" />
          </el-form-item>
          <el-form-item label="地点">
            <el-input v-model="labForm.location" placeholder="如：实训楼 202（可选）" />
          </el-form-item>
        </div>
        <el-form-item label="核心方向">
          <el-input v-model="labForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="后端 / 工程技术栈">
          <el-input v-model="labForm.techBackend" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="前端 / 客户端技术栈">
          <el-input v-model="labForm.techFrontend" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="AI / 大模型方向">
          <el-input v-model="labForm.aiDirection" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="项目产出类型">
          <el-input v-model="labForm.outputTypes" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="竞赛 / 科研成果">
          <el-input v-model="labForm.achievements" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="培养模式">
          <el-input v-model="labForm.trainingMode" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="考核 / 结业标准">
          <el-input v-model="labForm.graduationStandard" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="适合人群">
          <el-input v-model="labForm.suitableFor" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="labDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveLab">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { api } from '../../api/index.js'

const loading = ref(false)
const saving = ref(false)
const tab = ref('tutors')
const tutors = ref([])
const labs = ref([])
const tutorDialog = ref(false)
const labDialog = ref(false)

const tutorForm = reactive({
  id: null,
  username: '',
  name: '',
  labId: null,
  title: '',
  researchDirection: '',
  intro: '',
  quota: 2,
  labLeader: false
})

const LAB_EXTRA = ['techBackend', 'techFrontend', 'aiDirection', 'outputTypes',
                   'achievements', 'trainingMode', 'graduationStandard', 'suitableFor']
const labForm = reactive({
  id: null, code: '', name: '', location: '', description: '',
  ...Object.fromEntries(LAB_EXTRA.map((k) => [k, '']))
})

async function load() {
  loading.value = true
  try {
    const [tutorList, labList] = await Promise.all([api.adminTutors(), api.adminLabs()])
    tutors.value = tutorList
    labs.value = labList
  } finally {
    loading.value = false
  }
}

function openTutor(row) {
  Object.assign(tutorForm, {
    id: row?.tutorId || null,
    username: row?.username || '',
    name: row?.tutorName || '',
    labId: row?.labId || null,
    title: row?.title || '',
    researchDirection: row?.researchDirection || '',
    intro: row?.intro || '',
    quota: row?.quota ?? 2,
    labLeader: row?.userRole === 'LAB_ADMIN'
  })
  tutorDialog.value = true
}

async function saveTutor() {
  if (!tutorForm.name || !tutorForm.labId) {
    ElMessage.warning('请填写姓名与所属实训室')
    return
  }
  if (!tutorForm.id && !tutorForm.username) {
    ElMessage.warning('请填写登录账号')
    return
  }
  saving.value = true
  try {
    const payload = { ...tutorForm }
    if (tutorForm.id) {
      await api.updateTutor(tutorForm.id, payload)
    } else {
      await api.createTutor(payload)
    }
    ElMessage.success('已保存')
    tutorDialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggle(row) {
  const enable = row.status !== 1
  try {
    await ElMessageBox.confirm(
      enable ? `恢复 ${row.tutorName} 的招生与登录？` : `停用 ${row.tutorName}？其账号将无法登录。`,
      enable ? '启用导师' : '停用导师',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  await api.toggleTutor(row.tutorId, enable)
  ElMessage.success('已更新')
  await load()
}

async function changePosition(row, position) {
  const toLeader = position === 'LEADER'
  // 查该室当前负责人（若有），用于在确认框里把"自动撤销谁"讲清楚
  const currentLeader = tutors.value.find(
    (t) => t.labId === row.labId && t.position === 'LEADER' && t.tutorId !== row.tutorId
  )
  let msg
  if (toLeader) {
    msg = currentLeader
      ? `将 ${row.tutorName} 设为「${row.labName}」负责人。\n原负责人 ${currentLeader.tutorName} 将自动降为实训讲师。`
      : `将 ${row.tutorName} 设为「${row.labName}」负责人。`
  } else {
    msg = `将 ${row.tutorName} 降为实训讲师。\n「${row.labName}」将进入无负责人状态，请尽快指派新负责人。`
  }
  try {
    await ElMessageBox.confirm(msg, '调整职位', {
      confirmButtonText: '确认调整',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  saving.value = true
  try {
    const res = await api.changeTutorPosition(row.tutorId, position)
    if (toLeader && res.demotedPreviousLeader) {
      ElMessage.success(`已设为负责人，原负责人 ${res.demotedPreviousLeader} 已降为讲师`)
    } else if (!toLeader && res.labLeaderless) {
      ElMessage.warning(`已降为讲师，「${row.labName}」暂无负责人`)
    } else {
      ElMessage.success('职位已更新')
    }
    await load()
  } finally {
    saving.value = false
  }
}

function openLab(row) {
  Object.assign(labForm, {
    id: row?.id || null,
    code: row?.code || '',
    name: row?.name || '',
    location: row?.location || '',
    description: row?.description || '',
    ...Object.fromEntries(LAB_EXTRA.map((k) => [k, row?.[k] || '']))
  })
  labDialog.value = true
}

async function saveLab() {
  if (!labForm.name) {
    ElMessage.warning('请填写实训室名称')
    return
  }
  saving.value = true
  try {
    if (labForm.id) {
      await api.updateLab(labForm.id, { ...labForm })
    } else {
      await api.createLab({ ...labForm })
    }
    ElMessage.success('已保存')
    labDialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.cell-name {
  font-size: 13.5px;
  font-weight: 500;
}

.leader-badge {
  margin-left: 6px;
  padding: 1px 7px;
  border-radius: var(--radius-pill);
  font-size: 11px;
  font-weight: 500;
  color: var(--ios-orange-dark);
  background: rgba(255, 149, 0, 0.14);
  vertical-align: 1px;
}

.leader-field {
  display: flex;
  align-items: center;
}

.leader-tip {
  margin-top: 6px;
  line-height: 1.65;
}

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.lab-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 10px;
}

.lab-form {
  max-height: 56vh;
  overflow-y: auto;
  padding-right: 4px;
}
</style>
