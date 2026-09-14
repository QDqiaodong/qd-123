<template>
  <div class="page-container">
    <el-card class="summary-card">
      <div class="summary-header">
        <div class="header-title">
          <el-icon color="#F56C6C"><Box /></el-icon>
          <span>安全库存台账（现存量低于安全库存下限的配件，供采购按分区领料补货）</span>
        </div>
        <el-button :loading="loading" @click="loadShortages">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
      <div class="summary-tags">
        <el-tag type="danger" effect="plain">{{ filterScopeText }} {{ shortages.length }} 种</el-tag>
        <el-tag v-if="!isUnassignedFilter" type="warning" effect="plain">
          其中未分配分区 {{ unassignedCount }} 种
        </el-tag>
        <el-tag type="danger" effect="dark">紧急 {{ urgentCount }} 种（缺口≥下限一半）</el-tag>
        <el-tag type="info" effect="plain">缺口合计 {{ totalGap }} 件</el-tag>
        <el-tag type="info" effect="plain">组内按缺口从大到小排</el-tag>
        <el-tag type="info" effect="plain">未设下限的配件不进台账</el-tag>
      </div>
    </el-card>

    <el-card class="table-card">
      <div class="table-header">
        <div class="header-title">
          <el-icon color="#409EFF"><List /></el-icon>
          <span>低于下限明细</span>
        </div>
        <div class="table-actions">
          <el-select
            v-model="selectedZone"
            class="zone-filter"
            @change="handleZoneChange"
          >
            <el-option label="全部分区" :value="ZONE_ALL" />
            <el-option
              v-for="zone in assignedZoneOptions"
              :key="zone.zoneTagId"
              :label="zone.zoneTagName"
              :value="String(zone.zoneTagId)"
            />
            <el-option label="未分配分区" :value="ZONE_UNASSIGNED" />
          </el-select>
          <el-button type="success" plain :loading="exporting" @click="handleExport">
            <el-icon><Download /></el-icon>
            导出当前分区
          </el-button>
        </div>
      </div>

      <!-- 勾选低位配件后按分区汇总缺口件数，确认即生成补货单（待提交草稿） -->
      <div class="replenish-bar">
        <div class="replenish-info">
          已勾选 <span class="replenish-count">{{ selectedRows.length }}</span> 种，
          缺口合计 <span class="replenish-count">{{ selectedGapTotal }}</span> 件
          <span class="replenish-zone-breakdown">
            <span v-for="zone in selectedZoneSummaries" :key="zone.key" class="zone-chip">
              {{ zone.zoneName }} {{ zone.totalQuantity }} 件
            </span>
          </span>
        </div>
        <div class="replenish-actions">
          <el-button :disabled="selectedRows.length === 0" @click="clearSelection">清空勾选</el-button>
          <el-button
            type="primary"
            :disabled="selectedRows.length === 0"
            :loading="creating"
            @click="openCreateDialog"
          >
            <el-icon><ShoppingCart /></el-icon>
            生成补货单
          </el-button>
        </div>
      </div>

      <el-table
        v-if="!isUnassignedFilter"
        ref="assignedTableRef"
        v-loading="loading"
        :data="assignedShortages"
        border
        stripe
        class="assigned-table"
        :row-class-name="assignedRowClassName"
        :row-key="getRowKey"
        @selection-change="handleAssignedSelectionChange"
        style="width: 100%"
      >
        <el-table-column type="selection" width="46" :selectable="isRowSelectable" reserve-selection />
        <el-table-column prop="accessoryName" label="名称" min-width="160">
          <template #default="{ row }">
            {{ row.accessoryName }}
            <el-tag v-if="row.replenishPending" type="warning" size="small" effect="plain">
              补货中 {{ row.replenishOrderNo }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="model" label="型号" min-width="140" />
        <el-table-column label="分区" min-width="140" align="center">
          <template #default="{ row }">
            <el-tag type="primary" effect="light">{{ row.zoneTagName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="stockQuantity" label="现存量" min-width="100" align="center">
          <template #default="{ row }">
            <span class="shortage-text">{{ row.stockQuantity }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="safetyStock" label="下限" min-width="100" align="center" />
        <el-table-column label="缺口" min-width="130" align="center">
          <template #default="{ row }">
            <span class="gap-cell">
              <span class="shortage-text">{{ row.gapQuantity }}</span>
              <el-tag v-if="row.urgent" type="danger" size="small" effect="dark">紧急</el-tag>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="规格单位" min-width="90" align="center">
          <template #default="{ row }">{{ row.specUnit || '-' }}</template>
        </el-table-column>
        <template #empty>{{ assignedEmptyText }}</template>
      </el-table>

      <!-- 未分配分区可单独筛出：全部分区时单独成组，只看未分配分区时作为主表展示，低位也不能漏 -->
      <div v-if="!isAssignedZoneFilter" class="unassigned-section" :class="{ 'unassigned-only': isUnassignedFilter }">
        <div class="section-title">
          未分配分区（{{ isUnassignedFilter ? shortages.length : unassignedShortages.length }}）
        </div>
        <el-empty
          v-if="unassignedShortages.length === 0"
          description="未分配分区暂无低于下限的配件"
          :image-size="60"
        />
        <el-table
          v-else
          ref="unassignedTableRef"
          v-loading="loading"
          :data="unassignedShortages"
          border
          size="small"
          :row-class-name="unassignedRowClassName"
          :row-key="getRowKey"
          @selection-change="handleUnassignedSelectionChange"
          style="width: 100%"
        >
          <el-table-column type="selection" width="46" :selectable="isRowSelectable" reserve-selection />
          <el-table-column prop="accessoryName" label="名称" min-width="160">
            <template #default="{ row }">
              {{ row.accessoryName }}
              <el-tag v-if="row.replenishPending" type="warning" size="small" effect="plain">
                补货中 {{ row.replenishOrderNo }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="model" label="型号" min-width="140" />
          <el-table-column label="分区" width="120" align="center">
            <template #default>
              <el-tag type="warning" effect="plain">未分配</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="stockQuantity" label="现存量" width="100" align="center">
            <template #default="{ row }">
              <span class="shortage-text">{{ row.stockQuantity }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="safetyStock" label="下限" width="100" align="center" />
          <el-table-column label="缺口" width="130" align="center">
            <template #default="{ row }">
              <span class="gap-cell">
                <span class="shortage-text">{{ row.gapQuantity }}</span>
                <el-tag v-if="row.urgent" type="danger" size="small" effect="dark">紧急</el-tag>
              </span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- 生成补货单确认框：按分区汇总缺口件数，可逐行微调补货数量（不超过缺口） -->
    <el-dialog v-model="createDialogVisible" title="生成补货单" width="720px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="create-alert"
        title="生成的是待提交补货单草稿，可在草稿中继续调整数量；提交后配件档案才会回填补货单号与待补数量。已在补货中的配件不可重复勾选。"
      />
      <div class="dialog-zone-summary">
        <el-tag
          v-for="zone in dialogZoneSummaries"
          :key="zone.key"
          :type="zone.unassignedZone ? 'warning' : 'primary'"
          effect="light"
        >
          {{ zone.zoneName }}：{{ zone.accessoryCount }} 种 / {{ zone.totalQuantity }} 件
        </el-tag>
        <el-tag type="danger" effect="dark">合计 {{ dialogRows.length }} 种 / {{ dialogQuantityTotal }} 件</el-tag>
      </div>
      <el-table :data="dialogRows" border size="small" max-height="340">
        <el-table-column prop="accessoryName" label="名称" min-width="140" />
        <el-table-column label="分区" min-width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.unassignedZone" type="warning" effect="plain" size="small">未分配</el-tag>
            <el-tag v-else type="primary" effect="light" size="small">{{ row.zoneTagName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="gapQuantity" label="缺口" width="80" align="center" />
        <el-table-column label="补货数量" width="150" align="center">
          <template #default="{ row }">
            <el-input-number
              v-model="dialogQuantityMap[row.accessoryId]"
              :min="1"
              :max="row.gapQuantity"
              :precision="0"
              :step="1"
              controls-position="right"
              size="small"
              style="width: 130px"
            />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreateOrder">生成补货单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Box, Refresh, List, Download, ShoppingCart } from '@element-plus/icons-vue'
import { getSafetyStockShortages, exportSafetyStockShortages } from '@/api/accessory'
import { createReplenishOrder } from '@/api/replenishOrder'
import { onStockChanged } from '@/utils/stockSync'
import { resolveExportFileName, triggerBrowserDownload } from '@/utils/csvDownload'

const router = useRouter()

// 分区筛选下拉值：'__all__' 全集；'__unassigned__' 未分配分区；其余为分区 ID 字符串
const ZONE_ALL = '__all__'
const ZONE_UNASSIGNED = '__unassigned__'

const shortages = ref([])
const loading = ref(false)
// 导出进行中标记：请求未返回前重复点击直接忽略，避免重复下载
const exporting = ref(false)
const selectedZone = ref(ZONE_ALL)
// 已在台账中出现过的分区缓存：指定分区筛选后若该分区已无低位件，
// 下拉与统计标签仍能显示分区名而不是“当前分区”
const knownZoneMap = ref(new Map())

// 勾选生成补货单：两张表（已分配/未分配）的选中行按 accessoryId 合并；
// reserve-selection + row-key 让翻筛选/刷新时未被新数据覆盖的勾选尽量保留
const assignedTableRef = ref()
const unassignedTableRef = ref()
const selectedAssigned = ref([])
const selectedUnassigned = ref([])
const creating = ref(false)
const createDialogVisible = ref(false)
const dialogQuantityMap = reactive({})

const getRowKey = (row) => row.accessoryId

// 已在补货中的配件（档案已有待补标记）不可重复勾选
const isRowSelectable = (row) => !row.replenishPending

const handleAssignedSelectionChange = (rows) => {
  selectedAssigned.value = rows
}

const handleUnassignedSelectionChange = (rows) => {
  selectedUnassigned.value = rows
}

const selectedRows = computed(() => [...selectedAssigned.value, ...selectedUnassigned.value])

const selectedGapTotal = computed(() =>
  selectedRows.value.reduce((sum, item) => sum + (item.gapQuantity || 0), 0)
)

// 选中配件按分区分组汇总缺口件数（未分配分区殿后，后端同口径排序），供仓管按分区领料
const summarizeZones = (rows, quantityOf) => {
  const map = new Map()
  for (const row of rows) {
    const key = row.unassignedZone ? '__unassigned__' : row.zoneTagId
    if (!map.has(key)) {
      map.set(key, {
        key,
        zoneName: row.unassignedZone ? '未分配分区' : row.zoneTagName,
        unassignedZone: !!row.unassignedZone,
        accessoryCount: 0,
        totalQuantity: 0
      })
    }
    const summary = map.get(key)
    summary.accessoryCount += 1
    summary.totalQuantity += quantityOf(row)
  }
  return Array.from(map.values()).sort((a, b) =>
    a.unassignedZone === b.unassignedZone ? 0 : a.unassignedZone ? 1 : -1
  )
}

const selectedZoneSummaries = computed(() =>
  summarizeZones(selectedRows.value, (row) => row.gapQuantity || 0)
)

const dialogRows = computed(() => selectedRows.value)

const dialogZoneSummaries = computed(() =>
  summarizeZones(dialogRows.value, (row) => dialogQuantityMap[row.accessoryId] || 0)
)

const dialogQuantityTotal = computed(() =>
  dialogRows.value.reduce((sum, row) => sum + (dialogQuantityMap[row.accessoryId] || 0), 0)
)

const clearSelection = () => {
  assignedTableRef.value?.clearSelection()
  unassignedTableRef.value?.clearSelection()
  selectedAssigned.value = []
  selectedUnassigned.value = []
}

const openCreateDialog = () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先勾选低于下限的配件')
    return
  }
  // 补货数量默认等于缺口，仓管可在确认框内按分区汇总核对后微调（不超过缺口）
  dialogRows.value.forEach((row) => {
    dialogQuantityMap[row.accessoryId] = row.gapQuantity
  })
  createDialogVisible.value = true
}

const handleCreateOrder = async () => {
  const items = dialogRows.value.map((row) => ({
    accessoryId: row.accessoryId,
    replenishQuantity: dialogQuantityMap[row.accessoryId]
  }))
  if (items.some((item) => !item.replenishQuantity || item.replenishQuantity < 1)) {
    ElMessage.warning('每种配件的补货数量都必须是正整数')
    return
  }
  creating.value = true
  try {
    const newId = await createReplenishOrder(items)
    ElMessage.success('补货单已生成（待提交），请在补货单中核对并提交')
    createDialogVisible.value = false
    clearSelection()
    // 草稿不占用待补标记，但直接进入补货单页继续调整/提交，减少来回切换
    router.push({ path: '/replenish-order', query: { open: newId } })
  } finally {
    creating.value = false
  }
}

const isUnassignedFilter = computed(() => selectedZone.value === ZONE_UNASSIGNED)
const isAssignedZoneFilter = computed(() =>
  selectedZone.value !== ZONE_ALL && selectedZone.value !== ZONE_UNASSIGNED
)

const assignedShortages = computed(() =>
  shortages.value.filter(item => !item.unassignedZone)
)
const unassignedShortages = computed(() =>
  shortages.value.filter(item => item.unassignedZone)
)
const unassignedCount = computed(() => unassignedShortages.value.length)

// 紧急件数直接对当前筛选台账行计数：换分区或改下限刷新后，urgent 由后端随新缺口实时给出，
// 统计与每行“紧急”标记、排序同源，不会出现标签与顺序不一致
const urgentCount = computed(() =>
  shortages.value.filter(item => item.urgent).length
)

// 紧急行加深红底纹（缺口达到下限一半及以上），普通低位行保持浅红；未分配紧急行加深橙底
const assignedRowClassName = ({ row }) => (row.urgent ? 'urgent-row' : 'shortage-row')
const unassignedRowClassName = ({ row }) => (row.urgent ? 'urgent-row' : 'unassigned-row')

// 分区下拉的已分配选项由“全集台账见过的分区 + 当前台账行的分区”合并去重，
// 后端按分区排序、未分配殿后，合并时保留该顺序；未分配分区作为固定选项始终可选
const assignedZoneOptions = computed(() => {
  const ordered = []
  const seen = new Set()
  const pushZone = (zoneTagId, zoneTagName) => {
    if (zoneTagId == null || seen.has(zoneTagId)) return
    seen.add(zoneTagId)
    ordered.push({ zoneTagId, zoneTagName: zoneTagName || knownZoneMap.value.get(zoneTagId) || `分区${zoneTagId}` })
  }
  for (const item of shortages.value) {
    if (!item.unassignedZone) {
      pushZone(item.zoneTagId, item.zoneTagName)
    }
  }
  for (const [zoneTagId, zoneTagName] of knownZoneMap.value) {
    pushZone(zoneTagId, zoneTagName)
  }
  return ordered
})

// 缺口合计直接对当前筛到的台账行求和，与后端口径一致（每行缺口 = 下限 - 现存量）
const totalGap = computed(() =>
  shortages.value.reduce((sum, item) => sum + (item.gapQuantity || 0), 0)
)

const filterScopeText = computed(() => {
  if (isUnassignedFilter.value) return '未分配分区待补货'
  if (isAssignedZoneFilter.value) {
    const zone = assignedZoneOptions.value.find(z => String(z.zoneTagId) === selectedZone.value)
    return `${zone ? zone.zoneTagName : '当前分区'}待补货`
  }
  return '待补货'
})

const assignedEmptyText = computed(() =>
  isAssignedZoneFilter.value ? '该分区暂无低于下限的配件' : '已分配分区暂无低于下限的配件'
)

// 当前筛选对应的查询/导出参数：页面与导出同源，保证导出行数与缺口跟页面一致
const buildQueryParams = () => {
  if (isUnassignedFilter.value) {
    return { unassignedZone: true }
  }
  if (isAssignedZoneFilter.value) {
    return { zoneTagId: Number(selectedZone.value) }
  }
  return {}
}

const loadShortages = async () => {
  loading.value = true
  try {
    // 台账实时取自配件档案：改下限或现存量、换分区后刷新，条数与缺口都与档案对得上
    const rows = (await getSafetyStockShortages(buildQueryParams())) || []
    shortages.value = rows
    // 全集加载时把分区名记入缓存，供筛选后分区无低位件时仍能稳定显示分区名
    if (selectedZone.value === ZONE_ALL) {
      for (const item of rows) {
        if (!item.unassignedZone && item.zoneTagId != null) {
          knownZoneMap.value.set(item.zoneTagId, item.zoneTagName)
        }
      }
    }
  } finally {
    loading.value = false
  }
}

// 换分区即按新筛选重拉；筛选同时用于页面与导出，不需要客户端再过滤
const handleZoneChange = () => {
  loadShortages()
}

const buildFallbackFileName = () => {
  const date = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  const datePart = `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}`
  return `安全库存台账_${datePart}.csv`
}

const handleExport = async () => {
  // 导出请求未完成前忽略重复点击
  if (exporting.value) {
    return
  }
  if (shortages.value.length === 0) {
    ElMessage.warning('当前筛选暂无台账数据可导出')
    return
  }
  exporting.value = true
  try {
    // 与页面台账同一接口口径、同一套分区筛选参数：
    // 换分区或改下限后重新导出，行数与每行缺口都与当前页面一致
    const response = await exportSafetyStockShortages(buildQueryParams())
    const fileName = resolveExportFileName(
      response.headers['content-disposition'],
      buildFallbackFileName()
    )
    triggerBrowserDownload(response.data, fileName)
    ElMessage.success('导出成功')
  } catch (e) {
    // 错误提示已由请求拦截器统一展示
  } finally {
    exporting.value = false
  }
}

// 配件档案保存下限/现存量、分区调整、方案核销或盘点回写后，立即按当前筛选重算台账
const handleStockChanged = () => {
  loadShortages()
}

// bfcache 恢复时主动重拉，避免停留在旧台账
const handlePageShow = (event) => {
  if (event.persisted) {
    loadShortages()
  }
}

let unsubscribeStockChanged = null

onMounted(() => {
  loadShortages()
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
.table-card {
  border-radius: 8px;
}

.summary-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.table-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.zone-filter {
  width: 200px;
}

.replenish-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
  padding: 10px 14px;
  background: #f4f8ff;
  border: 1px solid #d9e6ff;
  border-radius: 6px;
}

.replenish-info {
  font-size: 13px;
  color: #303133;
}

.replenish-count {
  color: #f56c6c;
  font-weight: 700;
}

.replenish-zone-breakdown {
  margin-left: 12px;
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
}

.zone-chip {
  font-size: 12px;
  color: #409eff;
  background: #ecf5ff;
  border-radius: 4px;
  padding: 1px 8px;
}

.create-alert {
  margin-bottom: 14px;
}

.dialog-zone-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
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

.shortage-text {
  color: #f56c6c;
  font-weight: 700;
}

/* 缺口数字与“紧急”标签同一行展示 */
.gap-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

:deep(.shortage-row) {
  background-color: #fef0f0;
}

/* 紧急（缺口达下限一半及以上）：更深的红底，避免急件混在普通低位行里被漏看 */
:deep(.urgent-row) {
  background-color: #fde2e2;
}

:deep(.unassigned-row) {
  background-color: #fdf6ec;
}

.unassigned-section {
  margin-top: 20px;
}

/* 只看未分配分区时它就是主表，不再保留与上方明细的大间距 */
.unassigned-only {
  margin-top: 0;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #e6a23c;
  margin-bottom: 12px;
}
</style>
