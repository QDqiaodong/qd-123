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
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
