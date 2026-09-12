<template>
  <div class="page-container">
    <el-card class="summary-card">
      <div class="summary-header">
        <div class="header-title">
          <el-icon color="#E6A23C"><Warning /></el-icon>
          <span>库存缺口分析（需求合计仅统计已启用且未核销的方案）</span>
        </div>
        <el-button :loading="loading" @click="loadGaps">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
      <div class="summary-tags">
        <el-tag type="info" effect="plain">配件总数 {{ gaps.length }} 种</el-tag>
        <el-tag type="danger" effect="plain">现存不足 {{ shortageCount }} 种</el-tag>
        <el-tag type="warning" effect="plain">未分配分区 {{ unassignedCount }} 种</el-tag>
        <el-tag type="success" effect="plain">已核销方案 {{ writtenOffPlanCount }} 个（不参与合计）</el-tag>
        <el-tag type="danger" effect="plain">含已删除配件 {{ deletedCount }} 种（不可核销）</el-tag>
      </div>
    </el-card>

    <el-card class="zone-summary-card">
      <div class="summary-header">
        <div class="header-title">
          <el-icon color="#409EFF"><Collection /></el-icon>
          <span>按分区汇总（库房备料用：缺口件数与涉及配件种数）</span>
        </div>
        <el-button type="success" plain :loading="exporting" @click="handleExportZoneSummary">
          <el-icon><Download /></el-icon>
          导出分区汇总
        </el-button>
      </div>
      <el-table
        v-loading="loading"
        :data="zoneSummary"
        border
        stripe
        show-summary
        :summary-method="zoneSummaryMethod"
        :row-class-name="zoneSummaryRowClassName"
        style="width: 100%"
      >
        <el-table-column label="分区名称" min-width="200">
          <template #default="{ row }">
            <el-tag v-if="row.unassignedZone" type="warning" effect="plain">未分配分区</el-tag>
            <el-tag v-else type="primary" effect="light">{{ row.zoneTagName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="shortageAccessoryCount" label="涉及配件种数" min-width="140" align="center" />
        <el-table-column prop="gapQuantityTotal" label="缺口件数" min-width="140" align="center">
          <template #default="{ row }">
            <span :class="{ 'shortage-text': row.gapQuantityTotal > 0 }">{{ row.gapQuantityTotal }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="table-card">
      <el-table
        v-loading="loading"
        :data="gaps"
        border
        stripe
        :row-class-name="rowClassName"
        style="width: 100%"
      >
        <el-table-column prop="accessoryName" label="配件名称" min-width="160">
          <template #default="{ row }">
            {{ row.accessoryName }}
            <el-tag v-if="row.accessoryDeleted" type="danger" size="small" effect="dark">已删除</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="model" label="型号" min-width="140" />
        <el-table-column label="所属分区" min-width="140" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.zoneTagName" type="primary" effect="light">{{ row.zoneTagName }}</el-tag>
            <el-tag v-else type="warning" effect="plain">未分配分区</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="现存量" min-width="110" align="center">
          <template #default="{ row }">
            <span :class="{ 'shortage-text': row.shortage }">{{ row.stockQuantity }}</span>
          </template>
        </el-table-column>
        <el-table-column label="需求合计（启用未核销）" min-width="170" align="center">
          <template #default="{ row }">
            <span v-if="row.accessoryDeleted" class="deleted-hint">仍被方案引用，不参与合计</span>
            <span v-else>{{ row.requiredQuantity }}</span>
          </template>
        </el-table-column>
        <el-table-column label="缺口数量" min-width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.accessoryDeleted" class="deleted-hint">-</span>
            <span v-else :class="{ 'shortage-text': row.shortage }">{{ row.gapQuantity }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="specUnit" label="规格单位" min-width="90" align="center">
          <template #default="{ row }">{{ row.specUnit || '-' }}</template>
        </el-table-column>
      </el-table>

      <!-- 未分配分区单独列出 -->
      <div class="unassigned-section">
        <div class="section-title">未分配分区（{{ unassignedCount }}）</div>
        <el-empty
          v-if="unassignedGaps.length === 0"
          description="所有配件均已分配分区"
          :image-size="60"
        />
        <el-table v-else :data="unassignedGaps" border size="small" style="width: 100%">
          <el-table-column prop="accessoryName" label="配件名称" min-width="160">
            <template #default="{ row }">
              {{ row.accessoryName }}
              <el-tag v-if="row.accessoryDeleted" type="danger" size="small" effect="dark">已删除</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="model" label="型号" min-width="140" />
          <el-table-column label="现存量" width="100" align="center">
            <template #default="{ row }">
              <span :class="{ 'shortage-text': row.shortage }">{{ row.stockQuantity }}</span>
            </template>
          </el-table-column>
          <el-table-column label="需求合计" width="100" align="center">
            <template #default="{ row }">{{ row.accessoryDeleted ? '-' : row.requiredQuantity }}</template>
          </el-table-column>
          <el-table-column label="缺口" width="100" align="center">
            <template #default="{ row }">
              <span v-if="row.accessoryDeleted">-</span>
              <span v-else :class="{ 'shortage-text': row.shortage }">{{ row.gapQuantity }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Warning, Refresh, Download, Collection } from '@element-plus/icons-vue'
import {
  getStockGaps,
  getStockGapZoneSummary,
  exportStockGapZoneSummary,
  getWiringPlanPage
} from '@/api/wiringPlan'
import { onStockChanged } from '@/utils/stockSync'
import { resolveExportFileName, triggerBrowserDownload } from '@/utils/csvDownload'

const gaps = ref([])
const zoneSummary = ref([])
const loading = ref(false)
// 导出进行中标记：请求未返回前重复点击直接忽略，避免重复下载
const exporting = ref(false)
const writtenOffPlanCount = ref(0)

const shortageCount = computed(() => gaps.value.filter(item => item.shortage).length)
const unassignedCount = computed(() => gaps.value.filter(item => item.unassignedZone).length)
const deletedCount = computed(() => gaps.value.filter(item => item.accessoryDeleted).length)
const unassignedGaps = computed(() => gaps.value.filter(item => item.unassignedZone))

const rowClassName = ({ row }) => {
  if (row.accessoryDeleted) return 'deleted-row'
  if (row.shortage) return 'shortage-row'
  return ''
}

const zoneSummaryRowClassName = ({ row }) => (row.unassignedZone ? 'unassigned-zone-row' : '')

// 合计行直接对当前分区小计行求和：页面合计与分区小计同源，刷新后两者一致
const zoneSummaryMethod = ({ columns, data }) => {
  return columns.map((column, index) => {
    if (index === 0) return '合计'
    if (column.property === 'shortageAccessoryCount') {
      return String(data.reduce((sum, row) => sum + (row.shortageAccessoryCount || 0), 0))
    }
    if (column.property === 'gapQuantityTotal') {
      return String(data.reduce((sum, row) => sum + (row.gapQuantityTotal || 0), 0))
    }
    return ''
  })
}

const loadWrittenOffCount = async () => {
  // 已核销方案在方案列表中用核销标记展示，这里统计数量用于口径提示
  try {
    const res = await getWiringPlanPage({ pageNum: 1, pageSize: 999 })
    writtenOffPlanCount.value = (res.records || []).filter(plan => plan.writeoff).length
  } catch (e) {
    writtenOffPlanCount.value = 0
  }
}

const loadGaps = async () => {
  loading.value = true
  try {
    // 缺口明细与分区汇总同源拉取，刷新后页面合计、分区小计与导出文件保持一致
    const [gapRows, zoneRows] = await Promise.all([getStockGaps(), getStockGapZoneSummary()])
    gaps.value = gapRows || []
    zoneSummary.value = zoneRows || []
    await loadWrittenOffCount()
  } finally {
    loading.value = false
  }
}

const buildZoneSummaryFallbackFileName = () => {
  const date = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  const datePart = `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}`
  return `库存缺口分区汇总_${datePart}.csv`
}

const handleExportZoneSummary = async () => {
  // 导出请求未完成前忽略重复点击
  if (exporting.value) {
    return
  }
  if (zoneSummary.value.length === 0) {
    ElMessage.warning('暂无分区汇总数据可导出')
    return
  }
  exporting.value = true
  try {
    const response = await exportStockGapZoneSummary()
    const fileName = resolveExportFileName(
      response.headers['content-disposition'],
      buildZoneSummaryFallbackFileName()
    )
    triggerBrowserDownload(response.data, fileName)
    ElMessage.success('导出成功')
  } catch (e) {
    // 错误提示已由请求拦截器统一展示
  } finally {
    exporting.value = false
  }
}

// 配件档案保存现存量、分区调整或方案核销后，立即按新现存量重算缺口列表数字，
// 不停留在保存前的需求合计/缺口上
const handleStockChanged = () => {
  loadGaps()
}

// bfcache 恢复（浏览器前进/后退）时组件不会重新挂载，主动重拉保证与真实库存一致
const handlePageShow = (event) => {
  if (event.persisted) {
    loadGaps()
  }
}

let unsubscribeStockChanged = null

onMounted(() => {
  loadGaps()
  unsubscribeStockChanged = onStockChanged(handleStockChanged)
  window.addEventListener('pageshow', handlePageShow)
})

onBeforeUnmount(() => {
  unsubscribeStockChanged?.()
  window.removeEventListener('pageshow', handlePageShow)
})
</script>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.summary-card,
.zone-summary-card,
.table-card {
  border-radius: 8px;
}

.summary-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.summary-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

/* 现存量不足：缺口数字标红加粗 */
.shortage-text {
  color: #f56c6c;
  font-weight: 700;
}

.deleted-hint {
  color: #909399;
  font-size: 12px;
}

:deep(.shortage-row) {
  background-color: #fef0f0;
}

:deep(.deleted-row) {
  color: #909399;
  background-color: #f4f4f5;
}

/* 分区汇总中未分配分区单独一行，浅色底纹与“未分配分区”标签色调一致 */
:deep(.unassigned-zone-row) {
  background-color: #fdf6ec;
}

.unassigned-section {
  margin-top: 20px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #e6a23c;
  margin-bottom: 12px;
}
</style>
