<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="请输入配件名称/型号/材质"
            clearable
            style="width: 240px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="所属分区">
          <el-select
            v-model="searchForm.zoneTagId"
            placeholder="全部"
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
          <el-icon color="#409EFF"><Goods /></el-icon>
          <span>配件档案列表</span>
        </div>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增配件
        </el-button>
      </div>

      <el-table :data="tableData" border stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="accessoryName" label="配件名称" min-width="140" />
        <el-table-column prop="model" label="型号" min-width="140" />
        <el-table-column prop="material" label="材质" min-width="100" />
        <el-table-column prop="scene" label="适配布线场景" min-width="160" />
        <el-table-column label="规格区间" min-width="140" align="center">
          <template #default="{ row }">
            <span v-if="row.specMin != null || row.specMax != null">
              {{ row.specMin ?? '-' }} ~ {{ row.specMax ?? '-' }}
              <span v-if="row.specUnit">{{ row.specUnit }}</span>
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="所属分区" min-width="140" align="center">
          <template #default="{ row }">
            <el-tag v-if="getZoneTagName(row.zoneTagId)" type="primary" effect="light">
              {{ getZoneTagName(row.zoneTagId) }}
            </el-tag>
            <el-tag v-else type="warning" effect="plain">未分配</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="现存量" min-width="120" align="center">
          <template #default="{ row }">
            <span :class="{ 'stock-zero': !row.stockQuantity }">{{ row.stockQuantity ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" align="center" />
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="success" @click="handleChangeZone(row)">调整分区</el-button>
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
      width="600px"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
        <el-form-item label="配件名称" prop="accessoryName">
          <el-input v-model="formData.accessoryName" placeholder="请输入配件名称" />
        </el-form-item>
        <el-form-item label="型号" prop="model">
          <el-input v-model="formData.model" placeholder="请输入型号" />
        </el-form-item>
        <el-form-item label="材质" prop="material">
          <el-input v-model="formData.material" placeholder="请输入材质" />
        </el-form-item>
        <el-form-item label="适配布线场景" prop="scene">
          <el-input v-model="formData.scene" placeholder="请输入适配布线场景" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="规格最小值" prop="specMin">
              <el-input-number
                v-model="formData.specMin"
                :min="0"
                :precision="2"
                :step="0.1"
                style="width: 100%"
                placeholder="最小值"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="规格最大值" prop="specMax">
              <el-input-number
                v-model="formData.specMax"
                :min="0"
                :precision="2"
                :step="0.1"
                style="width: 100%"
                placeholder="最大值"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="规格单位" prop="specUnit">
              <el-input v-model="formData.specUnit" placeholder="如 mm" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="所属分区" prop="zoneTagId">
          <el-select
            v-model="formData.zoneTagId"
            placeholder="可暂不分配分区"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="item in zoneTagList"
              :key="item.id"
              :label="item.tagName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="现存量" prop="stockQuantity">
          <el-input-number
            v-model="formData.stockQuantity"
            :min="0"
            :precision="0"
            :step="1"
            style="width: 200px"
            placeholder="库房现存数量"
          />
          <span class="form-hint">用于与已启用方案的需求合计比对库存缺口</span>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="zoneDialogVisible" title="调整所属分区" width="400px">
      <el-form label-width="100px">
        <el-form-item label="配件名称">
          <span>{{ currentRow?.accessoryName }}</span>
        </el-form-item>
        <el-form-item label="当前分区">
          <el-tag type="info" v-if="getZoneTagName(currentRow?.zoneTagId)">
            {{ getZoneTagName(currentRow?.zoneTagId) }}
          </el-tag>
          <span v-else>未分配</span>
        </el-form-item>
        <el-form-item label="目标分区">
          <el-select v-model="targetZoneId" placeholder="请选择目标分区" clearable style="width: 100%">
            <el-option
              v-for="item in zoneTagList"
              :key="item.id"
              :label="item.tagName"
              :value="item.id"
            />
          </el-select>
          <div class="form-hint">清空选择后确定即设为未分配分区，未分配分区的配件会在缺口列表中单独列出</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="zoneDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleZoneSubmit">确定调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Goods } from '@element-plus/icons-vue'
import {
  getAccessoryPage,
  addAccessory,
  updateAccessory,
  deleteAccessory,
  updateAccessoryZone
} from '@/api/accessory'
import { getZoneTagList } from '@/api/zoneTag'

const tableData = ref([])
const zoneTagList = ref([])

const searchForm = reactive({
  keyword: '',
  zoneTagId: null
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const dialogVisible = ref(false)
const dialogTitle = computed(() => (formData.id ? '编辑配件' : '新增配件'))
const formRef = ref()
const formData = reactive({
  id: null,
  accessoryName: '',
  model: '',
  material: '',
  scene: '',
  specMin: null,
  specMax: null,
  specUnit: '',
  zoneTagId: null,
  stockQuantity: 0,
  remark: ''
})

const formRules = {
  accessoryName: [{ required: true, message: '请输入配件名称', trigger: 'blur' }],
  model: [{ required: true, message: '请输入型号', trigger: 'blur' }],
  stockQuantity: [{ required: true, message: '请输入现存量', trigger: 'change' }]
}

const zoneDialogVisible = ref(false)
const currentRow = ref(null)
const targetZoneId = ref(null)

const loadData = async () => {
  const res = await getAccessoryPage({
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize,
    keyword: searchForm.keyword,
    zoneTagId: searchForm.zoneTagId
  })
  tableData.value = res.records
  pagination.total = res.total
}

const loadZoneTagList = async () => {
  zoneTagList.value = await getZoneTagList()
}

const getZoneTagName = (id) => {
  const tag = zoneTagList.value.find(item => item.id === id)
  return tag ? tag.tagName : ''
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.zoneTagId = null
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

const handleEdit = (row) => {
  Object.assign(formData, row)
  dialogVisible.value = true
}

const handleDelete = (row) => {
  ElMessageBox.confirm(
    '删除后该配件将从配件档案中隐藏；若仍被布线方案引用，会在方案明细与缺口列表中显示为“配件已删除”，且不可核销出库。确定删除吗？',
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
    .then(async () => {
      await deleteAccessory(row.id)
      ElMessage.success('删除成功')
      loadData()
    })
    .catch(() => {})
}

const handleSubmit = async () => {
  await formRef.value.validate()

  if (formData.specMin != null && formData.specMax != null) {
    if (formData.specMin > formData.specMax) {
      ElMessage.warning('规格最小值不能大于最大值')
      return
    }
  }

  if (formData.id) {
    await updateAccessory(formData)
    ElMessage.success('修改成功')
  } else {
    await addAccessory(formData)
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
  formData.accessoryName = ''
  formData.model = ''
  formData.material = ''
  formData.scene = ''
  formData.specMin = null
  formData.specMax = null
  formData.specUnit = ''
  formData.zoneTagId = null
  formData.stockQuantity = 0
  formData.remark = ''
  formRef.value?.clearValidate()
}

const handleChangeZone = (row) => {
  currentRow.value = row
  targetZoneId.value = row.zoneTagId
  zoneDialogVisible.value = true
}

const handleZoneSubmit = async () => {
  // targetZoneId 为空时传 null，后端将该配件置为未分配分区
  await updateAccessoryZone(currentRow.value.id, targetZoneId.value ?? null)
  ElMessage.success(targetZoneId.value ? '分区调整成功' : '已设为未分配分区')
  zoneDialogVisible.value = false
  loadData()
}

onMounted(() => {
  loadZoneTagList()
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

.stock-zero {
  color: #f56c6c;
  font-weight: 600;
}

.form-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}
</style>
