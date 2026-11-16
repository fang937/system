<template>
  <section class="timeline" :class="{ compact }">
    <header class="tl-head">
      <div class="tl-head-main">
        <h3>{{ compact ? '报名进度' : '双选流程与时间轴' }}</h3>
        <p class="tl-sub">
          <template v-if="activeSteps.length === 1">
            当前处于<b>{{ activeSteps[0].title }}</b>
            <span class="tl-left" :class="{ urgent: isUrgent(activeSteps[0].endAt, now) }">
              · 剩余 {{ countdown(activeSteps[0].endAt, now) }}
            </span>
          </template>
          <template v-else-if="activeSteps.length > 1">
            当前同时进行 <b>{{ activeSteps.length }}</b> 个阶段
            <span class="tl-active-list">
              <span
                v-for="step in activeSteps"
                :key="step.code + (step.roundNo ?? '')"
                class="tl-active-chip"
                :class="{ urgent: isUrgent(step.endAt, now) }"
              >
                <span class="tl-active-name">{{ step.title }}</span>
                <span class="tl-active-time">
                  <el-icon v-if="isUrgent(step.endAt, now)"><WarningFilled /></el-icon>
                  剩余 {{ countdown(step.endAt, now) }}
                </span>
              </span>
            </span>
          </template>
          <template v-else>当前无进行中的阶段，请留意下一节点开放时间</template>
        </p>
      </div>
      <el-button v-if="compact" size="small" text type="primary" @click="expanded = !expanded">
        {{ expanded ? '收起说明' : '展开说明' }}
      </el-button>
    </header>

    <!-- 紧凑模式：横向阶段卡片，对应「八步流程」的进度视图 -->
    <ol v-if="compact" class="tl-strip">
      <li
        v-for="step in steps"
        :key="step.code + (step.roundNo ?? '')"
        class="tl-card"
        :class="step.state.toLowerCase()"
      >
        <div class="tl-card-top">
          <span class="tl-num">{{ String(step.seq).padStart(2, '0') }}</span>
          <span class="tl-pill">{{ step.stateLabel }}</span>
        </div>
        <div class="tl-card-title">{{ step.title }}</div>
        <p v-show="expanded" class="tl-card-desc">{{ step.desc }}</p>
        <div class="tl-card-time">
          <el-icon><Clock /></el-icon>
          <span>{{ shortTime(step.startAt) }} — {{ shortTime(step.endAt) }}</span>
        </div>
        <span
          v-if="step.state === 'ACTIVE'"
          class="tl-card-left"
          :class="{ urgent: isUrgent(step.endAt, now) }"
        >
          剩余 {{ countdown(step.endAt, now) }}
        </span>
      </li>
    </ol>

    <!-- 完整模式（管理端可编辑）：保留纵向列表，只做换肤 -->
    <ol v-else class="tl-list">
      <li v-for="step in steps" :key="step.code + (step.roundNo ?? '')" class="tl-item" :class="step.state.toLowerCase()">
        <span class="tl-dot">{{ step.seq }}</span>
        <div class="tl-body">
          <div class="tl-title">
            {{ step.title }}
            <span class="tl-state" :class="step.state.toLowerCase()">
              {{ step.stateLabel }}
            </span>
          </div>
          <p class="tl-desc">{{ step.desc }}</p>
          <div class="tl-time">
            <el-icon><Clock /></el-icon>
            <span>{{ fmtTime(step.startAt) }} — {{ fmtTime(step.endAt) }}</span>
            <span
              v-if="step.state === 'ACTIVE'"
              class="tl-countdown"
              :class="{ urgent: isUrgent(step.endAt, now) }"
            >
              剩余 {{ countdown(step.endAt, now) }}
            </span>
          </div>
        </div>
      </li>
    </ol>

    <p class="tl-contact">
      <el-icon><InfoFilled /></el-icon>
      <span>{{ contactText }}</span>
    </p>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Clock, InfoFilled, WarningFilled } from '@element-plus/icons-vue'
import { countdown, fmtTime, isUrgent, phaseStateMap } from '../utils/labels.js'

const props = defineProps({
  // /api/meta/phase 返回的 phases（含轮次展开的所有阶段）
  phases: { type: Array, default: () => [] },
  round: { type: Number, default: 1 },
  // 紧凑模式：横向卡片条，默认折叠说明
  compact: { type: Boolean, default: false },
  // 管理端为 true，底部提示换成可编辑口径
  canEdit: { type: Boolean, default: false }
})

