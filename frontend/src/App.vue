<template>
  <el-container class="layout-container">
    <el-header class="layout-header">
      <div class="header-title">
        <el-icon size="28" color="#409EFF"><Tools /></el-icon>
        <span>厂区安防布线配件存放分区归类系统</span>
      </div>
    </el-header>
    <el-container>
      <el-aside width="220px" class="layout-aside">
        <el-menu
          :default-active="activeMenu"
          router
          class="side-menu"
          background-color="#001529"
          text-color="#fff"
          active-text-color="#409EFF"
        >
          <el-menu-item index="/accessory">
            <el-icon><Goods /></el-icon>
            <span>配件档案管理</span>
          </el-menu-item>
          <el-menu-item index="/zone-tag">
            <el-icon><CollectionTag /></el-icon>
            <span>分区标签管理</span>
          </el-menu-item>
          <el-menu-item index="/wiring-plan">
            <el-icon><Connection /></el-icon>
            <span>布线方案管理</span>
          </el-menu-item>
          <el-menu-item index="/stock-gap">
            <el-icon><Warning /></el-icon>
            <span>库存缺口分析</span>
          </el-menu-item>
          <el-menu-item index="/safety-stock">
            <el-icon><Box /></el-icon>
            <span>安全库存台账</span>
          </el-menu-item>
          <el-menu-item index="/stock-check">
            <el-icon><DocumentChecked /></el-icon>
            <span>分区盘点</span>
          </el-menu-item>
          <el-menu-item index="/replenish-order">
            <el-icon><ShoppingCart /></el-icon>
            <span>补货单</span>
          </el-menu-item>
          <el-menu-item index="/cable-reel">
            <el-icon><Files /></el-icon>
            <span>整盘电源线建档</span>
          </el-menu-item>
          <el-menu-item index="/fiber-splice">
            <el-icon><Link /></el-icon>
            <span>光纤熔接接头登记</span>
          </el-menu-item>
          <el-menu-item index="/inspection-order">
            <el-icon><Stamp /></el-icon>
            <span>辅材送检单</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.layout-header {
  background: linear-gradient(90deg, #1890ff 0%, #096dd9 100%);
  display: flex;
  align-items: center;
  padding: 0 24px;
  color: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 10;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 20px;
  font-weight: 600;
}

.layout-aside {
  background-color: #001529;
}

.side-menu {
  border-right: none;
  height: 100%;
}

:deep(.el-menu-item) {
  height: 56px;
  line-height: 56px;
}

:deep(.el-menu-item:hover) {
  background-color: rgba(64, 158, 255, 0.1) !important;
}

:deep(.el-menu-item.is-active) {
  background-color: #1890ff !important;
}

.layout-main {
  background-color: #f0f2f5;
  padding: 20px;
}
</style>
