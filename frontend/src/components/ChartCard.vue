/**
 * 通用 ECharts 图表卡片组件。
 * 自动处理窗口缩放时的图表 resize。
 *
 * @author Claude
 * @date 2026-06-15
 */
<template>
  <div class="chart-card">
    <div v-if="title" class="chart-card-title">{{ title }}</div>
    <div ref="chartRef" class="chart-card-body" :style="{ height: height + 'px' }"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  title: { type: String, default: '' },
  height: { type: Number, default: 320 },
  option: { type: Object, required: true }
})

const chartRef = ref(null)
let chartInstance = null

function initChart() {
  if (!chartRef.value) return
  if (chartInstance) chartInstance.dispose()
  chartInstance = echarts.init(chartRef.value)
  chartInstance.setOption(props.option)
}

function onResize() {
  chartInstance?.resize()
}

onMounted(() => {
  nextTick(initChart)
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  chartInstance?.dispose()
})

watch(() => props.option, () => {
  if (chartInstance) {
    chartInstance.setOption(props.option, true)
  }
}, { deep: true })
</script>

<style scoped>
.chart-card {
  background: var(--content-bg);
  border-radius: 4px;
  padding: 16px;
  box-shadow: var(--shadow-light);
  margin-bottom: 16px;
}
.chart-card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  gap: 8px;
}
.chart-card-title::before {
  content: '';
  display: inline-block;
  width: 3px;
  height: 16px;
  background: var(--wms-primary);
  border-radius: 2px;
}
.chart-card-body {
  width: 100%;
}

@media (max-width: 768px) {
  .chart-card {
    padding: 14px;
    margin-bottom: 12px;
  }

  .chart-card-title {
    margin-bottom: 10px;
  }

  .chart-card-body {
    min-height: 240px;
  }
}
</style>