const expanded = ref(!props.compact)

const STEPS = [
  {
    code: 'BRIEFING',
    seq: 1,
    title: '集中宣讲',
    desc: '学院组织统一宣讲，由各实训室介绍项目方向、招收名额、培养安排与考核要求。'
  },
  {
    code: 'OPEN_DAY',
    seq: 2,
    title: '实训室开放日',
    desc: '学生实地走访实训室，与导师、高年级学生交流，了解项目氛围和培养方式。'
  },
  {
    code: 'APPLY',
    seq: 3,
    title: '意向填报',
    desc: '学生最多填报 3 个平行导师意向，不分志愿先后，同时选择是否服从实训室内部调剂和全院跨实训室调剂。填报窗口关闭后不可再修改。'
  },
  {
    code: 'INTERVIEW',
    seq: 4,
    title: '导师初选与面试考核',
    desc: '以实训室为单位统一组织面试，无论报名人数是否满额，所有报名学生均参加；考察学习态度、逻辑思维、编程基础和时间投入，确定拟录取名单、递补名单和内部调剂候选名单。'
  },
  {
    code: 'CONFIRM',
    seq: 5,
    title: '拟录取与学生确认',
    desc: '仅收到 1 个拟录取意向的自动确认；收到 2 个及以上意向的，在规定时间内自主选择唯一去向；逾期未确认视为放弃，出空名额按递补顺位补充。'
  },
  {
    code: 'SUPPLEMENT',
    seq: 6,
    title: '分轮补录',
    desc: '学院公布剩余名额，未录取学生重新填报志愿；实训室统一组织面试、双向确认与顺位递补，依次完成第二轮和第三轮补录。'
  },
  {
    code: 'ADJUST',
    seq: 7,
    title: '统筹调剂录取',
    desc: '三轮补录后剩余名额纳入全院调剂池，按优先级统筹安排：第一优先级为实训室内部调剂，第二优先级为全院跨实训室调剂，第三优先级为约谈未服从调剂学生并作最终统筹安置。'
  },
  {
    code: 'PUBLISH',
    seq: 8,
    title: '结果公示与正式入组',
    desc: '学院汇总最终录取名单并公示 1 天；公示无异议后，学生正式加入实训室开展 AI + PBL 项目实训。'
  }
]

/** 合并轮次阶段：轮次相关的三条取当前轮次那一份，其余取全局那份 */
const steps = computed(() =>
  STEPS.map((step) => {
    const list = props.phases.filter((p) => p.code === step.code)
    const hit =
      list.find((p) => p.roundNo === null || p.roundNo === undefined) ||
      list.find((p) => Number(p.roundNo) === Number(props.round)) ||
      list[0] ||
      null
    const state = hit?.state || 'PENDING'
    return {
      ...step,
      startAt: hit?.startAt,
      endAt: hit?.endAt,
      roundNo: hit?.roundNo,
      state,
      stateLabel: phaseStateMap[state]?.label || state
    }
  })
)

/** 进行中的全部阶段：时间窗可能重叠，逐条展示各自剩余时间 */
const activeSteps = computed(() => steps.value.filter((s) => s.state === 'ACTIVE'))

/** 10 秒一跳，足够让倒计时与「不足 1 小时」的红色状态及时更新 */
const now = ref(Date.now())
let ticker = null

onMounted(() => {
  ticker = setInterval(() => {
    now.value = Date.now()
  }, 10000)
})

onBeforeUnmount(() => {
  clearInterval(ticker)
})

/** 卡片条空间有限，时间只保留「月-日」 */
function shortTime(value) {
  if (!value) return '—'
  const text = fmtTime(value)
  return text.replace(/^\d{4}-/, '')
}

const contactText = computed(() =>
  props.canEdit
    ? '如需调整节点时间，可在此页直接修改或点击「立即进入 / 立即结束」；流程口径有疑问请联系学院教务。'
    : '流程与时间如有疑问，请联系实训室管理员或学院教务老师协助处理。'
)
</script>

<style scoped>
.timeline {
  background: var(--surface);
  border: 1px solid rgba(28, 111, 102, 0.07);
  border-radius: var(--clay-radius-lg);
  padding: 18px 20px 14px;
  box-shadow: var(--clay-shadow-sm), var(--clay-inset);
}

