import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/accessory'
  },
  {
    path: '/accessory',
    name: 'Accessory',
    component: () => import('@/views/AccessoryList.vue'),
    meta: { title: '配件档案管理' }
  },
  {
    path: '/zone-tag',
    name: 'ZoneTag',
    component: () => import('@/views/ZoneTagList.vue'),
    meta: { title: '分区标签管理' }
  },
  {
    path: '/wiring-plan',
    name: 'WiringPlan',
    component: () => import('@/views/WiringPlanList.vue'),
    meta: { title: '布线方案管理' }
  },
  {
    path: '/stock-gap',
    name: 'StockGap',
    component: () => import('@/views/StockGapList.vue'),
    meta: { title: '库存缺口分析' }
  },
  {
    path: '/stock-check',
    name: 'StockCheck',
    component: () => import('@/views/StockCheckList.vue'),
    meta: { title: '分区盘点' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
