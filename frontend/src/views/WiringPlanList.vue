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
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增方案
        </el-button>
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
        <el-table-column label="启用状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="(val) => handleStatusChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" align="center" />
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="success" @click="handleView(row)">详情</el-button>
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
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
          <el-descriptions-item label="创建时间">{{ currentPlan.createTime }}</el-descriptions-item>
          <el-descriptions-item label="方案说明" :span="2">
            {{ currentPlan.description || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="zone-detail-section">
          <div class="section-title">配件明细（按库房分区）</div>
          <el-empty v-if="zoneGroups.length === 0" description="暂无配件明细" :image-size="80" />
          <div v-for="group in zoneGroups" :key="group.zoneName" class="zone-group">
            <div class="zone-group-header">
              <el-tag type="primary" effect="light">{{ group.zoneName }}</el-tag>
              <span class="zone-group-count">共 {{ group.items.length }} 种配件</span>
            </div>
            <el-table :data="group.items" border size="small" style="width: 100%">
              <el-table-column prop="accessoryName" label="配件名称" min-width="160" />
              <el-table-column prop="model" label="型号" min-width="160" />
              <el-table-column prop="quantity" label="需求数量" width="120" align="center" />
            </el-table>
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Connection } from '@element-plus/icons-vue'
import {
  getWiringPlanPage,
  getWiringPlanById,
  addWiringPlan,
  updateWiringPlan,
  deleteWiringPlan,
  updateWiringPlanStatus
} from '@/api/wiringPlan'
import { getAccessoryPage } from '@/api/accessory'

const tableData = ref([])
const accessoryList = ref([])

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
  ElMessageBox.confirm('确定要删除该布线方案吗？存在关联配件明细的方案无法删除。', '提示', {
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
  try {
    await updateWiringPlanStatus(row.id, val)
    ElMessage.success(val === 1 ? '已启用' : '已停用')
  } catch (e) {
    loadData()
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
</style>
