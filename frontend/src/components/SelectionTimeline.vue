<template>
  <section class="timeline" :class="{ compact }">
    <header class="tl-head">
      <div class="tl-head-main">
        <h3>双选流程与时间轴</h3>
        <p class="tl-sub">
          <template v-if="activeStep">
            当前处于<b>{{ activeStep.title }}</b>
            <span class="tl-left">· 剩余 {{ countdownText }}</span>
          </template>
          <template v-else>当前无进行中的阶段，请留意下一节点开放时间</template>
        </p>
      </div>
      <el-button v-if="compact" size="small" text type="primary" @click="expanded = !expanded">
        {{ expanded ? '收起说明' : '展开说明' }}
      </el-button>
    </header>

    <ol class="tl-list">
      <li v-for="step in steps" :key="step.code + (step.roundNo ?? '')" class="tl-item" :class="step.state.toLowerCase()">
        <span class="tl-dot">{{ step.seq }}</span>
        <div class="tl-body">
          <div class="tl-title">
            {{ step.title }}
            <span class="tl-state" :class="step.state.toLowerCase()">
              {{ step.stateLabel }}
            </span>
          </div>
          <p v-show="expanded || !compact" class="tl-desc">{{ step.desc }}</p>
          <div class="tl-time">
            <el-icon><Clock /></el-icon>
            <span>{{ fmtTime(step.startAt) }} — {{ fmtTime(step.endAt) }}</span>
            <span v-if="step.state === 'ACTIVE'" class="tl-countdown">剩余 {{ countdown(step.endAt) }}</span>
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
import { computed, ref } from 'vue'
import { Clock, InfoFilled } from '@element-plus/icons-vue'
import { countdown, fmtTime, phaseStateMap } from '../utils/labels.js'

const props = defineProps({
  // /api/meta/phase 返回的 phases（含轮次展开的所有阶段）
  phases: { type: Array, default: () => [] },
  round: { type: Number, default: 1 },
  // 紧凑模式：默认折叠，需要点开才看完整 8 步
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

/** 进行中的阶段取 seq 最大的那条，与后端 active() 口径一致 */
const activeStep = computed(() => {
  const list = steps.value.filter((s) => s.state === 'ACTIVE')
  return list.length ? list[list.length - 1] : null
})

const countdownText = computed(() => (activeStep.value ? countdown(activeStep.value.endAt) : ''))

const contactText = computed(() =>
  props.canEdit
    ? '如需调整节点时间，可在此页直接修改或点击「立即进入 / 立即结束」；流程口径有疑问请联系学院教务。'
    : '流程与时间如有疑问，请联系实训室管理员或学院教务老师协助处理。'
)
</script>

<style scoped>
.timeline {
  background: var(--surface);
  border: 1px solid var(--separator);
  border-radius: var(--radius-lg);
  padding: 16px 18px 12px;
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
  font-size: 14px;
  font-weight: 650;
  color: var(--label-1);
}

.tl-sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--label-2);
}

.tl-sub b {
  color: var(--ios-blue);
  font-weight: 650;
}

.tl-left {
  color: var(--ios-orange-dark);
}

.tl-list {
  list-style: none;
  margin: 14px 0 0;
  padding: 0;
  position: relative;
}

.tl-item {
  position: relative;
  display: flex;
  gap: 12px;
  padding: 0 0 14px;
}

.tl-item:last-child {
  padding-bottom: 2px;
}

/* 左侧竖线 */
.tl-item::before {
  content: '';
  position: absolute;
  left: 13px;
  top: 26px;
  bottom: 0;
  width: 2px;
  background: var(--separator);
}

.tl-item:last-child::before {
  display: none;
}

.tl-item.active::before {
  background: linear-gradient(180deg, var(--ios-blue), var(--separator));
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
  background: var(--fill-1);
  color: var(--label-3);
  z-index: 1;
}

.tl-item.active .tl-dot {
  background: var(--ios-blue);
  color: #fff;
  box-shadow: 0 0 0 4px rgba(0, 122, 255, 0.14);
}

.tl-item.closed .tl-dot {
  background: var(--fill-2);
  color: var(--label-3);
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
  font-weight: 600;
  color: var(--label-1);
  line-height: 26px;
}

.tl-item.closed .tl-title {
  color: var(--label-2);
}

.tl-state {
  font-size: 11px;
  font-weight: 500;
  padding: 1px 7px;
  border-radius: var(--radius-pill);
  background: var(--fill-1);
  color: var(--label-3);
}

.tl-state.active {
  background: rgba(0, 122, 255, 0.12);
  color: var(--ios-blue);
}

.tl-state.closed {
  background: var(--fill-1);
  color: var(--label-3);
}

.tl-desc {
  margin: 2px 0 0;
  font-size: 12px;
  line-height: 1.65;
  color: var(--label-2);
}

.tl-time {
  margin-top: 5px;
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  color: var(--label-3);
  font-variant-numeric: tabular-nums;
  flex-wrap: wrap;
}

.tl-countdown {
  color: var(--ios-orange-dark);
}

.tl-contact {
  margin: 10px 0 0;
  padding-top: 10px;
  border-top: 1px dashed var(--separator);
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  color: var(--label-3);
}

/* 紧凑模式：压缩行高，只保留标题 / 状态 / 时间，说明需手动展开 */
.compact {
  padding: 12px 16px 8px;
}

.compact .tl-item {
  padding-bottom: 6px;
}

.compact .tl-dot {
  width: 24px;
  height: 22px;
  font-size: 11px;
  border-radius: 11px;
}

.compact .tl-item::before {
  left: 11px;
  top: 22px;
}

.compact .tl-title {
  font-size: 13px;
  line-height: 22px;
}

.compact .tl-time {
  margin-top: 1px;
  font-size: 11px;
}

.compact .tl-list {
  margin-top: 10px;
}

@media (max-width: 700px) {
  .tl-desc {
    font-size: 11.5px;
  }
}
</style>
