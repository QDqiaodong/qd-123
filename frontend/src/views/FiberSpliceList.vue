<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="接头编号">
          <el-input
            v-model="searchForm.keyword"
            placeholder="按接头编号搜索"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="所属分区">
          <el-select
            v-model="searchForm.zoneTagId"
            placeholder="全部分区"
            clearable
            style="width: 180px"
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
        <el-form-item label="OTDR">
          <el-select
            v-model="searchForm.otdrPassed"
            placeholder="全部"
            clearable
            style="width: 140px"
            @change="handleSearch"
          >
            <el-option label="已过 OTDR" :value="1" />
            <el-option label="未过 OTDR" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="可投运">
          <el-select
            v-model="searchForm.commissionable"
            placeholder="全部"
            clearable
            style="width: 130px"
            @change="handleSearch"
          >
            <el-option label="可投运" :value="1" />
            <el-option label="不可投运" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="档案状态">
          <el-select
            v-model="searchForm.status"
            placeholder="全部状态"
            clearable
            style="width: 130px"
            @change="handleSearch"
          >
            <el-option label="在档" :value="0" />
            <el-option label="已作废" :value="1" />
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
          <el-icon color="#409EFF"><Link /></el-icon>
          <span>光纤熔接接头登记</span>
        </div>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          登记接头
        </el-button>
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="rule-alert"
        title="仓管单独建账：登记接头编号、所属分区、盘留米数与是否过 OTDR，接头编号唯一。列表可按所属分区筛选。未通过 OTDR 的接头不能标记“可投运”；已标记可投运的接头需先取消标记才能改为未过 OTDR。作废只置“已作废”留档（记录作废原因与时间），不做物理删除，作废后只读；把档案状态筛为“已作废”即可查档。"
      />

      <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="spliceNo" label="接头编号" min-width="140" />
        <el-table-column label="所属分区" min-width="130">
          <template #default="{ row }">
            {{ row.zoneName }}
            <el-tag v-if="row.zoneDeleted" size="small" type="info" effect="plain">分区已删</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="盘留米数" min-width="110" align="center">
          <template #default="{ row }">
            <span class="meters-text">{{ row.reserveMeters }}</span>
            <span class="unit-text">m</span>
          </template>
        </el-table-column>
        <el-table-column label="OTDR" min-width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.otdrPassed === 1 ? 'success' : 'danger'" effect="plain">
              {{ row.otdrPassedText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="可投运" min-width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.commissionable === 1 ? 'success' : 'info'" effect="light">
              {{ row.commissionableText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="档案状态" min-width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'danger' : ''" :effect="row.status === 1 ? 'dark' : 'plain'">
              {{ row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="作废原因/时间" min-width="200">
          <template #default="{ row }">
            <template v-if="row.status === 1">
              <div class="void-reason">{{ row.voidReason || '（未填原因）' }}</div>
              <div class="void-time">{{ row.voidTime || '-' }}</div>
            </template>
            <span v-else class="placeholder-text">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140">
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="登记时间" min-width="170" align="center" />
        <el-table-column label="操作" width="250" fixed="right" align="center">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
              <el-button
                v-if="row.commissionable !== 1"
                link
                type="success"
                :disabled="row.otdrPassed !== 1"
                :title="row.otdrPassed !== 1 ? '未过 OTDR，不能标记可投运' : ''"
                @click="handleCommission(row, 1)"
              >
                标可投运
              </el-button>
              <el-button v-else link type="warning" @click="handleCommission(row, 0)">
                取消可投运
              </el-button>
              <el-button link type="danger" @click="openVoidDialog(row)">作废</el-button>
            </template>
            <el-tag v-else size="small" type="info" effect="plain">已留档，只读</el-tag>
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

    <!-- 登记/编辑对话框：取消/关闭只关窗，绝不发请求；编辑时接头编号只读不可改 -->
    <el-dialog
      v-model="formDialogVisible"
      :title="editingId == null ? '登记光纤熔接接头' : `编辑接头 ${form.spliceNo}`"
      width="500px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <el-form-item label="接头编号" prop="spliceNo">
          <el-input
            v-model="form.spliceNo"
            placeholder="请输入接头编号（唯一，不能重复登记）"
            clearable
            :disabled="editingId != null"
          />
        </el-form-item>
        <el-form-item label="所属分区" prop="zoneTagId">
          <el-select v-model="form.zoneTagId" placeholder="请选择所属分区" style="width: 100%">
            <el-option
              v-for="item in zoneTagList"
              :key="item.id"
              :label="item.tagName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="盘留米数" prop="reserveMeters">
          <el-input-number
            v-model="form.reserveMeters"
            :min="0"
            :precision="0"
            :step="1"
            controls-position="right"
            style="width: 200px"
          />
          <span class="unit-inline">米</span>
        </el-form-item>
        <el-form-item label="是否过 OTDR" prop="otdrPassed">
          <el-radio-group v-model="form.otdrPassed">
            <el-radio :value="0">未过</el-radio>
            <el-radio :value="1">已过</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item>
          <div class="form-hint">登记后一律为“不可投运”；只有已过 OTDR 的接头才能在列表中标记“可投运”。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ editingId == null ? '确认登记' : '保存修改' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 作废对话框：只置“已作废”留档，不物理删除；取消不发请求 -->
    <el-dialog v-model="voidDialogVisible" title="作废光纤熔接接头" width="460px">
      <div v-if="voidTarget" class="void-summary">
        <div>接头编号：<b>{{ voidTarget.spliceNo }}</b></div>
        <div>所属分区：{{ voidTarget.zoneName }}｜盘留 {{ voidTarget.reserveMeters }} 米</div>
        <div class="void-warn">作废后该接头只读留档，不可再编辑或标记可投运；系统不会物理删除该记录。</div>
      </div>
      <el-form label-width="90px">
        <el-form-item label="作废原因">
          <el-input
            v-model="voidReason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="可填写作废原因（选填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="voidDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="voiding" @click="handleVoid">确认作废留档</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Plus, Link } from '@element-plus/icons-vue'
import {
  getFiberSplicePage,
  createFiberSplice,
  updateFiberSplice,
  commissionFiberSplice,
  voidFiberSplice
} from '@/api/fiberSplice'
import { getZoneTagList } from '@/api/zoneTag'

const tableData = ref([])
const zoneTagList = ref([])
const loading = ref(false)

const searchForm = reactive({
  keyword: '',
  zoneTagId: null,
  otdrPassed: null,
  commissionable: null,
  status: 0
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const formDialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const editingId = ref(null)
const form = reactive({
  spliceNo: '',
  zoneTagId: null,
  reserveMeters: 0,
  otdrPassed: 0,
  remark: ''
})
const formRules = {
  spliceNo: [
    { required: true, message: '请填写接头编号', trigger: 'blur' },
    { whitespace: true, message: '接头编号不能为纯空格', trigger: 'blur' },
    { max: 60, message: '接头编号长度不能超过60个字符', trigger: 'blur' }
  ],
  zoneTagId: [
    { required: true, message: '请选择所属分区', trigger: 'change' }
  ],
  reserveMeters: [
    { required: true, message: '请填写盘留米数', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value == null || Number.isNaN(value) || value < 0) {
          callback(new Error('盘留米数必须为非负整数'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  otdrPassed: [
    { required: true, message: '请选择是否过 OTDR', trigger: 'change' }
  ]
}

const voidDialogVisible = ref(false)
const voiding = ref(false)
const voidTarget = ref(null)
const voidReason = ref('')

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
    if (searchForm.zoneTagId != null) {
      params.zoneTagId = searchForm.zoneTagId
    }
    if (searchForm.otdrPassed != null) {
      params.otdrPassed = searchForm.otdrPassed
    }
    if (searchForm.commissionable != null) {
      params.commissionable = searchForm.commissionable
    }
    if (searchForm.status != null) {
      params.status = searchForm.status
    }
    const res = await getFiberSplicePage(params)
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

const loadZoneTags = async () => {
  zoneTagList.value = await getZoneTagList()
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.zoneTagId = null
  searchForm.otdrPassed = null
  searchForm.commissionable = null
  searchForm.status = 0
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

const resetForm = () => {
  form.spliceNo = ''
  form.zoneTagId = null
  form.reserveMeters = 0
  form.otdrPassed = 0
  form.remark = ''
}

// 每次打开登记窗都重置为空白表单；取消只关窗，不调用任何接口
const openCreateDialog = () => {
  editingId.value = null
  resetForm()
  formDialogVisible.value = true
}

const openEditDialog = (row) => {
  if (row.status === 1) {
    ElMessage.warning('该接头已作废留档，不能再编辑')
    return
  }
  editingId.value = row.id
  form.spliceNo = row.spliceNo
  form.zoneTagId = row.zoneTagId
  form.reserveMeters = row.reserveMeters
  form.otdrPassed = row.otdrPassed
  form.remark = row.remark || ''
  formDialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    if (editingId.value == null) {
      await createFiberSplice({
        spliceNo: form.spliceNo.trim(),
        zoneTagId: form.zoneTagId,
        reserveMeters: form.reserveMeters,
        otdrPassed: form.otdrPassed,
        remark: form.remark?.trim() || ''
      })
      ElMessage.success('登记成功（需通过 OTDR 后才能标记可投运）')
    } else {
      await updateFiberSplice(editingId.value, {
        zoneTagId: form.zoneTagId,
        reserveMeters: form.reserveMeters,
        otdrPassed: form.otdrPassed,
        remark: form.remark?.trim() || ''
      })
      ElMessage.success('保存成功')
    }
    formDialogVisible.value = false
    await loadData()
  } finally {
    submitting.value = false
  }
}

const handleCommission = async (row, commissionable) => {
  try {
    await commissionFiberSplice(row.id, commissionable)
    ElMessage.success(commissionable === 1 ? '已标记为可投运' : '已取消可投运标记')
    await loadData()
  } catch (e) {
    // 未过 OTDR 等业务拒绝原因由请求拦截器统一弹错，这里不再重复提示
  }
}

const openVoidDialog = (row) => {
  voidTarget.value = row
  voidReason.value = ''
  voidDialogVisible.value = true
}

const handleVoid = async () => {
  if (!voidTarget.value) return
  voiding.value = true
  try {
    await voidFiberSplice(voidTarget.value.id, voidReason.value.trim())
    ElMessage.success('已作废留档（未物理删除，可在“已作废”筛选中查档）')
    voidDialogVisible.value = false
    voidTarget.value = null
    await loadData()
  } finally {
    voiding.value = false
  }
}

onMounted(() => {
  loadZoneTags()
  loadData()
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

.void-reason {
  color: #f56c6c;
  font-size: 13px;
}

.void-time {
  color: #909399;
  font-size: 12px;
  margin-top: 2px;
}

.placeholder-text {
  color: #c0c4cc;
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

.void-summary {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  margin-bottom: 16px;
  background-color: #fef0f0;
  border: 1px solid #fde2e2;
  border-radius: 6px;
  font-size: 13px;
  color: #303133;
}

.void-warn {
  color: #f56c6c;
}
</style>