.tl-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.tl-head-main {
  flex: 1;
  min-width: 0;
}

.tl-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.015em;
  color: var(--ink-900);
}

.tl-sub {
  margin: 5px 0 0;
  font-size: 12px;
  color: var(--ink-500);
}

.tl-sub b {
  color: var(--brand-600);
  font-weight: 700;
}

.tl-left {
  color: var(--sand-600);
  font-weight: 600;
}

/* 剩余不足 1 小时：统一转红 */
.tl-left.urgent {
  color: var(--ios-red-dark);
  font-weight: 800;
}

/* 多阶段并行：逐个列出各自的剩余时间 */
.tl-active-list {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-left: 6px;
  vertical-align: middle;
}

.tl-active-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 9px;
  border-radius: 999px;
  background: rgba(47, 157, 146, 0.08);
  color: var(--brand-700);
  font-size: 11.5px;
  font-weight: 600;
  line-height: 1.7;
}

.tl-active-name {
  font-weight: 650;
}

.tl-active-time {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-variant-numeric: tabular-nums;
}

.tl-active-time .el-icon {
  font-size: 11px;
}

.tl-active-chip.urgent {
  background: rgba(255, 59, 48, 0.1);
  color: var(--ios-red-dark);
  box-shadow: inset 0 0 0 1px rgba(255, 59, 48, 0.18);
}

.tl-active-chip.urgent .tl-active-time {
  font-weight: 800;
}

/* ============================================================================
   紧凑模式：横向阶段卡片
   ============================================================================ */

.tl-strip {
  list-style: none;
  margin: 14px 0 0;
  padding: 2px 2px 6px;
  display: grid;
  grid-auto-flow: column;
  /* 1fr 让八步在桌面端正好排满，不撑出滚动条 */
  grid-auto-columns: minmax(0, 1fr);
  gap: 8px;
  overflow-x: auto;
  scrollbar-width: none;
  scroll-snap-type: x proximity;
}

.tl-strip::-webkit-scrollbar {
  display: none;
}

.tl-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 118px;
  padding: 13px 14px 12px;
  border-radius: 18px;
  background: var(--surface);
  border: 1px solid rgba(28, 111, 102, 0.07);
  box-shadow: 0 2px 6px rgba(20, 78, 70, 0.03), inset 0 1px 0 rgba(255, 255, 255, 0.9);
  overflow: hidden;
  scroll-snap-align: start;
  transition: transform 0.28s var(--clay-ease), box-shadow 0.28s var(--clay-ease);
}

.tl-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px -18px rgba(20, 78, 70, 0.7), inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.tl-card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 6px;
}

.tl-num {
  font-size: 30px;
  line-height: 1;
  font-weight: 800;
  letter-spacing: -0.06em;
  font-variant-numeric: tabular-nums;
  color: rgba(47, 157, 146, 0.18);
}

.tl-pill {
  flex: none;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--ink-400);
  background: rgba(91, 125, 119, 0.09);
  white-space: nowrap;
}

.tl-card-title {
  font-size: 12.5px;
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: -0.01em;
  color: var(--ink-800);
}

.tl-card-desc {
  margin: 0;
  font-size: 11px;
  line-height: 1.62;
  color: var(--ink-500);
}

