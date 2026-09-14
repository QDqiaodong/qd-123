<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="分区">
          <el-select
            v-model="searchForm.zoneTagId"
            placeholder="全部分区"
            clearable
            style="width: 200px"
            @change="handleSearch"
          >
            <el-option
              v-for="item in zoneTagList"
              :key="item.id"
              :label="item.tagName"
              :value="item.id"
            />
            <el-option :label="UNASSIGNED_LABEL" :value="UNASSIGNED_FILTER_VALUE" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="searchForm.status"
            placeholder="全部状态"
            clearable
            style="width: 160px"
            @change="handleStatusChange"
          >
            <el-option label="待确认" :value="0" />
            <el-option label="已确认" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="差异说明">
          <el-select
            v-model="searchForm.hasRemark"
            placeholder="全部"
            clearable
            style="width: 150px"
            @change="handleRemarkFilterChange"
          >
            <el-option label="有说明" :value="true" />
            <el-option label="无说明" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <div class="table-header">
        <div class="header-title">
          <el-icon color="#409EFF"><DocumentChecked /></el-icon>
          <span>分区盘点单</span>
        </div>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          按分区开盘
        </el-button>
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="rule-alert"
        title="盘点单只登记实盘数并计算与账面现存量的差异，待确认期间不改动库存；确认后一次性回写档案现存量，单据随即锁定不可再改。已删除配件只展示不回写。"
      />

      <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="checkNo" label="盘点单号" min-width="180" />
        <el-table-column label="盘点分区" min-width="150" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.unassignedZone" type="warning" effect="plain">未分配分区</el-tag>
            <el-tag v-else type="primary" effect="light">{{ row.zoneName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'" effect="plain">
              {{ row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="明细/已登记" min-width="120" align="center">
          <template #default="{ row }">
            {{ row.itemCount }} 种 / {{ row.recordedCount || 0 }} 种
          </template>
        </el-table-column>
        <el-table-column label="差异种数" min-width="100" align="center">
          <template #default="{ row }">
            <span :class="{ 'diff-text': row.diffCount > 0 }">{{ row.diffCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="开盘时间" min-width="170" align="center" />
        <el-table-column prop="confirmTime" label="确认时间" min-width="170" align="center">
          <template #default="{ row }">{{ row.confirmTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="差异说明" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.confirmRemark" class="remark-text">{{ row.confirmRemark }}</span>
            <span v-else-if="row.status === 1" class="remark-empty">未填写</span>
            <span v-else class="remark-pending">待确认后填写</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">
              {{ row.status === 1 ? '查看' : '登记/查看' }}
            </el-button>
            <el-button
              v-if="row.status === 0"
              link
              type="danger"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 开盘对话框：选择分区（含未分配分区），空分区也允许开盘 -->
    <el-dialog v-model="createDialogVisible" title="按分区开盘盘点" width="480px">
      <el-form label-width="100px">
        <el-form-item label="盘点分区" required>
          <el-select v-model="createZoneTagId" style="width: 100%">
            <el-option
              v-for="item in zoneTagList"
              :key="item.id"
              :label="`${item.tagName}（${item.remark || '分区'}）`"
              :value="item.id"
            />
            <el-option label="未分配分区" :value="UNASSIGNED_FILTER_VALUE" />
          </el-select>
          <div class="form-hint">空分区和未分配分区也可以开盘；同一分区同时只允许一张待确认盘点单</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">开盘</el-button>
      </template>
    </el-dialog>

    <!-- 盘点确认对话框：必须填写差异说明才能确认，确认后在同一事务内一次性回写库存并锁单 -->
    <el-dialog v-model="confirmDialogVisible" title="确认盘点回写" width="520px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        class="confirm-alert"
        :title="confirmSummary"
      />
      <el-form label-width="92px" class="confirm-form">
        <el-form-item label="差异说明" required>
          <el-input
            v-model="confirmRemark"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="请填写本次盘点的差异说明（如盘盈盘亏原因、账实一致情况等），不填无法确认"
          />
          <div class="form-hint">差异说明会随盘点单保存，刷新后仍可查看，并支持在列表中按有无说明筛选</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmDialogVisible = false">取消</el-button>
        <el-button
          type="success"
          :loading="confirming"
          :disabled="!confirmRemark.trim()"
          @click="submitConfirm"
        >
          确认并回写库存
        </el-button>
      </template>
    </el-dialog>

    <!-- 盘点单详情抽屉：登记实盘数、实时算差异、确认回写 -->
    <el-drawer
      v-model="detailVisible"
      size="72%"
      :title="detail ? `盘点单 ${detail.header.checkNo}（${detail.header.zoneName}）` : '盘点单详情'"
      destroy-on-close
    >
      <div v-if="detail" v-loading="detailLoading" class="detail-container">
        <el-card class="detail-summary-card">
          <div class="summary-tags">
            <el-tag :type="detail.header.status === 1 ? 'success' : 'warning'" effect="plain">
              {{ detail.header.statusText }}
            </el-tag>
            <el-tag type="info" effect="plain">明细 {{ detail.items.length }} 种（含已删除）</el-tag>
            <el-tag type="info" effect="plain">
              已登记 {{ detail.recordedCount }} / {{ countableItems.length }} 种
            </el-tag>
            <el-tag type="danger" effect="plain">盘亏 {{ detail.lossCount }} 种</el-tag>
            <el-tag type="success" effect="plain">盘盈 {{ detail.gainCount }} 种</el-tag>
            <el-tag type="info" effect="plain">
              差异合计 {{ formatSigned(detail.totalDiffQuantity) }} 件
            </el-tag>
            <el-tag type="danger" effect="plain">
              已删除 {{ deletedItems.length }} 种（只展示不回写）
            </el-tag>
          </div>
          <div class="summary-time">
            开盘时间：{{ detail.header.createTime }}
            <span v-if="detail.header.confirmTime">｜确认时间：{{ detail.header.confirmTime }}</span>
          </div>
        </el-card>

        <el-card class="detail-table-card">
          <el-table :data="detail.items" border stripe :row-class-name="itemRowClassName" style="width: 100%">
            <el-table-column prop="accessoryName" label="配件名称" min-width="160">
              <template #default="{ row }">
                {{ row.accessoryName }}
                <el-tag v-if="row.accessoryDeleted" type="danger" size="small" effect="dark">已删除</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="model" label="型号" min-width="130" />
            <el-table-column label="账面现存量（开盘快照）" min-width="160" align="center">
              <template #default="{ row }">{{ row.bookQuantity }}</template>
            </el-table-column>
            <el-table-column label="实盘数量" min-width="160" align="center">
              <template #default="{ row }">
                <el-input-number
                  v-if="!row.accessoryDeleted && editable"
                  v-model="actualMap[row.id]"
                  :min="0"
                  :precision="0"
                  :step="1"
                  controls-position="right"
                  style="width: 140px"
                  @change="markDirty"
                />
                <span v-else-if="row.accessoryDeleted" class="deleted-hint">已删除，不可登记</span>
                <span v-else>{{ row.actualQuantity == null ? '未登记' : row.actualQuantity }}</span>
              </template>
            </el-table-column>
            <el-table-column label="差异（实盘-账面）" min-width="150" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.accessoryDeleted" type="info" effect="plain" size="small">不回写</el-tag>
                <el-tag v-else-if="actualMap[row.id] == null" type="info" effect="plain" size="small">
                  未登记
                </el-tag>
                <el-tag v-else :type="diffMeta(row).tagType" effect="light" size="small">
                  {{ diffMeta(row).text }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="specUnit" label="规格单位" min-width="90" align="center">
              <template #default="{ row }">{{ row.specUnit || '-' }}</template>
            </el-table-column>
          </el-table>
        </el-card>

        <div v-if="editable" class="detail-footer">
          <div class="footer-hint">
            实盘数登记后不会立即改动库存；全部登记完成并填写差异说明确认后，才按实盘数一次性回写档案现存量，单据锁定不可再改。
          </div>
          <div>
            <el-button :loading="saving" @click="handleSaveDraft">保存登记</el-button>
            <el-button
              type="success"
              :loading="confirming"
              :disabled="hasDirty || unrecordedCount > 0"
              @click="handleConfirm"
            >
              确认并回写库存
            </el-button>
          </div>
        </div>
        <div v-else class="detail-footer readonly-footer">
          <el-text v-if="detail.header.confirmRemark" type="info" class="readonly-remark">
            差异说明：{{ detail.header.confirmRemark }}
          </el-text>
          <el-text v-else type="info" class="readonly-remark">差异说明：未填写</el-text>
          <el-button type="success" plain :loading="exporting" @click="handleExportDiff">
            <el-icon><Download /></el-icon>
            导出差异明细
          </el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, DocumentChecked, Download } from '@element-plus/icons-vue'
import {
  getStockCheckPage,
  getStockCheckById,
  createStockCheck,
  recordStockCheckItems,
  confirmStockCheck,
  deleteStockCheck,
  exportStockCheckDiff
} from '@/api/stockCheck'
import { getZoneTagList } from '@/api/zoneTag'
import { notifyStockChanged, onStockChanged } from '@/utils/stockSync'
import { resolveExportFileName, triggerBrowserDownload } from '@/utils/csvDownload'

const UNASSIGNED_LABEL = '未分配分区'
// 分区筛选中“未分配分区”的前端占位值：后端真实 zoneTagId 为 null
const UNASSIGNED_FILTER_VALUE = 0

const tableData = ref([])
const zoneTagList = ref([])
const loading = ref(false)

const searchForm = reactive({
  zoneTagId: null,
  status: null,
  // 差异说明筛选：true=已确认且有说明，false=已确认但无说明（历史/异常数据），null=不筛
  hasRemark: null
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const createDialogVisible = ref(false)
const createZoneTagId = ref(null)
const creating = ref(false)

const detailVisible = ref(false)
const detail = ref(null)
const detailLoading = ref(false)
const saving = ref(false)
const confirming = ref(false)
// 确认弹窗与差异说明：确认回写前必填，纯空白不能点确认
const confirmDialogVisible = ref(false)
const confirmRemark = ref('')
// 导出进行中标记：请求未返回前重复点击直接忽略，避免重复下载
const exporting = ref(false)
// 抽屉内实盘录入值（itemId -> 数量），未登记为 null
const actualMap = reactive({})
// 是否有尚未“保存登记”的本地修改
const dirty = ref(false)

const editable = computed(() => detail.value?.header?.status === 0)
const countableItems = computed(() =>
  (detail.value?.items || []).filter(item => !item.accessoryDeleted)
)
const deletedItems = computed(() =>
  (detail.value?.items || []).filter(item => item.accessoryDeleted)
)
// 未登记种数：已删除配件无需登记
const unrecordedCount = computed(() =>
  countableItems.value.filter(item => actualMap[item.id] == null).length
)
const hasDirty = computed(() => dirty.value)

// 确认弹窗中的回写口径提示，与接口回写规则保持同源展示
const confirmSummary = computed(() => {
  if (!detail.value) return ''
  return `确认后将按实盘数一次性回写档案现存量（盘盈 ${detail.value.gainCount} 种、盘亏 ${detail.value.lossCount} 种，差异合计 ${formatSigned(detail.value.totalDiffQuantity)} 件），本盘点单将锁定不可再修改。`
})

const markDirty = () => {
  dirty.value = true
}

const formatSigned = (value) => {
  if (value == null) return '0'
  return value > 0 ? `+${value}` : String(value)
}

const diffMeta = (row) => {
  const actual = actualMap[row.id]
  if (actual == null) {
    return { tagType: 'info', text: '未登记' }
  }
  const diff = actual - row.bookQuantity
  if (diff > 0) return { tagType: 'success', text: `盘盈 +${diff}` }
  if (diff < 0) return { tagType: 'danger', text: `盘亏 ${diff}` }
  return { tagType: 'info', text: '一致 0' }
}

const itemRowClassName = ({ row }) => {
  if (row.accessoryDeleted) return 'deleted-row'
  const actual = actualMap[row.id]
  if (actual == null) return ''
  if (actual !== row.bookQuantity) return 'diff-row'
  return ''
}

const loadData = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      status: searchForm.status
    }
    // 差异说明筛选仅对已确认单有意义；已选定后后端强制按已确认单过滤
    if (searchForm.hasRemark != null) {
      params.hasRemark = searchForm.hasRemark
    }
    // 占位值 0 表示“未分配分区”，后端按 null 查询；普通选择传真实分区ID
    if (searchForm.zoneTagId === UNASSIGNED_FILTER_VALUE) {
      params.unassigned = true
    } else if (searchForm.zoneTagId != null) {
      params.zoneTagId = searchForm.zoneTagId
    }
    const res = await getStockCheckPage(params)
    tableData.value = res.records
    pagination.total = res.total
    // 越界兜底：total 还有数据但当前页为空（页码超出总页数，如翻到后页改条数、删完本页单据），
    // 不能把空表当成“没有单”，回到第一页按原分区/状态筛选重拉
    if (res.records.length === 0 && res.total > 0 && pagination.pageNum > 1) {
      pagination.pageNum = 1
      await loadData()
    }
  } finally {
    loading.value = false
  }
}

const loadZoneTagList = async () => {
  zoneTagList.value = await getZoneTagList()
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

// “有无差异说明”只针对已确认单：选择该筛选时把状态同步为已确认，
// 避免界面选了“待确认”却由后端强制按已确认查询造成口径困惑
const handleRemarkFilterChange = () => {
  if (searchForm.hasRemark != null) {
    searchForm.status = 1
  }
  handleSearch()
}

// 切回“待确认/全部状态”时差异说明筛选不再适用，自动清掉（以 change 回传值为准）
const handleStatusChange = (value) => {
  if (value !== 1 && searchForm.hasRemark != null) {
    searchForm.hasRemark = null
  }
  handleSearch()
}
const handleReset = () => {
  searchForm.zoneTagId = null
  searchForm.status = null
  searchForm.hasRemark = null
  pagination.pageNum = 1
  loadData()
}

const handleSizeChange = (size) => {
  pagination.pageSize = size
  // 改每页条数后必须回到第一页：否则停留在后页时新页码可能超出总页数，拿到空表被误判为没有单。
  // searchForm 不动，分区与状态筛选继续带上
  pagination.pageNum = 1
  loadData()
}

const handleCurrentChange = (page) => {
  pagination.pageNum = page
  loadData()
}

const openCreateDialog = () => {
  createZoneTagId.value = null
  createDialogVisible.value = true
}

const handleCreate = async () => {
  if (createZoneTagId.value == null) {
    ElMessage.warning('请选择盘点分区（未分配分区也可选择）')
    return
  }
  creating.value = true
  try {
    const payload =
      createZoneTagId.value === UNASSIGNED_FILTER_VALUE
        ? { zoneTagId: null }
        : { zoneTagId: createZoneTagId.value }
    const newId = await createStockCheck(payload)
    ElMessage.success('开盘成功')
    createDialogVisible.value = false
    pagination.pageNum = 1
    await loadData()
    // 开盘后直接进入登记，减少“开完盘还得回列表找单”的操作
    openDetail(newId)
  } finally {
    creating.value = false
  }
}

const openDetail = async (id) => {
  detailVisible.value = true
  await loadDetail(id)
}

const loadDetail = async (id) => {
  detailLoading.value = true
  try {
    const data = await getStockCheckById(id)
    detail.value = data
    Object.keys(actualMap).forEach(key => delete actualMap[key])
    data.items.forEach(item => {
      actualMap[item.id] = item.actualQuantity == null ? null : item.actualQuantity
    })
    dirty.value = false
  } finally {
    detailLoading.value = false
  }
}

const handleSaveDraft = async () => {
  if (!detail.value) return
  const items = countableItems.value
    .filter(item => actualMap[item.id] != null)
    .map(item => ({ itemId: item.id, actualQuantity: actualMap[item.id] }))
  if (items.length === 0) {
    ElMessage.warning('请至少登记一个配件的实盘数后再保存')
    return
  }
  saving.value = true
  try {
    await recordStockCheckItems(detail.value.header.id, items)
    ElMessage.success('实盘数已登记，库存尚未改动，确认后才会回写')
    dirty.value = false
    await loadDetail(detail.value.header.id)
    loadData()
  } finally {
    saving.value = false
  }
}

const handleConfirm = () => {
  if (!detail.value) return
  if (unrecordedCount.value > 0) {
    ElMessage.warning(`还有 ${unrecordedCount.value} 种配件未登记实盘数，请登记齐全并保存后再确认`)
    return
  }
  if (dirty.value) {
    // 按钮在脏数据时禁用，兜底防止键盘等方式触发：必须先保存登记，确保确认口径与已登记数据一致
    ElMessage.warning('实盘数有未保存的修改，请先点击“保存登记”后再确认')
    return
  }
  // 差异说明必填：弹窗内说明为纯空白时确认按钮禁用；确认后才按实盘数一次性回写
  confirmRemark.value = ''
  confirmDialogVisible.value = true
}

const submitConfirm = async () => {
  if (!detail.value) return
  const remark = confirmRemark.value.trim()
  if (!remark) {
    ElMessage.warning('请填写差异说明后再确认盘点回写')
    return
  }
  confirming.value = true
  try {
    await confirmStockCheck(detail.value.header.id, remark)
    ElMessage.success('盘点已确认，档案现存量已一次性回写')
    confirmDialogVisible.value = false
    await loadDetail(detail.value.header.id)
    loadData()
    // 档案现存量已变化：通知配件档案、方案列表与缺口页按最新库存刷新，
    // 保证刷新后档案现存量与最近一次已确认盘点一致
    notifyStockChanged('stock-check')
  } finally {
    confirming.value = false
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(
    `待确认盘点单 ${row.checkNo} 删除后不可恢复，确认删除吗？`,
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
    .then(async () => {
      await deleteStockCheck(row.id)
      ElMessage.success('删除成功')
      loadData()
    })
    .catch(() => {})
}

// 导出已确认盘点单的差异明细：与抽屉详情同一口径，合计行与页面差异种数、盈亏件数一致；
// 无差异（含空分区）时后端文件只有表头。待确认单前端先行拦截，后端也会拒绝并说明原因
const handleExportDiff = async () => {
  if (!detail.value) return
  if (exporting.value) {
    return
  }
  const header = detail.value.header
  if (header.status !== 1) {
    ElMessage.warning('待确认盘点单尚未回写库存，差异未定稿，请确认并回写后再导出差异明细')
    return
  }
  exporting.value = true
  try {
    const response = await exportStockCheckDiff(header.id)
    const fileName = resolveExportFileName(
      response.headers['content-disposition'],
      `盘点差异明细_${header.checkNo}.csv`
    )
    triggerBrowserDownload(response.data, fileName)
    ElMessage.success('导出成功')
  } catch (e) {
    // 错误提示已由请求拦截器统一展示（含待确认单等业务拒绝原因）
  } finally {
    exporting.value = false
  }
}

// 其他页面（如档案直接改了现存量）不改变待确认单的账面快照；
// 已确认单结果不受影响，但若用户正开着详情，列表状态仍轻量刷新一次保持一致
const handleStockChanged = () => {
  loadData()
}

let unsubscribeStockChanged = null

onMounted(() => {
  loadZoneTagList()
  loadData()
  unsubscribeStockChanged = onStockChanged(handleStockChanged)
})

onBeforeUnmount(() => {
  unsubscribeStockChanged?.()
})
</script>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-card,
.table-card {
  border-radius: 8px;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.rule-alert {
  margin-bottom: 16px;
}

.diff-text {
  color: #f56c6c;
  font-weight: 700;
}

.remark-text {
  color: #303133;
}

.remark-empty,
.remark-pending {
  color: #909399;
}

.confirm-alert {
  margin-bottom: 16px;
}

.confirm-form {
  margin-top: 4px;
}

.readonly-remark {
  flex: 1;
  min-width: 0;
  word-break: break-all;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.form-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.detail-container {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 0 20px 20px;
}

.detail-summary-card,
.detail-table-card {
  border-radius: 8px;
}

.summary-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.summary-time {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}

.detail-footer {
  position: sticky;
  bottom: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);
}

.readonly-footer {
  position: static;
  box-shadow: none;
}

.footer-hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.deleted-hint {
  color: #909399;
  font-size: 12px;
}

:deep(.deleted-row) {
  color: #909399;
  background-color: #f4f4f5;
}

:deep(.diff-row) {
  background-color: #fdf6ec;
}
</style>
