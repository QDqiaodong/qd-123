<template>
  <div class="page-container">
    <el-card class="table-card">
      <div class="table-header">
        <div class="header-title">
          <el-icon color="#409EFF"><CollectionTag /></el-icon>
          <span>分区标签列表</span>
        </div>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增标签
        </el-button>
      </div>

      <el-table :data="tableData" border stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="tagName" label="标签名称" min-width="160" />
        <el-table-column prop="tagCode" label="标签编码" min-width="160" />
        <el-table-column prop="sortOrder" label="排序号" width="100" align="center" />
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="180" align="center" />
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="500px"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="标签名称" prop="tagName">
          <el-input v-model="formData.tagName" placeholder="请输入标签名称" />
        </el-form-item>
        <el-form-item label="标签编码" prop="tagCode">
          <el-input v-model="formData.tagCode" placeholder="请输入标签编码" />
        </el-form-item>
        <el-form-item label="排序号" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" style="width: 100%" />
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
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CollectionTag, Plus } from '@element-plus/icons-vue'
import {
  getZoneTagList,
  addZoneTag,
  updateZoneTag,
  deleteZoneTag
} from '@/api/zoneTag'

const tableData = ref([])

const dialogVisible = ref(false)
const dialogTitle = computed(() => (formData.id ? '编辑标签' : '新增标签'))
const formRef = ref()
const formData = reactive({
  id: null,
  tagName: '',
  tagCode: '',
  sortOrder: 0,
  remark: ''
})

const formRules = {
  tagName: [{ required: true, message: '请输入标签名称', trigger: 'blur' }],
  tagCode: [{ required: true, message: '请输入标签编码', trigger: 'blur' }]
}

const loadData = async () => {
  tableData.value = await getZoneTagList()
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
  ElMessageBox.confirm('确定要删除该分区标签吗？删除前请确保该分区下没有配件。', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      await deleteZoneTag(row.id)
      ElMessage.success('删除成功')
      loadData()
    })
    .catch(() => {})
}

const handleSubmit = async () => {
  await formRef.value.validate()

  if (formData.id) {
    await updateZoneTag(formData)
    ElMessage.success('修改成功')
  } else {
    await addZoneTag(formData)
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
  formData.tagName = ''
  formData.tagCode = ''
  formData.sortOrder = 0
  formData.remark = ''
  formRef.value?.clearValidate()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
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
</style>