.tl-card-time {
  margin-top: auto;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 10.5px;
  font-variant-numeric: tabular-nums;
  color: var(--ink-400);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tl-card-left {
  align-self: flex-start;
  font-size: 10.5px;
  font-weight: 700;
  color: var(--brand-700);
}

/* 进行中卡片是薄荷实底，红色时间用白底胶囊托住 */
.tl-card-left.urgent {
  padding: 1px 7px;
  border-radius: 999px;
  background: #fff;
  color: var(--ios-red-dark);
  font-weight: 800;
  box-shadow: 0 0 0 1px rgba(215, 0, 21, 0.14), 0 3px 10px rgba(215, 0, 21, 0.18);
}

/* ---- 进行中：薄荷实底 ---- */

.tl-card.active {
  background: linear-gradient(160deg, #3cb8a8 0%, #2f9d92 58%, #248a80 100%);
  border-color: transparent;
  box-shadow: 0 16px 30px -22px rgba(47, 157, 146, 1), inset 0 1px 0 rgba(255, 255, 255, 0.26);
}

.tl-card.active .tl-num {
  color: rgba(255, 255, 255, 0.38);
}

.tl-card.active .tl-pill {
  color: #fff;
  background: rgba(255, 255, 255, 0.24);
}

.tl-card.active .tl-card-title {
  color: #fff;
}

.tl-card.active .tl-card-desc {
  color: rgba(255, 255, 255, 0.78);
}

.tl-card.active .tl-card-time {
  color: rgba(255, 255, 255, 0.72);
}

.tl-card.active .tl-card-left {
  color: #fff;
}

/* 进行中 + 不足 1 小时：白底红字，覆盖上面的白色文字 */
.tl-card.active .tl-card-left.urgent {
  color: var(--ios-red-dark);
}

/* ---- 未开始：奶油底 ---- */

.tl-card.pending {
  background: var(--cream-100);
  border-color: rgba(217, 164, 95, 0.16);
}

.tl-card.pending .tl-num {
  color: rgba(217, 164, 95, 0.34);
}

.tl-card.pending .tl-pill {
  color: var(--sand-600);
  background: rgba(217, 164, 95, 0.16);
}

/* ---- 已结束：灰化 ---- */

.tl-card.closed {
  background: rgba(91, 125, 119, 0.045);
  border-color: transparent;
  box-shadow: none;
}

.tl-card.closed .tl-num {
  color: rgba(91, 125, 119, 0.16);
}

.tl-card.closed .tl-card-title {
  color: var(--ink-400);
  font-weight: 600;
}

/* ============================================================================
   完整模式：纵向列表
   ============================================================================ */

.tl-list {
  list-style: none;
  margin: 16px 0 0;
  padding: 0;
  position: relative;
}

.tl-item {
  position: relative;
  display: flex;
  gap: 12px;
  padding: 0 0 16px;
}

.tl-item:last-child {
  padding-bottom: 2px;
}

.tl-item::before {
  content: '';
  position: absolute;
  left: 13px;
  top: 26px;
  bottom: 0;
  width: 2px;
  background: rgba(28, 111, 102, 0.08);
}

.tl-item:last-child::before {
  display: none;
}

.tl-item.active::before {
  background: linear-gradient(180deg, var(--brand-400), rgba(28, 111, 102, 0.08));
}

.tl-dot {
  flex: none;
  width: 28px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  background: rgba(47, 157, 146, 0.08);
  color: var(--ink-400);
  z-index: 1;
}

.tl-item.active .tl-dot {
  background: linear-gradient(150deg, #4fc0b2, #2f9d92);
  color: #fff;
  box-shadow: 0 0 0 4px rgba(47, 157, 146, 0.14);
}

.tl-body {
  flex: 1;
  min-width: 0;
}

.tl-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
  font-weight: 650;
  color: var(--ink-900);
  line-height: 26px;
}

.tl-item.closed .tl-title {
  color: var(--ink-500);
}

.tl-state {
  font-size: 10.5px;
  font-weight: 600;
  padding: 1px 8px;
  border-radius: 999px;
  background: rgba(91, 125, 119, 0.09);
  color: var(--ink-400);
}

.tl-state.active {
  background: rgba(47, 157, 146, 0.12);
  color: var(--brand-700);
}

.tl-desc {
  margin: 3px 0 0;
  font-size: 12px;
  line-height: 1.68;
  color: var(--ink-500);
}

.tl-time {
  margin-top: 5px;
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  color: var(--ink-400);
  font-variant-numeric: tabular-nums;
  flex-wrap: wrap;
}

.tl-countdown {
  color: var(--sand-600);
  font-weight: 600;
}

.tl-countdown.urgent {
  color: var(--ios-red-dark);
  font-weight: 800;
}

.tl-contact {
  margin: 12px 0 0;
  padding-top: 11px;
  border-top: 1px dashed rgba(28, 111, 102, 0.12);
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  color: var(--ink-400);
}

.compact {
  padding: 16px 18px 10px;
}

@media (max-width: 1000px) {
  /* 窄屏不再均分，改为固定宽度横向滚动，保证文字可读 */
  .tl-strip {
    grid-auto-columns: 132px;
  }

  .tl-num {
    font-size: 26px;
  }

  .tl-card {
    min-height: 108px;
    padding: 11px 12px 10px;
  }

  .tl-desc {
    font-size: 11.5px;
  }
}
</style>
