<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="请输入方案名称/适用场景"
            clearable
            style="width: 240px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-select
            v-model="searchForm.status"
            placeholder="全部"
            clearable
            style="width: 160px"
            @change="handleSearch"
          >
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
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
          <el-icon color="#409EFF"><Connection /></el-icon>
          <span>布线方案列表</span>
        </div>
        <div class="header-actions">
          <el-button type="success" plain :loading="exporting" @click="handleExport">
            <el-icon><Download /></el-icon>
            导出当前筛选结果
          </el-button>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增方案
          </el-button>
        </div>
      </div>

      <el-table :data="tableData" border stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="planName" label="方案名称" min-width="180" />
        <el-table-column prop="scene" label="适用场景" min-width="160" />
        <el-table-column prop="detailCount" label="配件种类" width="100" align="center">
          <template #default="{ row }">
            <el-tag type="info" effect="plain">{{ row.detailCount }} 种</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="库存校验" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.writeoff" type="success" effect="dark">已核销</el-tag>
            <el-tag v-else-if="row.status !== 1" type="info" effect="plain">停用不合计</el-tag>
            <el-tag v-else-if="row.hasDeletedAccessory" type="danger" effect="plain">含已删除配件</el-tag>
            <el-tag v-else-if="row.stockSufficient" type="success" effect="plain">库存充足</el-tag>
            <el-tag v-else type="danger" effect="dark">库存不足</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              :loading="row.statusLoading"
              @change="(val) => handleStatusChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" align="center" />
        <el-table-column label="操作" width="270" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="success" @click="handleView(row)">详情</el-button>
            <el-button link type="primary" :disabled="row.writeoff" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="handleWriteoff(row)" :disabled="!canWriteoff(row)">
              核销出库
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="720px"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="方案名称" prop="planName">
          <el-input v-model="formData.planName" placeholder="请输入方案名称" />
        </el-form-item>
        <el-form-item label="适用场景" prop="scene">
          <el-input v-model="formData.scene" placeholder="请输入适用场景" />
        </el-form-item>
        <el-form-item label="启用状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="方案说明" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入方案说明" />
        </el-form-item>
        <el-form-item label="配件明细">
          <div class="detail-editor">
            <el-table :data="formData.details" border size="small" style="width: 100%">
              <el-table-column label="配件" min-width="260">
                <template #default="{ row }">
                  <el-select
                    v-model="row.accessoryId"
                    placeholder="请选择配件"
                    filterable
                    style="width: 100%"
                  >
                    <el-option
                      v-for="item in accessoryList"
                      :key="item.id"
                      :label="`${item.accessoryName}（${item.model}）`"
                      :value="item.id"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="需求数量" width="160" align="center">
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.quantity"
                    :min="1"
                    :precision="0"
                    style="width: 130px"
                  />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center">
                <template #default="{ $index }">
                  <el-button link type="danger" @click="handleRemoveDetail($index)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button class="add-detail-btn" type="primary" plain @click="handleAddDetail">
              <el-icon><Plus /></el-icon>
              添加配件
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailDialogVisible" title="方案详情" width="760px">
      <template v-if="currentPlan">
        <el-descriptions :column="2" border class="plan-descriptions">
          <el-descriptions-item label="方案名称">{{ currentPlan.planName }}</el-descriptions-item>
          <el-descriptions-item label="适用场景">{{ currentPlan.scene || '-' }}</el-descriptions-item>
          <el-descriptions-item label="启用状态">
            <el-tag :type="currentPlan.status === 1 ? 'success' : 'danger'" effect="light">
              {{ currentPlan.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="核销状态">
            <el-tag v-if="currentPlan.writeoff" type="success" effect="dark">
              已核销出库{{ currentPlan.writeoffTime ? `（${formatTime(currentPlan.writeoffTime)}）` : '' }}
            </el-tag>
            <span v-else>未核销</span>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ currentPlan.createTime }}</el-descriptions-item>
          <el-descriptions-item label="方案说明" :span="2">
            {{ currentPlan.description || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="currentPlan.writeoff"
          title="该方案已核销出库，现存量已按下列需求数量扣减；同一方案不可重复核销。"
          type="success"
          :closable="false"
          show-icon
          class="detail-alert"
        />
        <el-alert
          v-else-if="currentPlan.hasDeletedAccessory"
          title="方案包含已删除配件，无法核销出库，请先调整方案明细。"
          type="error"
          :closable="false"
          show-icon
          class="detail-alert"
        />
        <el-alert
          v-else-if="currentPlan.status === 1 && !currentPlan.stockSufficient"
          title="部分配件现存量不足（红色行），请补充库存后再核销出库。"
          type="error"
          :closable="false"
          show-icon
          class="detail-alert"
        />
        <el-alert
          v-else-if="currentPlan.status === 1"
          title="配件现存量充足，可核销出库；核销后将按需求数量扣减现存量且不可重复核销。"
          type="success"
          :closable="false"
          show-icon
          class="detail-alert"
        />

        <div class="zone-detail-section">
          <div class="section-title">配件明细（按库房分区）</div>
          <el-empty v-if="zoneGroups.length === 0" description="暂无配件明细" :image-size="80" />
          <div v-for="group in zoneGroups" :key="group.zoneName" class="zone-group">
            <div class="zone-group-header">
              <el-tag type="primary" effect="light">{{ group.zoneName }}</el-tag>
              <span class="zone-group-count">共 {{ group.items.length }} 种配件</span>
            </div>
            <el-table
              :data="group.items"
              border
              size="small"
              :row-class-name="detailRowClassName"
              style="width: 100%"
            >
              <el-table-column label="配件名称" min-width="160">
                <template #default="{ row }">
                  {{ row.accessoryName }}
                  <el-tag v-if="row.accessoryDeleted" type="danger" size="small" effect="dark">已删除</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="model" label="型号" min-width="140" />
              <el-table-column prop="quantity" label="需求数量" width="100" align="center" />
              <el-table-column label="现存量" width="100" align="center">
                <template #default="{ row }">
                  <span v-if="row.accessoryDeleted" class="deleted-hint">-</span>
                  <span :class="{ 'shortage-text': isDetailRowShort(row) }">{{ row.stockQuantity ?? 0 }}</span>
                </template>
              </el-table-column>
              <el-table-column label="缺口" width="90" align="center">
                <template #default="{ row }">
                  <span v-if="row.accessoryDeleted" class="deleted-hint">不可核销</span>
                  <span :class="{ 'shortage-text': isDetailRowShort(row) }">{{ detailGap(row) }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <el-button
          v-if="currentPlan"
          type="warning"
          :disabled="!canWriteoff(currentPlan) || writeoffLoading"
          :loading="writeoffLoading"
          @click="handleWriteoff(currentPlan)"
        >
          核销出库
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Connection, Download } from '@element-plus/icons-vue'
import {
  getWiringPlanPage,
  getWiringPlanById,
  addWiringPlan,
  updateWiringPlan,
  deleteWiringPlan,
  updateWiringPlanStatus,
  exportWiringPlans,
  writeoffWiringPlan
} from '@/api/wiringPlan'
import { getAccessoryPage } from '@/api/accessory'

const tableData = ref([])
const accessoryList = ref([])

// 导出进行中标记：请求未返回前重复点击直接忽略，避免重复下载
const exporting = ref(false)

const searchForm = reactive({
  keyword: '',
  status: null
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const dialogVisible = ref(false)
const dialogTitle = computed(() => (formData.id ? '编辑方案' : '新增方案'))
const formRef = ref()
const formData = reactive({
  id: null,
  planName: '',
  scene: '',
  description: '',
  status: 1,
  details: []
})

const formRules = {
  planName: [{ required: true, message: '请输入方案名称', trigger: 'blur' }]
}

const detailDialogVisible = ref(false)
const currentPlan = ref(null)
// 核销请求进行中标记：请求未返回前重复点击直接忽略，避免重复核销
const writeoffLoading = ref(false)

// 仅“启用、未核销、库存充足且不含已删除配件”的方案可核销出库
const canWriteoff = (plan) => !!plan
  && plan.status === 1
  && !plan.writeoff
  && !!plan.stockSufficient
  && !plan.hasDeletedAccessory

// 方案明细行是否库存不足（已核销方案不再标红，已删除配件单独走“不可核销”样式）
const isDetailRowShort = (row) => {
  if (!currentPlan.value || currentPlan.value.writeoff || row.accessoryDeleted) return false
  return (row.stockQuantity ?? 0) < row.quantity
}

const detailGap = (row) => Math.max(0, row.quantity - (row.stockQuantity ?? 0))

const detailRowClassName = ({ row }) => {
  if (row.accessoryDeleted) return 'deleted-row'
  return isDetailRowShort(row) ? 'shortage-row' : ''
}

const formatTime = (time) => {
  if (!time) return ''
  return String(time).replace('T', ' ').slice(0, 19)
}

const handleWriteoff = (row) => {
  if (!canWriteoff(row)) {
    return
  }
  ElMessageBox.confirm(
    `确认按方案「${row.planName}」核销出库吗？将按明细需求数量一次性扣减配件现存量，核销后该方案不可重复核销、不可编辑。`,
    '核销出库确认',
    {
      confirmButtonText: '确认核销',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
    .then(async () => {
      if (writeoffLoading.value) return
      writeoffLoading.value = true
      try {
        await writeoffWiringPlan(row.id)
        ElMessage.success('核销出库成功，现存量已扣减')
        detailDialogVisible.value = false
        await loadData()
      } finally {
        writeoffLoading.value = false
      }
    })
    .catch(() => {})
}

const zoneGroups = computed(() => {
  if (!currentPlan.value?.details) return []
  const groupMap = new Map()
  for (const item of currentPlan.value.details) {
    const zoneName = item.zoneTagName || '未分配分区'
    if (!groupMap.has(zoneName)) {
      groupMap.set(zoneName, [])
    }
    groupMap.get(zoneName).push(item)
  }
  return Array.from(groupMap.entries()).map(([zoneName, items]) => ({ zoneName, items }))
})

const loadData = async () => {
  const res = await getWiringPlanPage({
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize,
    keyword: searchForm.keyword,
    status: searchForm.status
  })
  tableData.value = res.records
  pagination.total = res.total
}

const loadAccessoryList = async () => {
  const res = await getAccessoryPage({ pageNum: 1, pageSize: 999 })
  accessoryList.value = res.records
}

// 导出文件名主体最长保留 80 个字符（含扩展名），防止超长关键词拼出的文件名超出文件系统限制
const MAX_FILENAME_LENGTH = 80

// 超长时截断文件名主体并保留 .csv 扩展名
const truncateFileName = (name) => {
  if (!name || name.length <= MAX_FILENAME_LENGTH) {
    return name
  }
  const dotIndex = name.lastIndexOf('.')
  const ext = dotIndex >= 0 ? name.slice(dotIndex) : ''
  const stem = dotIndex >= 0 ? name.slice(0, dotIndex) : name
  return stem.slice(0, MAX_FILENAME_LENGTH - ext.length) + ext
}

const buildFallbackFileName = () => {
  const date = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  const datePart = `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}`
  const keyword = (searchForm.keyword || '').trim()
  const keywordPart = keyword ? `_${keyword}` : ''
  const statusPart = searchForm.status === 1 ? '_启用' : searchForm.status === 0 ? '_停用' : ''
  const main = `布线方案导出${keywordPart}${statusPart}_${datePart}.csv`
  return truncateFileName(main)
}

// 优先使用后端返回的中文文件名（Content-Disposition: filename*），解析失败或超长时使用前端兜底名
const resolveExportFileName = (contentDisposition) => {
  const fallback = buildFallbackFileName()
  if (!contentDisposition) {
    return fallback
  }
  const starMatch = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (starMatch && starMatch[1]) {
    try {
      const name = decodeURIComponent(starMatch[1])
      return truncateFileName(name) || fallback
    } catch (e) {
      return fallback
    }
  }
  const plainMatch = contentDisposition.match(/filename="?([^";]+)"?/i)
  if (plainMatch && plainMatch[1] && /[一-龥]/.test(plainMatch[1])) {
    return truncateFileName(plainMatch[1])
  }
  return fallback
}

const triggerBrowserDownload = (blob, fileName) => {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // 释放 Blob URL，避免内存泄漏
  window.URL.revokeObjectURL(url)
}

const handleExport = async () => {
  // 导出请求未完成前忽略重复点击
  if (exporting.value) {
    return
  }
  exporting.value = true
  try {
    // 按当前筛选条件实时确认结果是否为空（搜索框输入后未点搜索时表格数据可能仍是旧条件）
    const pageRes = await getWiringPlanPage({
      pageNum: 1,
      pageSize: 1,
      keyword: searchForm.keyword,
      status: searchForm.status
    })
    if (!pageRes.records || pageRes.records.length === 0) {
      ElMessage.warning('当前筛选条件下没有可导出的方案')
      return
    }
    const response = await exportWiringPlans({
      keyword: searchForm.keyword,
      status: searchForm.status
    })
    const fileName = resolveExportFileName(response.headers['content-disposition'])
    triggerBrowserDownload(response.data, fileName)
    ElMessage.success('导出成功')
  } catch (e) {
    // 错误提示已由请求拦截器统一展示
  } finally {
    exporting.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.status = null
  pagination.pageNum = 1
  loadData()
}

const handleSizeChange = (size) => {
  pagination.pageSize = size
  loadData()
}

const handleCurrentChange = (page) => {
  pagination.pageNum = page
  loadData()
}

const handleAdd = () => {
  resetForm()
  dialogVisible.value = true
}

const handleEdit = async (row) => {
  resetForm()
  const plan = await getWiringPlanById(row.id)
  formData.id = plan.id
  formData.planName = plan.planName
  formData.scene = plan.scene
  formData.description = plan.description
  formData.status = plan.status
  formData.details = (plan.details || []).map(item => ({
    accessoryId: item.accessoryId,
    quantity: item.quantity
  }))
  dialogVisible.value = true
}

const handleView = async (row) => {
  currentPlan.value = await getWiringPlanById(row.id)
  detailDialogVisible.value = true
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该布线方案吗？存在关联配件明细或已核销出库的方案无法删除。', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      await deleteWiringPlan(row.id)
      ElMessage.success('删除成功')
      loadData()
    })
    .catch(() => {})
}

const handleStatusChange = async (row, val) => {
  // 请求未返回前忽略重复切换，避免连续快速点击导致状态错乱
  if (row.statusLoading) {
    return
  }
  row.statusLoading = true
  try {
    await updateWiringPlanStatus(row.id, val)
    ElMessage.success(val === 1 ? '已启用' : '已停用')
  } catch (e) {
    // 切换失败：开关恢复原状态（后端错误提示由请求拦截器统一展示）
    row.status = val === 1 ? 0 : 1
  } finally {
    row.statusLoading = false
  }
}

const handleAddDetail = () => {
  formData.details.push({ accessoryId: null, quantity: 1 })
}

const handleRemoveDetail = (index) => {
  formData.details.splice(index, 1)
}

const validateDetails = () => {
  const seen = new Set()
  for (const item of formData.details) {
    if (!item.accessoryId) {
      ElMessage.warning('请选择配件')
      return false
    }
    if (!item.quantity || item.quantity < 1) {
      ElMessage.warning('需求数量必须为正整数')
      return false
    }
    if (seen.has(item.accessoryId)) {
      ElMessage.warning('同一方案内配件不可重复')
      return false
    }
    seen.add(item.accessoryId)
  }
  return true
}

const handleSubmit = async () => {
  await formRef.value.validate()

  if (!validateDetails()) {
    return
  }

  const payload = {
    id: formData.id,
    planName: formData.planName,
    scene: formData.scene,
    description: formData.description,
    status: formData.status,
    details: formData.details
  }

  if (formData.id) {
    await updateWiringPlan(payload)
    ElMessage.success('修改成功')
  } else {
    await addWiringPlan(payload)
    ElMessage.success('新增成功')
  }

  dialogVisible.value = false
  loadData()
}

const handleDialogClosed = () => {
  resetForm()
}

const resetForm = () => {
  formData.id = null
  formData.planName = ''
  formData.scene = ''
  formData.description = ''
  formData.status = 1
  formData.details = []
  formRef.value?.clearValidate()
}

onMounted(() => {
  loadAccessoryList()
  loadData()
})
</script>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-card {
  border-radius: 8px;
}

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

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.detail-editor {
  width: 100%;
}

.add-detail-btn {
  margin-top: 12px;
}

.plan-descriptions {
  margin-bottom: 20px;
}

.zone-detail-section .section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.zone-group {
  margin-bottom: 16px;
}

.zone-group-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.zone-group-count {
  font-size: 13px;
  color: #909399;
}

.detail-alert {
  margin-bottom: 16px;
}

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
</style>
