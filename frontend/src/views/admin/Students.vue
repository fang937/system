<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>学生名册</h2>
        <p>全部学生账号与当前志愿状态。学号即登录账号，密码见部署说明。</p>
      </div>
      <div class="head-tools">
        <el-input
          v-model="keyword"
          placeholder="搜学号 / 姓名 / 班级"
          clearable
          style="width: 240px"
          :prefix-icon="Search"
        />
        <el-button type="primary" :icon="Plus" @click="openCreate">录入学生</el-button>
        <el-button :icon="Upload" @click="openBatch">批量录入</el-button>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat">
        <div class="label">学生总数</div>
        <div class="value mono">{{ rows.length }}</div>
        <div class="hint">含未填报学生</div>
      </div>
      <div class="stat">
        <div class="label">本轮已填报</div>
        <div class="value mono">{{ submitted }}</div>
        <div class="hint">第 {{ round }} 轮志愿</div>
      </div>
      <div class="stat">
        <div class="label">已录取</div>
        <div class="value mono">{{ matched }}</div>
        <div class="hint">含各轮与调剂</div>
      </div>
      <div class="stat">
        <div class="label">未录取</div>
        <div class="value mono">{{ rows.length - matched }}</div>
        <div class="hint">未填报 {{ notApplied }} · 往轮落选 {{ pastUnmatched }}</div>
      </div>
    </div>

    <el-card shadow="never" class="card card-pad">
      <el-table v-loading="loading" :data="filtered" stripe height="560">
        <el-table-column label="学号" width="120">
          <template #default="{ row }">
            <span class="mono">{{ row.studentNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="姓名" width="130">
          <template #default="{ row }">
            <span class="cell-name">{{ row.studentName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="major" label="专业" min-width="110" />
        <el-table-column prop="className" label="班级" width="110" />
        <el-table-column label="GPA" width="90" align="center">
          <template #default="{ row }">{{ row.gpa ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="志愿状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusOf(row).type" effect="light">{{ statusOf(row).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最终去向" min-width="220">
          <template #default="{ row }">
            <template v-if="row.finalTutorName">
              <el-tag v-if="row.matchedRoundNo" size="small" effect="plain" class="round-tag">
                第 {{ row.matchedRoundNo }} 轮
              </el-tag>
              {{ row.finalLabName }} · {{ row.finalTutorName }}
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="right" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty">没有匹配的学生</div>
        </template>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑学生信息' : '录入学生信息'"
      width="580px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="学号" prop="studentNo">
              <el-input v-model="form.studentNo" placeholder="同时作为登录账号" maxlength="30" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" prop="name">
              <el-input v-model="form.name" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="专业">
              <el-input v-model="form.major" placeholder="软件工程" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="班级">
              <el-input v-model="form.className" placeholder="软工2301" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="年级">
              <el-input v-model="form.grade" placeholder="2023" maxlength="20" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="GPA" prop="gpa">
              <el-input-number
                v-model="form.gpa"
                :min="0"
                :max="4"
                :step="0.01"
                :precision="2"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级权重">
              <el-input-number
                v-model="form.priorityScore"
                :min="0"
                :max="9999"
                :step="1"
                :precision="2"
                controls-position="right"
                placeholder="留空按 GPA×20"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机">
              <el-input v-model="form.phone" maxlength="20" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="技能标签">
              <el-input v-model="form.skillTags" placeholder="逗号分隔，如 Java,Vue,MySQL" maxlength="255" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="邮箱">
              <el-input v-model="form.email" maxlength="100" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <p class="dialog-tip">
        学号同时作为登录账号；新增学生初始密码为 123456。优先级权重留空时按 GPA×20 自动计算。
      </p>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="batchVisible"
      title="批量录入学生"
      width="760px"
      destroy-on-close
      @closed="afterBatchClose"
    >
      <template v-if="!batchResult">
        <el-alert :closable="false" type="info" show-icon class="batch-tip">
          <template #title>
            每行一名学生，列之间用逗号或 Tab 分隔，顺序为
            <b>学号, 姓名, 专业, 班级, 年级, GPA, 技能标签, 手机, 邮箱</b>。
            学号与姓名为必填；含逗号的字段请用英文引号包起来。可直接从 Excel 复制粘贴（含表头会自动跳过）。
          </template>
        </el-alert>
        <el-input
          v-model="batchText"
          type="textarea"
          :rows="12"
          resize="vertical"
          placeholder="2023931,张三,软件工程,软工2301,2023,3.60,&quot;Java,Vue&quot;,13800000000,zhangsan@example.com&#10;2023932,李四,软件工程,软工2302,2023,3.45,Python,13900000000,lisi@example.com"
        />
        <div class="batch-foot">
          <span class="tiny">已识别 {{ parsedCount }} 条 · 单次最多 500 条</span>
        </div>
      </template>

      <template v-else>
        <div class="batch-summary">
          <el-tag type="success" effect="dark">成功 {{ batchResult.successCount }} 人</el-tag>
          <el-tag v-if="batchResult.failCount" type="danger" effect="dark">
            失败 {{ batchResult.failCount }} 人
          </el-tag>
          <span class="tiny">共 {{ batchResult.total }} 条</span>
        </div>
        <el-table :data="batchResult.items" max-height="360" stripe size="small">
          <el-table-column prop="row" label="#" width="50" />
          <el-table-column prop="studentNo" label="学号" width="130" />
          <el-table-column label="姓名" width="110">
            <template #default="{ row }">{{ row.name || '—' }}</template>
          </el-table-column>
          <el-table-column label="结果" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.success ? 'success' : 'danger'" effect="light">
                {{ row.success ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="240" />
        </el-table>
        <p v-if="batchResult.failCount" class="dialog-tip">
          失败项不影响已成功的学生；可点「继续录入」只带出失败项，修正后重新提交。
        </p>
      </template>

      <template #footer>
        <template v-if="!batchResult">
          <el-button @click="batchVisible = false">取消</el-button>
          <el-button type="primary" :loading="batchSaving" @click="submitBatch">开始录入</el-button>
        </template>
        <template v-else>
          <el-button v-if="batchResult.failCount" @click="continueBatch">继续录入</el-button>
          <el-button type="primary" @click="batchVisible = false">完成</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search, Upload } from '@element-plus/icons-vue'
import { api } from '../../api/index.js'
import { useUserStore } from '../../stores/user.js'

const DEFAULT_PASSWORD = '123456'

const store = useUserStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const keyword = ref('')
const dialogVisible = ref(false)
const formRef = ref(null)
const round = computed(() => store.currentRound || 1)

const EMPTY_FORM = {
  id: null,
  studentNo: '',
  name: '',
  major: '',
  className: '',
  grade: '',
  gpa: null,
  priorityScore: null,
  skillTags: '',
  phone: '',
  email: ''
}
const form = reactive({ ...EMPTY_FORM })
const rules = {
  studentNo: [
    { required: true, message: '请填写学号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]{3,30}$/, message: '3~30 位字母、数字、下划线或短横线', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
  gpa: [
    {
      // 留空表示暂不填写；一旦填写就必须是 0~4（含边界）之间的数字
      validator: (_rule, value, callback) => {
        if (value === null || value === undefined || value === '') {
          return callback()
        }
        const num = Number(value)
        if (!Number.isFinite(num)) {
          return callback(new Error('GPA 需为有效数字'))
        }
        if (num < 0 || num > 4) {
          return callback(new Error('GPA 需在 0~4 之间'))
        }
        callback()
      },
      trigger: ['blur', 'change']
    }
  ]
}

// 后端返回的 matched / appliedThisRound 是 0/1，跨轮录取的学生依然算「已录取」
const submitted = computed(() => rows.value.filter((r) => Number(r.appliedThisRound) === 1).length)
const matched = computed(() => rows.value.filter((r) => Number(r.matched) === 1).length)
// 完全没有志愿记录才算未填报；有志愿但未被录取的是「往轮落选」，不能混为一谈
const notApplied = computed(() => rows.value.filter((r) => !r.applicationId).length)
const pastUnmatched = computed(
  () => rows.value.filter((r) => r.applicationId && Number(r.matched) !== 1 && Number(r.appliedThisRound) !== 1).length
)

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return rows.value
  return rows.value.filter((r) =>
    [r.studentNo, r.studentName, r.className, r.major]
      .filter(Boolean)
      .some((v) => String(v).toLowerCase().includes(kw))
  )
})

function statusOf(row) {
  if (Number(row.matched) === 1) return { label: '已录取', type: 'success' }
  if (Number(row.appliedThisRound) === 1) return { label: '已填报', type: 'primary' }
  if (row.applicationId) return { label: '往轮未录取', type: 'warning' }
  return { label: '未填报', type: 'info' }
}

async function load() {
  loading.value = true
  try {
    rows.value = await api.students()
  } finally {
    loading.value = false
  }
}

/* ---------------- 单条录入 / 编辑 ---------------- */

function openCreate() {
  Object.assign(form, EMPTY_FORM)
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.studentId,
    studentNo: row.studentNo || '',
    name: row.studentName || '',
    major: row.major || '',
    className: row.className || '',
    grade: row.grade || '',
    gpa: row.gpa == null ? null : Number(row.gpa),
    priorityScore: row.priorityScore == null ? null : Number(row.priorityScore),
    skillTags: row.skillTags || '',
    phone: row.phone || '',
    email: row.email || ''
  })
  dialogVisible.value = true
}

async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const payload = {
      studentNo: form.studentNo,
      name: form.name,
      major: form.major,
      className: form.className,
      grade: form.grade,
      gpa: form.gpa,
      priorityScore: form.priorityScore,
      skillTags: form.skillTags,
      phone: form.phone,
      email: form.email
    }
    if (form.id) {
      await api.updateStudent(form.id, payload)
      ElMessage.success('学生信息已保存')
    } else {
      await api.createStudent(payload)
      ElMessage.success(`已录入 ${form.name}，初始密码 ${DEFAULT_PASSWORD}`)
    }
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

/* ---------------- 批量录入 ---------------- */

const batchVisible = ref(false)
const batchSaving = ref(false)
const batchText = ref('')
const batchResult = ref(null)
const batchChanged = ref(false)

const parsedRows = computed(() => parseBatch(batchText.value))
const parsedCount = computed(() => parsedRows.value.length)

/** 逗号 / 中文逗号 / Tab 分隔；英文引号内的分隔符不拆分，兼容「Java,Vue」这类标签 */
function splitCells(line) {
  const cells = []
  let current = ''
  let quoted = false
  for (const ch of line) {
    if (ch === '"') {
      quoted = !quoted
      continue
    }
    if (!quoted && (ch === ',' || ch === '，' || ch === '\t')) {
      cells.push(current)
      current = ''
      continue
    }
    current += ch
  }
  cells.push(current)
  return cells.map((cell) => cell.trim())
}

function parseBatch(text) {
  const list = []
  String(text || '')
    .split(/\r?\n/)
    .forEach((line) => {
      if (!line.trim()) return
      const cells = splitCells(line)
      // 从 Excel 粘贴常带表头
      if (/^学号$/.test(cells[0])) return
      list.push({
        studentNo: cells[0] || '',
        name: cells[1] || '',
        major: cells[2] || '',
        className: cells[3] || '',
        grade: cells[4] || '',
        gpa: cells[5] || '',
        skillTags: cells[6] || '',
        phone: cells[7] || '',
        email: cells[8] || ''
      })
    })
  return list
}

function openBatch() {
  batchText.value = ''
  batchResult.value = null
  batchChanged.value = false
  batchVisible.value = true
}

async function submitBatch() {
  const list = parsedRows.value
  if (!list.length) {
    ElMessage.warning('请先粘贴或输入学生数据')
    return
  }
  if (list.length > 500) {
    ElMessage.warning('单次最多 500 条，请分批提交')
    return
  }
  batchSaving.value = true
  try {
    const result = await api.batchStudents(list)
    batchResult.value = result
    if (result.successCount > 0) batchChanged.value = true
    ElMessage.success(`录入完成：成功 ${result.successCount} 人，失败 ${result.failCount} 人`)
  } finally {
    batchSaving.value = false
  }
}

/** 只把失败项带回输入框，修正后重提；已成功的不会重复创建 */
function continueBatch() {
  const failed = parsedRows.value.filter((row, index) => !batchResult.value?.items?.[index]?.success)
  const quote = (value) => {
    const text = String(value ?? '')
    return text.includes(',') ? `"${text}"` : text
  }
  batchText.value = failed
    .map((row) =>
      [row.studentNo, row.name, row.major, row.className, row.grade, row.gpa, row.skillTags, row.phone, row.email]
        .map(quote)
        .join(',')
    )
    .join('\n')
  batchResult.value = null
}

function afterBatchClose() {
  if (batchChanged.value) {
    batchChanged.value = false
    load()
  }
}

onMounted(load)
</script>

<style scoped>
.head-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cell-name {
  font-weight: 500;
}

.round-tag {
  margin-right: 6px;
}

.dialog-tip {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--label-3);
}

.batch-tip {
  margin-bottom: 10px;
}

.batch-foot {
  margin-top: 6px;
  text-align: right;
}

.batch-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
</style>
