import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/clusters',
    children: [
      {
        path: 'clusters',
        name: 'Clusters',
        component: () => import('@/views/Clusters.vue')
      },
      {
        path: 'clusters/:clusterId/stacks',
        name: 'Stacks',
        component: () => import('@/views/Stacks.vue')
      },
      {
        path: 'clusters/:clusterId/stacks/:stackId',
        name: 'StackDetail',
        component: () => import('@/views/StackDetail.vue')
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('@/views/Users.vue')
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/clusters'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const authStore = useAuthStore()
  if (!to.meta.public && !authStore.username) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
