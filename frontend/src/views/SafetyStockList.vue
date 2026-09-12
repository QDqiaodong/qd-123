<template>
  <div class="page-container">
    <el-card class="summary-card">
      <div class="summary-header">
        <div class="header-title">
          <el-icon color="#F56C6C"><Box /></el-icon>
          <span>安全库存台账（现存量低于安全库存下限的配件，供采购补货）</span>
        </div>
        <el-button :loading="loading" @click="loadShortages">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
      <div class="summary-tags">
        <el-tag type="danger" effect="plain">待补货 {{ shortages.length }} 种</el-tag>
        <el-tag type="warning" effect="plain">其中未分配分区 {{ unassignedShortages.length }} 种</el-tag>
        <el-tag type="info" effect="plain">缺口合计 {{ totalGap }} 件</el-tag>
        <el-tag type="info" effect="plain">未设下限的配件不进台账</el-tag>
      </div>
    </el-card>

    <el-card class="table-card">
      <div class="table-header">
        <div class="header-title">
          <el-icon color="#409EFF"><List /></el-icon>
          <span>低于下限明细（已分配分区）</span>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="assignedShortages"
        border
        stripe
        class="assigned-table"
        row-class-name="shortage-row"
        style="width: 100%"
      >
        <el-table-column prop="accessoryName" label="名称" min-width="160" />
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
        <el-table-column label="缺口" min-width="110" align="center">
          <template #default="{ row }">
            <span class="shortage-text">{{ row.gapQuantity }}</span>
          </template>
        </el-table-column>
        <el-table-column label="规格单位" min-width="90" align="center">
          <template #default="{ row }">{{ row.specUnit || '-' }}</template>
        </el-table-column>
        <template #empty>已分配分区暂无低于下限的配件</template>
      </el-table>

      <!-- 未分配分区单独列出，低位也不能漏 -->
      <div class="unassigned-section">
        <div class="section-title">未分配分区（{{ unassignedShortages.length }}）</div>
        <el-empty
          v-if="unassignedShortages.length === 0"
          description="未分配分区暂无低于下限的配件"
          :image-size="60"
        />
        <el-table v-else :data="unassignedShortages" border size="small" row-class-name="unassigned-row" style="width: 100%">
          <el-table-column prop="accessoryName" label="名称" min-width="160" />
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
          <el-table-column label="缺口" width="100" align="center">
            <template #default="{ row }">
              <span class="shortage-text">{{ row.gapQuantity }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { Box, Refresh, List } from '@element-plus/icons-vue'
import { getSafetyStockShortages } from '@/api/accessory'
import { onStockChanged } from '@/utils/stockSync'

const shortages = ref([])
const loading = ref(false)

const assignedShortages = computed(() =>
  shortages.value.filter(item => !item.unassignedZone)
)
const unassignedShortages = computed(() =>
  shortages.value.filter(item => item.unassignedZone)
)
// 缺口合计直接对台账行求和，与后端口径一致（每行缺口 = 下限 - 现存量）
const totalGap = computed(() =>
  shortages.value.reduce((sum, item) => sum + (item.gapQuantity || 0), 0)
)

const loadShortages = async () => {
  loading.value = true
  try {
    // 台账实时取自配件档案：改下限或现存量后刷新，条数与缺口都与档案对得上
    shortages.value = (await getSafetyStockShortages()) || []
  } finally {
    loading.value = false
  }
}

// 配件档案保存下限/现存量、分区调整、方案核销或盘点回写后，立即按最新数据重算台账
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

.summary-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.shortage-text {
  color: #f56c6c;
  font-weight: 700;
}

:deep(.shortage-row) {
  background-color: #fef0f0;
}

:deep(.unassigned-row) {
  background-color: #fdf6ec;
}

.unassigned-section {
  margin-top: 20px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #e6a23c;
  margin-bottom: 12px;
}
</style>
