## 一、统计概览卡片

### 数据来源

```
Dashboard.vue → loadData()
  │
  ├── getStockReport({}) → 获取全部物料的库存水位报告
  │
  └── 统计计算:
        ├── totalSku        = stockData.length
        ├── deadStockCount  = filter(ruleEvaluation === 'DEAD_STOCK').length
        └── highRiskCount   = filter(ruleEvaluation === 'LOW_STOCK').length
```

### 前端渲染

```
Dashboard.vue 统计行 [行10-28]
  │
  ├── stat-item: 总物料 SKU 数  (蓝色数字)
  ├── stat-item stat-warn: 呆滞物料 (橙色)
  ├── stat-item stat-danger: 断供高风险物料 (红色)
  └── stat-tip: 最后更新时间 + 刷新按钮 + 打印看板
```

---
## 二、库存水位分布饼图

### 数据构建

```
Dashboard.vue → computed: levelPieOption [行201-218]
  │
  ├── 统计 stockData 中各 ruleEvaluation 的数量
  │     NORMAL:    绿色
  │     LOW_STOCK: 红色（超低储）
  │     HIGH:      橙色（超高储）
  │     DEAD_STOCK: 灰色（滞销）
  │
  └── ECharts 配置:
        type: 'pie'
        radius: ['45%', '75%']  → 环形图
        center: ['50%', '55%']
        label: { show: true, formatter: '{b}\n{d}%' }
```

### 渲染组件

```
Dashboard.vue → ChartCard [行38]
  │
  ├── <ChartCard title="库存水位分布" :height="260" :option="levelPieOption" />
  │
  ▼
ChartCard.vue
  ├── props: title, height, option
  ├── 初始化: echarts.init(chartRef)
  ├── 响应式: watch(option) → chart.setOption(option)
  ├── 自适应: new ResizeObserver(() => chart.resize())
  └── 销毁: onUnmounted → chart.dispose()
```

---
## 三、库存量 Top 10 柱状图

### 数据构建

```
Dashboard.vue → computed: stockBarOption [行220-266]
  │
  ├── stockData 按 stockQty 降序 → 取前 10
  ├── 横轴: 物料编码（materialCode）
  ├── 纵轴: 当前库存（stockQty）
  │
  └── ECharts 配置:
        type: 'bar'
        xAxis: { type: 'category', data: [...物料编码] }
        yAxis: { type: 'value', name: '库存量(件)' }
        series: [{ name: '当前库存', color 按水位分级 }]
```

### 水位颜色分级

```
bar 颜色 = ruleEvaluation 映射:
  NORMAL     → #67c23a (绿色)
  LOW_STOCK  → #f56c6c (红色)
  HIGH       → #e6a23c (橙色)
  DEAD_STOCK → #909399 (灰色)
```

### 渲染组件

```
Dashboard.vue → ChartCard [行42]
  │
  └── <ChartCard title="库存量 Top 10" :height="260" :option="stockBarOption" />
```

---
## 四、ChartCard 通用图表卡片组件

### 组件结构

```
ChartCard.vue
  │
  ├── props:
  │     ├── title: string     (卡片标题)
  │     ├── height: number    (图表高度, 默认 300)
  │     └── option: object    (ECharts 配置)
  │
  ├── template:
  │     ├── div.chart-card-header → {{ title }}
  │     └── div.chart-card-body
  │           └── div(ref="chartRef") ← ECharts 实例挂载点
  │
  ├── 初始化:
  │     └── onMounted → chart = echarts.init(chartRef.value)
  │                     chart.setOption(props.option)
  │
  ├── 响应式更新:
  │     └── watch(() => props.option) → chart.setOption(newOption)
  │
  ├── 窗口自适应:
  │     └── new ResizeObserver(() => chart.resize())
  │           .observe(chartContainer)
  │
  └── 销毁:
        └── onUnmounted → chart.dispose()
```

### 可复用性

ChartCard 是通用组件，可在任何页面引入使用：

```
import ChartCard from '@/components/ChartCard.vue'
<ChartCard title="任意标题" :height="300" :option="echartOption" />
```

---
## 五、库存报表页（StockReport.vue）

### 数据获取

```
StockReport.vue → mounted → loadData()
  │
  ├── getStockReport({}) → 获取库存报表数据
  │
  └── 表格展示 [行115]:
        ├── 物料号 + 物料名称
        ├── 当前库存 (stockQty)
        ├── 安全水位 (minStockDays / maxStockDays)
        ├── 评估结果 (ruleEvaluation): 正常/超低储/超高储/滞销
        └── AI 建议
```

### 图表可视化

```
StockReport.vue → 图表区域 [行111]
  │
  └── 库存变化趋势图（使用 ChartCard 组件）
```

### 数据导出

```
StockReport.vue → 导出按钮
  │
  └── utils/export.js → 调用导出逻辑
        ├── 将表格数据转为 CSV 格式
        └── 触发浏览器下载
```

---
## 六、实时告警轮询

### 机制

```
Dashboard.vue → onMounted → startAlertPolling() [行296-318]
  │
  ├── setInterval(60000ms)
  │     └── getStockReport({}) → 检测新增 LOW_STOCK 物料
  │
  └── 新告警 → ElNotification({ type: 'warning' })
        └── 消息: "以下物料触发超低储预警：M_PART_001, M_PART_002"

组件销毁时 onUnmounted → clearInterval(alertTimer)
```
---
### Dashboard.vue 图表初始化流程

```
Dashboard.vue [行201-266]
  │
  ├── 导入: ChartCard 组件
  │
  ├── computed: levelPieOption
  │     └── 从 stockData 计算各水位计数 → 构建 pie 配置
  │
  ├── computed: stockBarOption
  │     └── 从 stockData 取 Top 10 → 构建 bar 配置
  │
  └── template:
        <ChartCard title="库存水位分布" :option="levelPieOption" />
        <ChartCard title="库存量 Top 10" :option="stockBarOption" />
```

### 图表数据刷新

| 触发时机 | 方法 |
|----------|------|
| 页面加载 | `onMounted → loadData()` |
| 手动刷新 | 刷新按钮 → `loadData()` |
| 扫码出入库 | `onBarcodeScanned() → loadData()` |
| 定时告警 | `setInterval(60000ms) → getStockReport()` |

每次 `loadData()` 更新 `stockData` 后，两个 computed 属性（levelPieOption / stockBarOption）自动重新计算 → `watch(option)` 触发 ChartCard 刷新 ECharts 实例。
