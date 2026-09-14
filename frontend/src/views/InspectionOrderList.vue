<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="送检单号/配件">
          <el-input
            v-model="searchForm.keyword"
            placeholder="按送检单号或配件名称搜索"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="是否已回样">
          <el-select
            v-model="searchForm.sampleReturned"
            placeholder="全部"
            clearable
            style="width: 140px"
            @change="handleSearch"
          >
            <el-option label="已回样" :value="1" />
            <el-option label="待回样" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="合格状态">
          <el-select
            v-model="searchForm.qualified"
            placeholder="全部"
            clearable
            style="width: 140px"
            @change="handleSearch"
          >
            <el-option label="合格" :value="1" />
            <el-option label="未判定" :value="0" />
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
          <el-icon color="#409EFF"><Stamp /></el-icon>
          <span>辅材送检单</span>
        </div>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          新建送检单
        </el-button>
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="rule-alert"
        title="仓管按已建档配件新建送检单，送检批次与实验室名称必须填完整，缺一不能提交；新建后单据处于“待回样”。实验室写回结论后单据才变为“已回样”，未回样不能标记合格；已回样且判定合格的可取消合格标记。列表可按是否已回样筛选，筛选条件与回样结论刷新后仍在。"
      />

      <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="inspectionNo" label="送检单号" min-width="170" />
        <el-table-column label="送检配件" min-width="180">
          <template #default="{ row }">
            <div class="accessory-name">{{ row.accessoryName }}</div>
            <div class="accessory-model">{{ row.model }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="batchNo" label="送检批次" min-width="130" />
        <el-table-column prop="labName" label="实验室" min-width="150" />
        <el-table-column label="回样状态" min-width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.sampleReturned === 1 ? 'success' : 'warning'" effect="plain">
              {{ row.sampleReturnedText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="合格状态" min-width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.qualified === 1" type="success" effect="dark">合格</el-tag>
            <el-tag v-else-if="row.sampleReturned === 1" type="info" effect="plain">未判定合格</el-tag>
            <el-tag v-else type="info" effect="plain">待回样</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="回样结论" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.sampleReturned === 1" class="conclusion-text">{{ row.labConclusion }}</span>
            <span v-else class="placeholder-text">待回样后写回</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="送检时间" min-width="170" align="center" />
        <el-table-column label="回样时间" min-width="170" align="center">
          <template #default="{ row }">{{ row.sampleReturnTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.sampleReturned === 0"
              link
              type="primary"
              @click="openResultDialog(row)"
            >
              写回结论
            </el-button>
            <template v-else>
              <el-button
                v-if="row.qualified !== 1"
                link
                type="success"
                @click="handleQualify(row, 1)"
              >
                标合格
              </el-button>
              <el-button v-else link type="warning" @click="handleQualify(row, 0)">
                取消合格
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

    <!-- 新建送检单：取消/关闭只关窗不发请求；送检批次、实验室名称缺一不能提交 -->
    <el-dialog v-model="createDialogVisible" title="新建辅材送检单" width="500px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="送检配件" prop="accessoryId">
          <el-select
            v-model="createForm.accessoryId"
            placeholder="请选择已建档配件"
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
        <el-form-item label="送检批次" prop="batchNo">
          <el-input v-model="createForm.batchNo" placeholder="请填写送检批次" clearable maxlength="100" />
        </el-form-item>
        <el-form-item label="实验室名称" prop="labName">
          <el-input v-model="createForm.labName" placeholder="请填写实验室名称" clearable maxlength="200" />
        </el-form-item>
        <el-form-item>
          <div class="form-hint">送检批次与实验室名称填完整后才能提交；提交后单据处于“待回样”，实验室写回结论前不能标记合格。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">提交送检单</el-button>
      </template>
    </el-dialog>

    <!-- 写回回样结论：结论必填（纯空白不能写回），写回后单据变为已回样，结论随单持久化刷新仍在 -->
    <el-dialog v-model="resultDialogVisible" title="实验室回样结论写回" width="520px">
      <div v-if="resultTarget" class="result-summary">
        <div>送检单号：<b>{{ resultTarget.inspectionNo }}</b></div>
        <div>送检配件：{{ resultTarget.accessoryName }}（{{ resultTarget.model }}）</div>
        <div>送检批次：{{ resultTarget.batchNo }}｜实验室：{{ resultTarget.labName }}</div>
      </div>
      <el-form label-width="92px">
        <el-form-item label="回样结论" required>
          <el-input
            v-model="labConclusion"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="请填写实验室写回的检测结论，不填无法回样"
          />
          <div class="form-hint">结论写回后单据由“待回样”变为“已回样”，之后才能标记合格；结论随单保存，刷新后仍可查看。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resultDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="resultWriting"
          :disabled="!labConclusion.trim()"
          @click="handleWriteResult"
        >
          确认回样
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Plus, Stamp } from '@element-plus/icons-vue'
import {
  getInspectionPage,
  createInspection,
  writeInspectionResult,
  qualifyInspection
} from '@/api/inspectionOrder'
import { getAccessoryPage } from '@/api/accessory'

// 筛选条件本地持久化键：刷新页面后自动恢复“是否已回样”等筛选；回样结论由后端持久化，随列表重拉仍在
const FILTER_STORAGE_KEY = 'inspection-order-filters'

const tableData = ref([])
const accessoryOptions = ref([])
const loading = ref(false)

const searchForm = reactive({
  keyword: '',
  sampleReturned: null,
  qualified: null
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
  accessoryId: null,
  batchNo: '',
  labName: ''
})
const createRules = {
  accessoryId: [
    { required: true, message: '请选择已建档配件', trigger: 'change' }
  ],
  batchNo: [
    { required: true, message: '请填写送检批次', trigger: 'blur' },
    { whitespace: true, message: '送检批次不能为纯空格', trigger: 'blur' },
    { max: 100, message: '送检批次长度不能超过100个字符', trigger: 'blur' }
  ],
  labName: [
    { required: true, message: '请填写实验室名称', trigger: 'blur' },
    { whitespace: true, message: '实验室名称不能为纯空格', trigger: 'blur' },
    { max: 200, message: '实验室名称长度不能超过200个字符', trigger: 'blur' }
  ]
}

const resultDialogVisible = ref(false)
const resultWriting = ref(false)
const resultTarget = ref(null)
const labConclusion = ref('')

const saveFilters = () => {
  try {
    localStorage.setItem(FILTER_STORAGE_KEY, JSON.stringify({
      keyword: searchForm.keyword,
      sampleReturned: searchForm.sampleReturned,
      qualified: searchForm.qualified
    }))
  } catch (e) {
    // localStorage 不可用（隐私模式等）时退化为仅当次会话生效，不影响送检流程
  }
}

const restoreFilters = () => {
  try {
    const raw = localStorage.getItem(FILTER_STORAGE_KEY)
    if (!raw) return
    const saved = JSON.parse(raw)
    if (typeof saved.keyword === 'string') searchForm.keyword = saved.keyword
    if (saved.sampleReturned === 0 || saved.sampleReturned === 1) {
      searchForm.sampleReturned = saved.sampleReturned
    }
    if (saved.qualified === 0 || saved.qualified === 1) {
      searchForm.qualified = saved.qualified
    }
  } catch (e) {
    // 持久化内容损坏时忽略，按默认筛选加载
  }
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
    if (searchForm.sampleReturned != null) {
      params.sampleReturned = searchForm.sampleReturned
    }
    if (searchForm.qualified != null) {
      params.qualified = searchForm.qualified
    }
    const res = await getInspectionPage(params)
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
  // 新建送检单只能选已建档配件：配件档案分页接口默认不含已删除配件
  const res = await getAccessoryPage({ pageNum: 1, pageSize: 1000 })
  accessoryOptions.value = res.records || []
}

const handleSearch = () => {
  pagination.pageNum = 1
  saveFilters()
  loadData()
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.sampleReturned = null
  searchForm.qualified = null
  try {
    localStorage.removeItem(FILTER_STORAGE_KEY)
  } catch (e) {
    // 忽略 localStorage 不可用
  }
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

const resetCreateForm = () => {
  createForm.accessoryId = null
  createForm.batchNo = ''
  createForm.labName = ''
}

// 每次打开新建窗都重置为空白表单；取消只关窗，不调用任何接口
const openCreateDialog = () => {
  resetCreateForm()
  createDialogVisible.value = true
}

const handleCreate = async () => {
  if (!createFormRef.value) return
  try {
    await createFormRef.value.validate()
  } catch (e) {
    // 送检批次或实验室未填完整时表单拦截，不能提交
    return
  }
  creating.value = true
  try {
    await createInspection({
      accessoryId: createForm.accessoryId,
      batchNo: createForm.batchNo.trim(),
      labName: createForm.labName.trim()
    })
    ElMessage.success('送检单已提交，当前为待回样，实验室写回结论后才能标记合格')
    createDialogVisible.value = false
    pagination.pageNum = 1
    await loadData()
  } finally {
    creating.value = false
  }
}

const openResultDialog = (row) => {
  resultTarget.value = row
  labConclusion.value = ''
  resultDialogVisible.value = true
}

const handleWriteResult = async () => {
  if (!resultTarget.value) return
  const conclusion = labConclusion.value.trim()
  if (!conclusion) {
    ElMessage.warning('请填写实验室回样结论后再确认回样')
    return
  }
  resultWriting.value = true
  try {
    await writeInspectionResult(resultTarget.value.id, conclusion)
    ElMessage.success('回样结论已写回，单据已回样，可标记合格')
    resultDialogVisible.value = false
    resultTarget.value = null
    await loadData()
  } finally {
    resultWriting.value = false
  }
}

const handleQualify = async (row, qualified) => {
  try {
    await qualifyInspection(row.id, qualified)
    ElMessage.success(qualified === 1 ? '已标记为合格' : '已取消合格标记')
    await loadData()
  } catch (e) {
    // 未回样等业务拒绝原因由请求拦截器统一弹错，这里不再重复提示
  }
}

onMounted(() => {
  restoreFilters()
  loadAccessoryOptions()
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

.accessory-name {
  font-weight: 600;
  color: #303133;
}

.accessory-model {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.conclusion-text {
  color: #303133;
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

.result-summary {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  margin-bottom: 16px;
  background-color: #ecf5ff;
  border: 1px solid #d9ecff;
  border-radius: 6px;
  font-size: 13px;
  color: #303133;
}
</style>
