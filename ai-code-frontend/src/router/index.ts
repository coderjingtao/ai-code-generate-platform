import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
    },
    {
      path: '/user/login',
      name: 'user login',
      component: () => import('@/views/user/UserLoginView.vue'),
    },
    {
      path: '/user/register',
      name: 'user register',
      component: () => import('@/views/user/UserRegisterView.vue'),
    },
    {
      path: '/admin/userManagement',
      name: 'user management',
      component: () => import('@/views/admin/UserManagementView.vue'),
    },
    {
      path: '/admin/appManagement',
      name: 'app management',
      component: () => import('@/views/admin/AppManagementView.vue'),
    },
    {
      path: '/admin/chatManagement',
      name: 'chat management',
      component: () => import('@/views/admin/ChatManagementView.vue'),
    },
    {
      path: '/app/chat/:id',
      name: 'app chat',
      component: () => import('@/views/app/AppChatView.vue'),
    },
    {
      path: '/app/edit/:id',
      name: 'app edit',
      component: () => import('@/views/app/AppEditView.vue'),
    },
    {
      path: '/workspace',
      name: 'workspace',
      component: () => import('@/views/WorkspaceView.vue'),
    },
    {
      path: '/history',
      name: 'history',
      component: () => import('@/views/HistoryView.vue'),
    },
  ],
})

export default router
