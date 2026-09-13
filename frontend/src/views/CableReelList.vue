<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="盘号">
          <el-input
            v-model="searchForm.keyword"
            placeholder="按盘号搜索"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="绑定配件">
          <el-select
            v-model="searchForm.accessoryId"
            placeholder="全部配件"
            clearable
            filterable
            style="width: 220px"
            @change="handleSearch"
          >
            <el-option
              v-for="item in accessoryOptions"
              :key="item.id"
              :label="`${item.accessoryName}（${item.model}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="searchForm.status"
            placeholder="全部状态"
            clearable
            style="width: 150px"
            @change="handleSearch"
          >
            <el-option label="未开盘" :value="0" />
            <el-option label="已开盘" :value="1" />
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
          <el-icon color="#409EFF"><Files /></el-icon>
          <span>整盘电源线档案</span>
        </div>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          整盘建档
        </el-button>
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="rule-alert"
        title="按盘号建档，登记盘号、绑定配件与盘上剩余米数，同一盘号只能建一次。建档为“未开盘”，米数还不计入配件档案；开盘确认时要求该配件档案现存为 0（整盘是米数唯一来源），确认后整盘米数一次性计入档案，盘上剩余与配件档案米数同步联动、保持一致，此后才能从该盘扣米，没开过的盘不能扣米。"
      />

      <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="reelNo" label="盘号" min-width="150" />
        <el-table-column label="绑定配件" min-width="200">
          <template #default="{ row }">
            {{ row.accessoryName }}
            <el-tag size="small" type="info" effect="plain">{{ row.model }}</el-tag>
            <el-tag v-if="row.accessoryDeleted" size="small" type="danger" effect="dark">已删除</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="盘上剩余" min-width="120" align="center">
          <template #default="{ row }">
            <span class="meters-text">{{ row.remainingMeters }}</span>
            <span class="unit-text">{{ row.specUnit || 'm' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="配件档案米数" min-width="150" align="center">
          <template #default="{ row }">
            <template v-if="row.status === 1">
              <span :class="{ 'meters-text': true, 'mismatch-text': row.stockMatched === false }">
                {{ row.accessoryStockQuantity ?? '-' }}
              </span>
              <span class="unit-text">{{ row.specUnit || 'm' }}</span>
              <el-tag
                v-if="row.stockMatched === true"
                size="small"
                type="success"
                effect="light"
                class="match-tag"
              >
                一致
              </el-tag>
              <el-tag v-else-if="row.stockMatched === false" size="small" type="danger" effect="dark" class="match-tag">
                不一致
              </el-tag>
            </template>
            <el-tag v-else size="small" type="info" effect="plain">未开盘，未入账</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'" effect="plain">
              {{ row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="openTime" label="开盘时间" min-width="170" align="center">
          <template #default="{ row }">{{ row.openTime || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="建档时间" min-width="170" align="center" />
        <el-table-column label="操作" width="230" fixed="right" align="center">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button link type="primary" @click="handleOpen(row)">开盘确认</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
            <template v-else>
              <el-button link type="warning" :disabled="row.accessoryDeleted" @click="openDeductDialog(row)">
                扣米
              </el-button>
            </template>
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

    <!-- 建档对话框：取消/关闭只关窗，绝不发请求，不会落这条盘 -->
    <el-dialog v-model="createDialogVisible" title="整盘电源线建档" width="500px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="110px">
        <el-form-item label="盘号" prop="reelNo">
          <el-input v-model="createForm.reelNo" placeholder="请输入盘号（同一盘号只能建一次）" clearable />
        </el-form-item>
        <el-form-item label="绑定配件" prop="accessoryId">
          <el-select
            v-model="createForm.accessoryId"
            placeholder="请选择绑定的配件"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="item in accessoryOptions"
              :key="item.id"
              :label="`${item.accessoryName}（${item.model}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="盘上剩余米数" prop="remainingMeters">
          <el-input-number
            v-model="createForm.remainingMeters"
            :min="0"
            :precision="0"
            :step="1"
            controls-position="right"
            style="width: 200px"
          />
          <span class="unit-inline">米</span>
        </el-form-item>
        <el-form-item>
          <div class="form-hint">建档后为“未开盘”，盘上米数暂不计入配件档案；开盘确认后才一次性入账并允许扣米。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelCreate">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">确认建档</el-button>
      </template>
    </el-dialog>

    <!-- 扣米对话框：仅已开盘盘可打开；取消不发请求 -->
    <el-dialog v-model="deductDialogVisible" title="线缆盘扣米" width="460px">
      <div v-if="deductTarget" class="deduct-summary">
        <div>盘号：<b>{{ deductTarget.reelNo }}</b></div>
        <div>绑定配件：{{ deductTarget.accessoryName }}（{{ deductTarget.model }}）</div>
        <div>
          盘上剩余：<b>{{ deductTarget.remainingMeters }}</b> 米｜
          配件档案：<b>{{ deductTarget.accessoryStockQuantity }}</b> 米
        </div>
      </div>
      <el-form ref="deductFormRef" :model="deductForm" :rules="deductRules" label-width="100px" class="deduct-form">
        <el-form-item label="扣减米数" prop="meters">
          <el-input-number
            v-model="deductForm.meters"
            :min="1"
            :precision="0"
            :step="1"
            controls-position="right"
            style="width: 200px"
          />
          <span class="unit-inline">米</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deductDialogVisible = false">取消</el-button>
        <el-button type="warning" :loading="deducting" @click="handleDeduct">确认扣米</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Files } from '@element-plus/icons-vue'
import {
  getCableReelPage,
  createCableReel,
  openCableReel,
  deductCableReel,
  deleteCableReel
} from '@/api/cableReel'
import { getAccessoryPage } from '@/api/accessory'
import { notifyStockChanged, onStockChanged } from '@/utils/stockSync'

const tableData = ref([])
const accessoryOptions = ref([])
const loading = ref(false)

const searchForm = reactive({
  keyword: '',
  accessoryId: null,
  status: null
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const createDialogVisible = ref(false)
const creating = ref(false)
const createFormRef = ref(null)
const createForm = reactive({
  reelNo: '',
  accessoryId: null,
  remainingMeters: 100
})
const createRules = {
  reelNo: [
    { required: true, message: '请填写盘号', trigger: 'blur' },
    { whitespace: true, message: '盘号不能为纯空格', trigger: 'blur' },
    { max: 60, message: '盘号长度不能超过60个字符', trigger: 'blur' }
  ],
  accessoryId: [
    { required: true, message: '请选择绑定的配件', trigger: 'change' }
  ],
  remainingMeters: [
    { required: true, message: '请填写盘上剩余米数', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value == null || Number.isNaN(value) || value < 0) {
          callback(new Error('盘上剩余米数必须为非负整数'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const deductDialogVisible = ref(false)
const deducting = ref(false)
const deductTarget = ref(null)
const deductFormRef = ref(null)
const deductForm = reactive({ meters: 1 })
const deductRules = {
  meters: [
    { required: true, message: '请填写扣减米数', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        const remaining = deductTarget.value?.remainingMeters ?? 0
        if (value == null || value < 1) {
          callback(new Error('扣减米数必须为正整数'))
        } else if (value > remaining) {
          callback(new Error(`扣减米数不能超过盘上剩余（${remaining} 米）`))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const loadData = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (searchForm.keyword && searchForm.keyword.trim()) {
      params.keyword = searchForm.keyword.trim()
    }
    if (searchForm.status != null) {
      params.status = searchForm.status
    }
    if (searchForm.accessoryId != null) {
      params.accessoryId = searchForm.accessoryId
    }
    const res = await getCableReelPage(params)
    tableData.value = res.records
    pagination.total = res.total
    // 越界兜底：total 还有数据但当前页为空时回到第一页按原筛选重拉
    if (res.records.length === 0 && res.total > 0 && pagination.pageNum > 1) {
      pagination.pageNum = 1
      await loadData()
    }
  } finally {
    loading.value = false
  }
}

const loadAccessoryOptions = async () => {
  const res = await getAccessoryPage({ pageNum: 1, pageSize: 999 })
  accessoryOptions.value = res.records
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.accessoryId = null
  searchForm.status = null
  pagination.pageNum = 1
  loadData()
}

const handleSizeChange = (size) => {
  pagination.pageSize = size
  pagination.pageNum = 1
  loadData()
}

const handleCurrentChange = (page) => {
  pagination.pageNum = page
  loadData()
}

// 每次打开都重置为空白表单；取消只关窗，不调用任何建档接口
const openCreateDialog = () => {
  createForm.reelNo = ''
  createForm.accessoryId = null
  createForm.remainingMeters = 100
  createDialogVisible.value = true
}

const cancelCreate = () => {
  // 建档窗点取消：仅关窗，绝不落这条盘（不发请求）
  createDialogVisible.value = false
}

const handleCreate = async () => {
  if (!createFormRef.value) return
  try {
    await createFormRef.value.validate()
  } catch (e) {
    return
  }
  creating.value = true
  try {
    await createCableReel({
      reelNo: createForm.reelNo.trim(),
      accessoryId: createForm.accessoryId,
      remainingMeters: createForm.remainingMeters
    })
    ElMessage.success('建档成功（未开盘，开盘确认后米数才计入配件档案）')
    createDialogVisible.value = false
    pagination.pageNum = 1
    await loadData()
  } finally {
    creating.value = false
  }
}

const handleOpen = (row) => {
  ElMessageBox.confirm(
    `确认开盘 ${row.reelNo} 吗？开盘后整盘 ${row.remainingMeters} 米将一次性计入配件「${row.accessoryName}」档案（要求该配件档案现存为 0，整盘是米数唯一来源），`
      + '盘上剩余与档案米数保持一致，之后才能从该盘扣米；同一配件同时只能有一个已开盘。',
    '开盘确认',
    {
      confirmButtonText: '确认开盘',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
    .then(async () => {
      await openCableReel(row.id)
      ElMessage.success('开盘成功，整盘米数已计入配件档案')
      await loadData()
      // 档案现存量已变化：通知配件档案、缺口页等按最新库存刷新
      notifyStockChanged('cable-reel')
    })
    .catch(() => {})
}

const openDeductDialog = (row) => {
  // 只有已开盘盘才有“扣米”入口；未开盘盘不渲染该按钮，后端也会再次拒绝
  if (row.status !== 1) {
    ElMessage.warning('该盘尚未开盘确认，不能扣米')
    return
  }
  if (row.stockMatched === false) {
    ElMessage.warning('盘上剩余与配件档案米数不一致，请先核对档案库存后再扣米')
    return
  }
  deductTarget.value = row
  deductForm.meters = 1
  deductDialogVisible.value = true
}

const handleDeduct = async () => {
  if (!deductFormRef.value || !deductTarget.value) return
  try {
    await deductFormRef.value.validate()
  } catch (e) {
    return
  }
  deducting.value = true
  try {
    await deductCableReel(deductTarget.value.id, deductForm.meters)
    ElMessage.success(`已扣减 ${deductForm.meters} 米`)
    deductDialogVisible.value = false
    deductTarget.value = null
    await loadData()
    notifyStockChanged('cable-reel')
  } finally {
    deducting.value = false
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(
    `未开盘盘 ${row.reelNo} 删除后不可恢复，确认删除吗？（已开盘盘不能删除）`,
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
    .then(async () => {
      await deleteCableReel(row.id)
      ElMessage.success('删除成功')
      loadData()
    })
    .catch(() => {})
}

// 配件档案现存量被其他页面（如盘点回写）改动后，刷新本页以更新“一致/不一致”标记
const handleStockChanged = () => {
  loadData()
}

let unsubscribeStockChanged = null

onMounted(() => {
  loadAccessoryOptions()
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

.meters-text {
  font-weight: 700;
  font-size: 15px;
}

.unit-text {
  margin-left: 2px;
  color: #909399;
  font-size: 12px;
}

.unit-inline {
  margin-left: 8px;
  color: #606266;
}

.mismatch-text {
  color: #f56c6c;
}

.match-tag {
  margin-left: 6px;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.form-hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.deduct-summary {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  margin-bottom: 16px;
  background-color: #f4f8ff;
  border: 1px solid #d9e8ff;
  border-radius: 6px;
  font-size: 13px;
  color: #303133;
}

.deduct-form {
  margin-top: 4px;
}
</style>
