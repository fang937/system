<template>
  <div class="coach-feedback" :class="{ compact }">
    <div class="fb-head">
      <div class="fb-score">
        <b>{{ score ?? '—' }}</b>
        <span>/100</span>
      </div>
      <div class="fb-level-wrap">
        <div class="fb-level">{{ level }}</div>
        <div class="tiny">本次练习得分</div>
      </div>
      <div class="fb-gap" />
      <slot name="actions" />
    </div>

    <div v-if="dimensions.length" class="dims">
      <div v-for="dim in dimensions" :key="dim.code || dim.name" class="dim">
        <div class="dim-top">
          <span>{{ dim.name }}</span>
          <b>{{ clamp(dim.score) }}</b>
        </div>
        <div class="dim-track">
          <div class="dim-fill" :style="{ width: clamp(dim.score) + '%' }" />
        </div>
        <div v-if="dim.comment" class="dim-comment">{{ dim.comment }}</div>
      </div>
    </div>

    <div class="fb-grid">
      <div class="fb-box good">
        <div class="fb-title">做得好的</div>
        <ul>
          <li v-for="item in feedback?.strengths || []" :key="item">{{ item }}</li>
        </ul>
      </div>
      <div class="fb-box improve">
        <div class="fb-title">可以改进</div>
        <ul>
          <li v-for="item in feedback?.improvements || []" :key="item">{{ item }}</li>
        </ul>
      </div>
    </div>

    <div class="sample">
      <div class="fb-title">参考表达（请替换为你的真实经历）</div>
      <p>{{ feedback?.sampleAnswer }}</p>
    </div>

    <div v-if="feedback?.followUp" class="followup">
      <span>追问：{{ feedback.followUp }}</span>
      <slot name="followup" />
    </div>

    <p v-if="feedback?.disclaimer" class="ai-note">{{ feedback.disclaimer }}</p>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  feedback: { type: Object, default: null },
  compact: { type: Boolean, default: false }
})

const score = computed(() =>
  props.feedback?.score == null ? null : clamp(props.feedback.score)
)
const level = computed(() =>
  props.feedback?.level || (score.value == null ? '—' : levelOf(score.value))
)
const dimensions = computed(() => props.feedback?.dimensions || [])

function clamp(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return 0
  return Math.max(0, Math.min(100, Math.round(number)))
}

function levelOf(value) {
  if (value >= 85) return '优秀'
  if (value >= 75) return '良好'
  if (value >= 60) return '合格'
  return '待提升'
}
</script>

<style scoped>
.coach-feedback {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed var(--separator);
}

.fb-head {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.fb-score {
  display: flex;
  align-items: baseline;
  gap: 3px;
  color: #4b49b8;
  font-variant-numeric: tabular-nums;
}

.fb-score b {
  font-size: 34px;
  line-height: 1;
  font-weight: 700;
  letter-spacing: -0.03em;
}

.fb-score span {
  font-size: 12px;
  color: var(--label-3);
}

.fb-level {
  font-size: 15px;
  font-weight: 650;
  color: var(--label-1);
}

.fb-gap {
  flex: 1;
}

.dims {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 18px;
  margin-top: 14px;
}

.dim-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: var(--label-2);
}

.dim-top b {
  font-size: 12.5px;
  color: #4b49b8;
  font-variant-numeric: tabular-nums;
}

.dim-track {
  margin-top: 5px;
  height: 5px;
  border-radius: 999px;
  background: var(--fill-1);
  overflow: hidden;
}

.dim-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #5856d6, #af52de);
  transition: width 0.4s var(--ease-ios);
}

.dim-comment {
  margin-top: 4px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--label-3);
}

.fb-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 14px;
}

.fb-box {
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--fill-2);
}

.fb-box.good {
  border-left: 3px solid var(--ios-green);
}

.fb-box.improve {
  border-left: 3px solid var(--ios-orange);
}

.fb-title {
  font-size: 12.5px;
  font-weight: 650;
  color: var(--label-2);
}

.fb-box ul {
  margin: 6px 0 0;
  padding-left: 16px;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.fb-box li {
  font-size: 12px;
  line-height: 1.65;
  color: var(--label-1);
}

.sample {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(88, 86, 214, 0.06);
}

.sample p {
  margin: 6px 0 0;
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--label-1);
  white-space: pre-wrap;
  word-break: break-word;
}

.followup {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 10px;
  padding: 9px 11px;
  border-radius: 10px;
  background: var(--fill-2);
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--label-2);
}

.compact {
  margin-top: 10px;
  padding-top: 10px;
}

.compact .fb-score b {
  font-size: 26px;
}

.compact .dims {
  grid-template-columns: 1fr;
}

@media (max-width: 620px) {
  .dims,
  .fb-grid {
    grid-template-columns: 1fr;
  }

  .fb-head {
    align-items: flex-start;
  }
}
</style>
