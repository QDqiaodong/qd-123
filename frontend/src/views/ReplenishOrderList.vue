<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="状态">
          <el-select
            v-model="searchForm.status"
            placeholder="全部状态"
            clearable
            style="width: 180px"
            @change="handleSearch"
          >
            <el-option label="待提交" :value="0" />
            <el-option label="已提交" :value="1" />
            <el-option label="已作废" :value="2" />
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
          <el-icon color="#409EFF"><ShoppingCart /></el-icon>
          <span>补货单</span>
        </div>
        <el-button type="primary" @click="goLedger">
          <el-icon><Box /></el-icon>
          去安全库存台账勾选生成
        </el-button>
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="rule-alert"
        title="在安全库存台账勾选低于下限的配件生成补货单，单据按分区汇总缺口件数。待提交草稿可调整补货数量；提交后配件档案回填补货单号与待补数量、数量锁定不可再改；已提交单作废后清除档案待补标记，单据留档只读。"
      />

      <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="replenishNo" label="补货单号" min-width="180" />
        <el-table-column label="状态" min-width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="plain">
              {{ row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="配件种数" min-width="100" align="center">
          <template #default="{ row }">{{ row.itemCount }} 种</template>
        </el-table-column>
        <el-table-column label="涉及分区" min-width="100" align="center">
          <template #default="{ row }">{{ row.zoneCount }} 个</template>
        </el-table-column>
        <el-table-column label="补货件数合计" min-width="130" align="center">
          <template #default="{ row }">
            <span class="total-quantity">{{ row.totalQuantity }} 件</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="生成时间" min-width="170" align="center" />
        <el-table-column prop="submitTime" label="提交时间" min-width="170" align="center">
          <template #default="{ row }">{{ row.submitTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">
              {{ row.status === 0 ? '编辑/查看' : '查看' }}
            </el-button>
            <el-button v-if="row.status === 0" link type="danger" @click="handleDelete(row)">
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

    <!-- 补货单详情抽屉：分区汇总 + 明细；草稿可改数量/提交，已提交可作废 -->
    <el-drawer
      v-model="detailVisible"
      size="70%"
      :title="detail ? `补货单 ${detail.header.replenishNo}` : '补货单详情'"
      destroy-on-close
    >
      <div v-if="detail" v-loading="detailLoading" class="detail-container">
        <el-card class="detail-summary-card">
          <div class="summary-tags">
            <el-tag :type="statusTagType(detail.header.status)" effect="plain">
              {{ detail.header.statusText }}
            </el-tag>
            <el-tag type="info" effect="plain">配件 {{ detail.header.itemCount }} 种</el-tag>
            <el-tag type="info" effect="plain">涉及分区 {{ detail.header.zoneCount }} 个</el-tag>
            <el-tag type="danger" effect="dark">补货合计 {{ detail.header.totalQuantity }} 件</el-tag>
            <el-tag v-if="detail.header.status === 2" type="info" effect="plain">已作废，只读留档</el-tag>
          </div>
          <div v-if="detail.header.cancelReason" class="cancel-reason">
            作废原因：{{ detail.header.cancelReason }}
          </div>
          <div class="summary-time">
            生成时间：{{ detail.header.createTime }}
            <span v-if="detail.header.submitTime">｜提交时间：{{ detail.header.submitTime }}</span>
            <span v-if="detail.header.cancelTime">｜作废时间：{{ detail.header.cancelTime }}</span>
          </div>
        </el-card>

        <el-card class="detail-table-card">
          <div class="section-title">按分区汇总缺口件数</div>
          <el-table :data="detail.zoneSummaries" border size="small" class="zone-summary-table">
            <el-table-column label="分区" min-width="160">
              <template #default="{ row }">
                <el-tag v-if="row.unassignedZone" type="warning" effect="plain">未分配分区</el-tag>
                <el-tag v-else type="primary" effect="light">{{ row.zoneName }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="accessoryCount" label="涉及配件种数" min-width="130" align="center" />
            <el-table-column label="缺口件数" min-width="130" align="center">
              <template #default="{ row }">
                <span class="total-quantity">{{ row.totalQuantity }} 件</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="zone-total-row">
            合计：{{ detail.header.itemCount }} 种 / {{ detail.header.totalQuantity }} 件
          </div>
        </el-card>

        <el-card class="detail-table-card">
          <div class="section-title">补货明细</div>
          <el-alert
            v-if="editable && detail.conflictCount > 0"
            type="error"
            :closable="false"
            show-icon
            class="conflict-alert"
            :title="`有 ${detail.conflictCount} 种配件已删除或已被其他补货单占用，请处理后再提交`"
          />
          <el-table :data="detail.items" border stripe :row-class-name="itemRowClassName" style="width: 100%">
            <el-table-column prop="accessoryName" label="配件名称" min-width="150">
              <template #default="{ row }">
                {{ row.accessoryName }}
                <el-tag v-if="row.accessoryDeleted" type="danger" size="small" effect="dark">已删除</el-tag>
                <el-tag v-else-if="row.pendingConflict" type="warning" size="small" effect="dark">已被其他单占用</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="model" label="型号" min-width="120" />
            <el-table-column label="分区" min-width="130" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.unassignedZone" type="warning" effect="plain" size="small">未分配</el-tag>
                <el-tag v-else type="primary" effect="light" size="small">{{ row.zoneName }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="stockQuantity" label="现存量" min-width="90" align="center" />
            <el-table-column prop="safetyStock" label="下限" min-width="90" align="center" />
            <el-table-column prop="gapQuantity" label="缺口" min-width="90" align="center" />
            <el-table-column label="补货数量" min-width="150" align="center">
              <template #default="{ row }">
                <el-input-number
                  v-if="editable"
                  v-model="quantityMap[row.id]"
                  :min="1"
                  :max="row.gapQuantity"
                  :precision="0"
                  :step="1"
                  controls-position="right"
                  style="width: 130px"
                  @change="markDirty"
                />
                <span v-else>{{ row.replenishQuantity }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="specUnit" label="单位" min-width="80" align="center">
              <template #default="{ row }">{{ row.specUnit || '-' }}</template>
            </el-table-column>
          </el-table>
        </el-card>

        <div v-if="editable" class="detail-footer">
          <div class="footer-hint">
            草稿不占用配件待补标记；补货数量不超过缺口，调整后请先“保存数量”。提交后档案回填补货单号与待补数量且数量锁定。
          </div>
          <div>
            <el-button :loading="saving" :disabled="!hasDirty" @click="handleSaveQuantities">保存数量</el-button>
            <el-button type="success" :loading="submitting" @click="handleSubmit">提交补货单</el-button>
          </div>
        </div>
        <div v-else-if="detail.header.status === 1" class="detail-footer">
          <div class="footer-hint">补货单已提交，配件档案可见补货单号与待补数量，数量不可再修改。</div>
          <el-button type="danger" plain :loading="cancelling" @click="openCancelDialog">作废补货单</el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 作废原因对话框：作废后清除档案待补标记 -->
    <el-dialog v-model="cancelDialogVisible" title="作废补货单" width="480px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        class="cancel-alert"
        title="作废后单据内全部配件档案上的补货单号与待补数量将被清除，单据本身只读留档不可恢复。"
      />
      <el-form label-width="80px">
        <el-form-item label="作废原因">
          <el-input
            v-model="cancelReason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="可填写作废原因（选填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="cancelling" @click="handleCancelConfirm">确认作废</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, ShoppingCart, Box } from '@element-plus/icons-vue'
import {
  getReplenishOrderPage,
  getReplenishOrderById,
  updateReplenishOrderItems,
  submitReplenishOrder,
  cancelReplenishOrder,
  deleteReplenishOrder
} from '@/api/replenishOrder'
import { notifyStockChanged, onStockChanged } from '@/utils/stockSync'

const router = useRouter()
const route = useRoute()

const tableData = ref([])
const loading = ref(false)

const searchForm = reactive({
  status: null
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const detailVisible = ref(false)
const detail = ref(null)
const detailLoading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const cancelling = ref(false)
// 明细补货数量本地录入值（itemId -> 数量）
const quantityMap = reactive({})
const dirty = ref(false)

const cancelDialogVisible = ref(false)
const cancelReason = ref('')

const editable = computed(() => detail.value?.header?.status === 0)
const hasDirty = computed(() => dirty.value)

const statusTagType = (status) => {
  if (status === 1) return 'success'
  if (status === 2) return 'info'
  return 'warning'
}

const markDirty = () => {
  dirty.value = true
}

const itemRowClassName = ({ row }) => {
  if (row.accessoryDeleted || row.pendingConflict) return 'conflict-row'
  return ''
}

const goLedger = () => {
  router.push('/safety-stock')
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getReplenishOrderPage({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      status: searchForm.status
    })
    tableData.value = res.records
    pagination.total = res.total
    if (res.records.length === 0 && res.total > 0 && pagination.pageNum > 1) {
      pagination.pageNum = 1
      await loadData()
    }
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
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

const openDetail = async (id) => {
  detailVisible.value = true
  await loadDetail(id)
}

const loadDetail = async (id) => {
  detailLoading.value = true
  try {
    const data = await getReplenishOrderById(id)
    detail.value = data
    Object.keys(quantityMap).forEach((key) => delete quantityMap[key])
    data.items.forEach((item) => {
      quantityMap[item.id] = item.replenishQuantity
    })
    dirty.value = false
  } finally {
    detailLoading.value = false
  }
}

const handleSaveQuantities = async () => {
  if (!detail.value) return
  const items = detail.value.items.map((item) => ({
    itemId: item.id,
    replenishQuantity: quantityMap[item.id]
  }))
  if (items.some((item) => item.replenishQuantity == null || item.replenishQuantity < 1)) {
    ElMessage.warning('每种配件的补货数量都必须是正整数')
    return
  }
  saving.value = true
  try {
    await updateReplenishOrderItems(detail.value.header.id, items)
    ElMessage.success('补货数量已保存')
    dirty.value = false
    await loadDetail(detail.value.header.id)
    loadData()
  } finally {
    saving.value = false
  }
}

const handleSubmit = () => {
  if (!detail.value) return
  if (dirty.value) {
    ElMessage.warning('补货数量有未保存的修改，请先点击“保存数量”后再提交')
    return
  }
  if (detail.value.conflictCount > 0) {
    ElMessage.warning('有配件已删除或已被其他补货单占用，请刷新处理后再提交')
    return
  }
  const total = detail.value.header.totalQuantity
  ElMessageBox.confirm(
    `提交后将把本单（${detail.value.items.length} 种、合计 ${total} 件）的补货单号与待补数量回写到配件档案，`
      + '补货数量随即锁定不可再改。确认提交吗？',
    '提交补货单',
    {
      confirmButtonText: '确认提交',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
    .then(async () => {
      submitting.value = true
      try {
        await submitReplenishOrder(detail.value.header.id)
        ElMessage.success('补货单已提交，配件档案已回填补货单号与待补数量')
        await loadDetail(detail.value.header.id)
        loadData()
        // 档案待补标记变化：通知配件档案与安全库存台账刷新（台账需禁用已占用配件）
        notifyStockChanged('replenish-order')
      } finally {
        submitting.value = false
      }
    })
    .catch(() => {})
}

const openCancelDialog = () => {
  cancelReason.value = ''
  cancelDialogVisible.value = true
}

const handleCancelConfirm = async () => {
  if (!detail.value) return
  cancelling.value = true
  try {
    await cancelReplenishOrder(detail.value.header.id, cancelReason.value || null)
    ElMessage.success('补货单已作废，配件档案待补标记已清除')
    cancelDialogVisible.value = false
    await loadDetail(detail.value.header.id)
    loadData()
    notifyStockChanged('replenish-order')
  } finally {
    cancelling.value = false
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(
    `待提交补货单 ${row.replenishNo} 删除后不可恢复（草稿未占用配件待补标记），确认删除吗？`,
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
    .then(async () => {
      await deleteReplenishOrder(row.id)
      ElMessage.success('删除成功')
      loadData()
    })
    .catch(() => {})
}

// 其他页面（如台账生成新单、作废）后列表状态保持一致
const handleStockChanged = () => {
  loadData()
  if (detail.value && detailVisible.value) {
    loadDetail(detail.value.header.id)
  }
}

let unsubscribeStockChanged = null

onMounted(async () => {
  await loadData()
  unsubscribeStockChanged = onStockChanged(handleStockChanged)
  // 从台账“生成补货单”跳转而来：列表加载完直接打开新草稿，减少回列表找单的操作
  if (route.query.open) {
    const openId = Number(route.query.open)
    if (!Number.isNaN(openId)) {
      await openDetail(openId)
    }
    router.replace({ path: route.path })
  }
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

.total-quantity {
  color: #f56c6c;
  font-weight: 700;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
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

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
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

.cancel-reason {
  margin-top: 10px;
  font-size: 13px;
  color: #e6a23c;
}

.zone-total-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
  font-weight: 600;
  color: #303133;
}

.conflict-alert {
  margin-bottom: 12px;
}

.cancel-alert {
  margin-bottom: 16px;
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

.footer-hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

:deep(.conflict-row) {
  background-color: #fef0f0;
}
</style>
