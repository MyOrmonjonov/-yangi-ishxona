
import { StrictMode, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { createRoot } from 'react-dom/client'
import {
  AlertTriangle,
  Archive,
  ArrowLeft,
  ArrowRightLeft,
  Bell,
  CalendarClock,
  Camera,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleUserRound,
  Clock3,
  Copy,
  FileText,
  Flag,
  Folder,
  HelpCircle,
  Image as ImageIcon,
  Languages,
  Layers3,
  ListFilter,
  LockKeyhole,
  LogOut,
  MessageSquare,
  Mic,
  Moon,
  Paperclip,
  Plus,
  Save,
  Send,
  Share2,
  MoreHorizontal,
  Play,
  RotateCcw,
  Search,
  Settings2,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  Square,
  Trash2,
  UsersRound,
  Volume2,
  VolumeX,
  X,
} from 'lucide-react'
import './styles.css'
import { I18nContext, LANGUAGES, translate, useI18n } from './i18n'
import type { Lang } from './i18n'
import { DesktopShell } from './desktop'

type Theme = 'system' | 'dark' | 'light'
type Screen = 'tasks' | 'create' | 'edit' | 'deadline' | 'edit-deadline' | 'settings' | 'workspace' | 'help' | 'groups' | 'members' | 'task-rules' | 'archive'
type Sheet = 'workspace' | 'workspace-switch' | 'scope' | 'task-place' | 'task-topic' | 'group-picker' | 'create-status' | 'create-priority' | 'edit-priority' | 'edit-status' | 'edit-place' | 'edit-topic' | 'edit-author' | 'edit-actions' | 'edit-share' | 'task-filter' | 'task-group' | 'appearance' | 'language' | null
export type TaskStatus = 'NEW' | 'IN_PROGRESS' | 'REVIEW' | 'BLOCKED' | 'COMPLETED' | 'CANCELLED'
export type TaskPriority = 'LOW' | 'NORMAL' | 'IMPORTANT' | 'URGENT'
export type TaskVisibility = 'PERSONAL' | 'WORKSPACE' | 'ONE_TO_ONE' | 'GROUP'
export type TaskView = 'ACTIVE' | 'MINE' | 'CREATED' | 'TODAY' | 'OVERDUE' | 'NO_DEADLINE' | 'UNASSIGNED' | 'COMPLETED' | 'ARCHIVE'
export type TaskGrouping = 'LIST' | 'STATUS' | 'ASSIGNEE'

export interface TaskDashboardFilter {
  view: TaskView
  statuses: TaskStatus[]
  assigneeIds: number[]
  priorities: TaskPriority[]
}

export interface Task {
  id: number
  groupId?: number
  topicId?: number
  topicName?: string
  title: string
  description?: string
  status: TaskStatus
  priority: TaskPriority
  dueAt?: string
  visibility: TaskVisibility
  checklist?: string
  files?: number
  groupName?: string
  assigneeIds?: number[]
  authorId?: number
  author?: string
  createdAt?: string
  updatedAt?: string
  assignees?: TaskPerson[]
  checklistItems?: TaskChecklistItem[]
  attachments?: TaskAttachment[]
  reminderMinutes?: number
  archivedAt?: string
}

export interface TaskPerson {
  id: number
  name: string
  username?: string
  photoUrl?: string
}

export interface TaskChecklistItem {
  id: number
  text: string
  done: boolean
}

export interface TaskAttachment {
  id: number
  name: string
  contentType?: string
  size: number
  url: string
  available?: boolean
}

export interface AuthResponse {
  accessToken: string
  user: {
    id: number
    telegramId?: number
    firstName: string
    lastName?: string
    username?: string
    photoUrl?: string
    uiLanguage?: Lang
    theme?: Theme
    remindersEnabled?: boolean
  }
  workspaces: Array<{ id: number; name: string; role: string }>
}

export interface WorkspaceMember {
  id: number
  firstName: string
  lastName?: string
  username?: string
  photoUrl?: string
  roleCode?: string
  active: boolean
  temporarilyBlocked: boolean
}

export interface LinkedGroup {
  id: number
  title: string
  members: number
  memberList: GroupMember[]
  botConnected: boolean
  botUsername?: string
  taskCreationPolicy?: string
}

export interface AvailableTelegramGroup {
  chatId: number
  title: string
}

export interface TelegramTopic {
  id: number
  name: string
}

export interface GroupMember {
  id: number
  name: string
  username?: string
  photoUrl?: string
}

interface ChecklistItem {
  id: number
  text: string
  done: boolean
}

export interface AttachmentItem {
  id: number
  name: string
  file: File
}

function isImageAttachment(name: string, contentType?: string) {
  return contentType?.startsWith('image/') === true
    || /\.(jpe?g|png|gif|webp|heic|heif)$/i.test(name)
}

function isAudioAttachment(name: string, contentType?: string) {
  return contentType?.startsWith('audio/') === true
    || /\.(ogg|oga|opus|mp3|wav|m4a|webm|aac|flac)$/i.test(name)
}

export function showTelegramMessage(message: string) {
  const webApp = window.Telegram?.WebApp
  if (webApp?.showAlert) webApp.showAlert(message)
  else window.alert(message)
}

export function showTelegramConfirm(message: string): Promise<boolean> {
  const webApp = window.Telegram?.WebApp
  if (webApp?.showConfirm) {
    return new Promise((resolve) => webApp.showConfirm!(message, resolve))
  }
  return Promise.resolve(window.confirm(message))
}

const MAX_ATTACHMENT_BYTES = 8 * 1024 * 1024
export const BROWSER_SESSION_KEY = 'taskapp_browser_session'

export function attachmentItems(files: FileList): { accepted: AttachmentItem[]; rejected: string[] } {
  const accepted: AttachmentItem[] = []
  const rejected: string[] = []
  Array.from(files).forEach((file, index) => {
    const name = file.name?.trim() || `camera-${Date.now()}-${index + 1}.jpg`
    if (file.size > MAX_ATTACHMENT_BYTES) {
      rejected.push(name)
      return
    }
    accepted.push({ id: Date.now() * 100 + index, name, file })
  })
  return { accepted, rejected }
}

export type Translator = (key: string, vars?: Record<string, string | number>) => string

export function statusLabel(t: Translator, status: TaskStatus) { return t(`status.${status}`) }
export function priorityLabel(t: Translator, priority: TaskPriority) { return t(`priority.${priority}`) }
export function placeLabel(t: Translator, visibility: TaskVisibility) { return t(`place.${visibility}`) }
export function reminderLabel(t: Translator, minutes: number) { return t(`reminder.${minutes}`) }

export const defaultTaskFilter: TaskDashboardFilter = {
  view: 'ACTIVE',
  statuses: [],
  assigneeIds: [],
  priorities: [],
}


function screenFromHash(): Screen {
  const value = window.location.hash.replace('#', '') as Screen
  return ['tasks', 'create', 'edit', 'deadline', 'edit-deadline', 'settings', 'workspace', 'help', 'groups', 'members', 'task-rules', 'archive']
    .includes(value) ? value : 'tasks'
}

function telegramProfileName() {
  const user = window.Telegram?.WebApp?.initDataUnsafe?.user
  if (!user) return 'Foydalanuvchi'
  const fullName = [user.first_name, user.last_name].filter(Boolean).join(' ').trim()
  return fullName || (user.username ? `@${user.username}` : 'Foydalanuvchi')
}

function authProfileName(user: AuthResponse['user']) {
  const fullName = [user.firstName, user.lastName].filter(Boolean).join(' ').trim()
  return fullName || (user.username ? `@${user.username}` : 'Foydalanuvchi')
}

function groupStorageKey() {
  const telegramId = window.Telegram?.WebApp?.initDataUnsafe?.user?.id ?? 'preview'
  return `taskapp.linked-groups.${telegramId}`
}

function storedGroups(): LinkedGroup[] {
  try {
    const value = localStorage.getItem(groupStorageKey())
    const parsed = value ? JSON.parse(value) as Array<Omit<LinkedGroup, 'memberList'> & { memberList?: GroupMember[] }> : []
    return parsed.map((group) => ({
      ...group,
      memberList: group.memberList ?? [],
      botConnected: group.botConnected ?? false,
    }))
  } catch {
    return []
  }
}

function useIsDesktop() {
  const [isDesktop, setIsDesktop] = useState(() => typeof window !== 'undefined' && window.innerWidth >= 1024)
  useEffect(() => {
    const onResize = () => setIsDesktop(window.innerWidth >= 1024)
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])
  return isDesktop
}

function App() {
  const isDesktop = useIsDesktop()
  const [screen, setScreen] = useState<Screen>(screenFromHash)
  const [sheet, setSheet] = useState<Sheet>(null)
  const [auth, setAuth] = useState<AuthResponse | null>(null)
  const [browserSession, setBrowserSession] = useState(false)
  const [tasks, setTasks] = useState<Task[]>([])
  const [archivedTasks, setArchivedTasks] = useState<Task[]>([])
  const [archivedLoading, setArchivedLoading] = useState(false)
  const [workspaceMembers, setWorkspaceMembers] = useState<WorkspaceMember[]>([])
  const [taskFilter, setTaskFilter] = useState<TaskDashboardFilter>(defaultTaskFilter)
  const [taskGrouping, setTaskGrouping] = useState<TaskGrouping>('STATUS')
  const [taskSearch, setTaskSearch] = useState('')
  const [scope, setScope] = useState<TaskVisibility>('WORKSPACE')
  const [scopeGroupId, setScopeGroupId] = useState<number | null>(null)
  const [profileName, setProfileName] = useState(telegramProfileName)
  const [workspaceName, setWorkspaceName] = useState(telegramProfileName)
  const [groups, setGroups] = useState<LinkedGroup[]>(storedGroups)
  const [groupPickerBusy, setGroupPickerBusy] = useState(false)
  const [availableGroups, setAvailableGroups] = useState<AvailableTelegramGroup[]>([])
  const [availableGroupsLoading, setAvailableGroupsLoading] = useState(false)
  const [linkingChatId, setLinkingChatId] = useState<number | null>(null)
  const [invitingMembers, setInvitingMembers] = useState(false)
  const [selectedGroupId, setSelectedGroupId] = useState<number | null>(null)
  const [selectedTopicId, setSelectedTopicId] = useState<number | null>(null)
  const [groupTopics, setGroupTopics] = useState<TelegramTopic[]>([])
  const [topicsLoading, setTopicsLoading] = useState(false)
  const [assigneeIds, setAssigneeIds] = useState<number[]>([])
  const [lang, setLang] = useState<Lang>('uz')
  const [theme, setTheme] = useState<Theme>('system')
  const [remindersEnabled, setRemindersEnabled] = useState(true)
  const [preferencesSaving, setPreferencesSaving] = useState(false)
  const [activeWorkspaceId, setActiveWorkspaceId] = useState<number | null>(null)
  const latestWorkspaceIdRef = useRef<number | null>(null)
  const [workspaceSwitching, setWorkspaceSwitching] = useState(false)
  const [newWorkspaceName, setNewWorkspaceName] = useState('')
  const [creatingWorkspace, setCreatingWorkspace] = useState(false)

  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [status, setStatus] = useState<TaskStatus>('NEW')
  const [priority, setPriority] = useState<TaskPriority>('NORMAL')
  const [visibility, setVisibility] = useState<TaskVisibility>('PERSONAL')
  const [deadline, setDeadline] = useState<string | undefined>()
  const [reminderMinutes, setReminderMinutes] = useState<number | undefined>()
  const [moreOpen, setMoreOpen] = useState(false)
  const [checklist, setChecklist] = useState<ChecklistItem[]>([])
  const [checklistDraft, setChecklistDraft] = useState('')
  const [attachments, setAttachments] = useState<AttachmentItem[]>([])
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null)
  const [editTitle, setEditTitle] = useState('')
  const [editDescription, setEditDescription] = useState('')
  const [editStatus, setEditStatus] = useState<TaskStatus>('NEW')
  const [editPriority, setEditPriority] = useState<TaskPriority>('NORMAL')
  const [editVisibility, setEditVisibility] = useState<TaskVisibility>('PERSONAL')
  const [editGroupId, setEditGroupId] = useState<number | null>(null)
  const [editTopicId, setEditTopicId] = useState<number | null>(null)
  const [editAssigneeIds, setEditAssigneeIds] = useState<number[]>([])
  const [editAuthorId, setEditAuthorId] = useState<number | null>(null)
  const [editDeadline, setEditDeadline] = useState<string | undefined>()
  const [editReminderMinutes, setEditReminderMinutes] = useState<number | undefined>()
  const [editChecklist, setEditChecklist] = useState<ChecklistItem[]>([])
  const [editChecklistDraft, setEditChecklistDraft] = useState('')
  const [editAttachments, setEditAttachments] = useState<AttachmentItem[]>([])
  const [editSaving, setEditSaving] = useState(false)
  const [editDetailsLoaded, setEditDetailsLoaded] = useState(false)
  const [editError, setEditError] = useState('')
  const galleryRef = useRef<HTMLInputElement | null>(null)
  const cameraRef = useRef<HTMLInputElement | null>(null)
  const fileRef = useRef<HTMLInputElement | null>(null)
  const editGalleryRef = useRef<HTMLInputElement | null>(null)
  const editCameraRef = useRef<HTMLInputElement | null>(null)
  const editFileRef = useRef<HTMLInputElement | null>(null)

  useEffect(() => {
    localStorage.setItem(groupStorageKey(), JSON.stringify(groups))
  }, [groups])

  useEffect(() => {
    const onHashChange = () => {
      setSheet(null)
      setScreen(screenFromHash())
      window.scrollTo({ top: 0, behavior: 'instant' })
    }
    window.addEventListener('hashchange', onHashChange)
    if (!window.location.hash) window.history.replaceState(null, '', '#tasks')
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])

  function applySession(session: AuthResponse) {
    setAuth(session)
    setProfileName(authProfileName(session.user))
    if (session.workspaces[0]) {
      latestWorkspaceIdRef.current = session.workspaces[0].id
      setActiveWorkspaceId(session.workspaces[0].id)
      setWorkspaceName(session.workspaces[0].name)
    }
    if (session.user.uiLanguage) setLang(session.user.uiLanguage)
    if (session.user.theme) setTheme(session.user.theme)
    if (session.user.remindersEnabled !== undefined) setRemindersEnabled(session.user.remindersEnabled)
    return Promise.all([loadTasks(session), loadGroups(session), loadWorkspaceMembers(session)])
  }

  useEffect(() => {
    const webApp = window.Telegram?.WebApp
    webApp?.ready()
    webApp?.expand()
    if (!webApp?.initData) {
      // Not opened inside Telegram - fall back to a browser session saved by the
      // Telegram Login Widget page (see LoginScreen), or send the user there.
      const stored = localStorage.getItem(BROWSER_SESSION_KEY)
      if (!stored) {
        window.location.hash = '#login'
        return
      }
      let session: AuthResponse
      try {
        session = JSON.parse(stored) as AuthResponse
      } catch {
        localStorage.removeItem(BROWSER_SESSION_KEY)
        window.location.hash = '#login'
        return
      }
      setBrowserSession(true)
      void applySession(session).catch((reason) => {
        showTelegramMessage(reason instanceof Error ? reason.message : t('error.authFailed'))
        localStorage.removeItem(BROWSER_SESSION_KEY)
        window.location.hash = '#login'
      })
      return
    }

    void fetch('/api/auth/telegram', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ initData: webApp.initData }),
    })
      .then(async (response) => {
        if (!response.ok) {
          const error = await response.json().catch(() => null) as { message?: string } | null
          throw new Error(error?.message ? `${error.message} (${response.status})` : `${t('error.authFailed')} (${response.status})`)
        }
        return response.json() as Promise<AuthResponse>
      })
      .then((session) => applySession(session))
      .catch((reason) => showTelegramMessage(reason instanceof Error ? reason.message : t('error.authFailed')))
  }, [])

  useEffect(() => {
    if (!auth) return
    const params = new URLSearchParams(window.location.search)
    const taskParam = params.get('task')
    if (!taskParam) return
    const taskId = Number(taskParam)
    params.delete('task')
    const nextSearch = params.toString()
    window.history.replaceState(null, '', window.location.pathname + (nextSearch ? `?${nextSearch}` : '') + window.location.hash)
    if (!Number.isFinite(taskId)) return
    setEditAttachments([])
    setEditDetailsLoaded(false)
    goTo('edit')
    void fetch(`/api/tasks/${taskId}`, {
      headers: { Authorization: `Bearer ${auth.accessToken}` },
    }).then(async (response) => {
      if (!response.ok) throw new Error(t('error.taskLoadFailed'))
      const detailed = normalizeTask(await response.json())
      setTasks((current) => current.some((item) => item.id === detailed.id)
        ? current.map((item) => item.id === detailed.id ? detailed : item)
        : [...current, detailed])
      fillEditForm(detailed)
      setEditDetailsLoaded(true)
    }).catch((reason) => showTelegramMessage(reason instanceof Error ? reason.message : t('error.taskLoadFailed')))
  }, [auth])

  useEffect(() => {
    const root = document.documentElement
    if (theme === 'system') delete root.dataset.theme
    else root.dataset.theme = theme
  }, [theme])

  function t(key: string, vars?: Record<string, string | number>) {
    return translate(lang, key, vars)
  }

  async function savePreferences(patch: { uiLanguage?: Lang; theme?: Theme; remindersEnabled?: boolean }) {
    if (patch.uiLanguage) setLang(patch.uiLanguage)
    if (patch.theme) setTheme(patch.theme)
    if (patch.remindersEnabled !== undefined) setRemindersEnabled(patch.remindersEnabled)
    if (!auth) return
    setPreferencesSaving(true)
    try {
      await fetch('/api/users/me/preferences', {
        method: 'PATCH',
        headers: {
          Authorization: `Bearer ${auth.accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(patch),
      })
    } catch {
      // Tarmoq xatosi bo'lsa ham UI o'zgargan holicha qoladi; keyingi kirishda serverdan qayta yuklanadi.
    } finally {
      setPreferencesSaving(false)
    }
  }

  useEffect(() => {
    if (!auth) return
    const refreshGroups = () => { void loadGroups(auth) }
    window.addEventListener('focus', refreshGroups)
    return () => window.removeEventListener('focus', refreshGroups)
  }, [auth])

  useEffect(() => {
    if (!auth || activeWorkspaceId === null) return
    const currentAuth = auth
    const currentWorkspaceId = activeWorkspaceId
    const accessToken = currentAuth.accessToken
    const currentUserId = currentAuth.user.id
    let socket: WebSocket | null = null
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null
    let stopped = false

    function connect() {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      const url = `${protocol}//${window.location.host}/ws/tasks`
        + `?token=${encodeURIComponent(accessToken)}&workspaceId=${currentWorkspaceId}`
      socket = new WebSocket(url)
      socket.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data) as { type?: string; actorUserId?: number }
          if (data.type === 'TASK_CHANGED' && data.actorUserId !== currentUserId) {
            void loadTasks(currentAuth, currentWorkspaceId)
          }
        } catch {
          // noto'g'ri formatdagi xabar e'tiborsiz qoldiriladi
        }
      }
      socket.onclose = () => {
        if (stopped) return
        reconnectTimer = setTimeout(connect, 4000)
      }
      socket.onerror = () => socket?.close()
    }

    connect()

    return () => {
      stopped = true
      if (reconnectTimer) clearTimeout(reconnectTimer)
      socket?.close()
    }
  }, [auth, activeWorkspaceId])

  useEffect(() => {
    if (screen !== 'edit') return
    const missing = selectedTaskId === null
      || (tasks.length > 0 && !tasks.some((task) => task.id === selectedTaskId))
    if (missing) goTo('tasks')
  }, [screen, selectedTaskId, tasks])

  function activeWorkspace(session: AuthResponse | null | undefined) {
    if (!session) return undefined
    return session.workspaces.find((item) => item.id === activeWorkspaceId) ?? session.workspaces[0]
  }

  async function switchWorkspace(workspaceId: number) {
    if (!auth || workspaceId === activeWorkspaceId) {
      setSheet(null)
      return
    }
    setWorkspaceSwitching(true)
    try {
      latestWorkspaceIdRef.current = workspaceId
      setActiveWorkspaceId(workspaceId)
      const workspace = auth.workspaces.find((item) => item.id === workspaceId)
      if (workspace) setWorkspaceName(workspace.name)
      const session = { ...auth }
      await Promise.all([loadTasks(session, workspaceId), loadGroups(session, workspaceId),
        loadWorkspaceMembers(session, workspaceId)])
      setSheet(null)
    } finally {
      setWorkspaceSwitching(false)
    }
  }

  async function createWorkspace() {
    if (!auth || !newWorkspaceName.trim()) return
    setCreatingWorkspace(true)
    try {
      const response = await fetch('/api/workspaces', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${auth.accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ name: newWorkspaceName.trim() }),
      })
      if (!response.ok) throw new Error(t('error.workspaceCreateFailed'))
      const created = await response.json() as { id: number; name: string; role: string }
      const updatedAuth: AuthResponse = { ...auth, workspaces: [...auth.workspaces, created] }
      setAuth(updatedAuth)
      setNewWorkspaceName('')
      await switchWorkspace(created.id)
    } catch (reason) {
      showTelegramMessage(reason instanceof Error ? reason.message : t('error.workspaceCreateFailed'))
    } finally {
      setCreatingWorkspace(false)
    }
  }

  async function loadTasks(session: AuthResponse, workspaceId?: number) {
    const workspace = workspaceId ? session.workspaces.find((item) => item.id === workspaceId) : activeWorkspace(session)
    if (!workspace) return
    const response = await fetch(`/api/tasks?workspaceId=${workspace.id}&scope=ALL`, {
      headers: { Authorization: `Bearer ${session.accessToken}` },
    })
    if (!response.ok) return
    const result = (await response.json()) as Array<Partial<Task> & { id: number; title: string }>
    if (latestWorkspaceIdRef.current !== null && workspace.id !== latestWorkspaceIdRef.current) return
    setTasks(result.map(normalizeTask))
  }

  async function loadGroups(session: AuthResponse, workspaceId?: number) {
    const workspace = workspaceId ? session.workspaces.find((item) => item.id === workspaceId) : activeWorkspace(session)
    if (!workspace) return []
    const response = await fetch(`/api/groups?workspaceId=${workspace.id}`, {
      headers: { Authorization: `Bearer ${session.accessToken}` },
    })
    if (!response.ok) return []
    const result = (await response.json() as LinkedGroup[])
      .map((group) => ({
        ...group,
        memberList: group.memberList ?? [],
        botConnected: group.botConnected ?? false,
      }))
    if (latestWorkspaceIdRef.current === null || workspace.id === latestWorkspaceIdRef.current) setGroups(result)
    return result
  }

  async function loadGroupTopics(groupId: number) {
    if (!auth) return []
    setTopicsLoading(true)
    try {
      const response = await fetch(`/api/groups/${groupId}/topics`, {
        headers: { Authorization: `Bearer ${auth.accessToken}` },
      })
      if (!response.ok) {
        setGroupTopics([])
        return []
      }
      const result = await response.json() as TelegramTopic[]
      setGroupTopics(result)
      return result
    } finally {
      setTopicsLoading(false)
    }
  }

  async function loadAvailableGroups() {
    const workspace = activeWorkspace(auth)
    if (!auth || !workspace) return []
    setAvailableGroupsLoading(true)
    try {
      const response = await fetch(`/api/groups/telegram/available?workspaceId=${workspace.id}`, {
        headers: { Authorization: `Bearer ${auth.accessToken}` },
      })
      if (!response.ok) return []
      const result = await response.json() as AvailableTelegramGroup[]
      setAvailableGroups(result)
      return result
    } finally {
      setAvailableGroupsLoading(false)
    }
  }

  function openGroupPicker() {
    setSheet('group-picker')
    void loadAvailableGroups()
  }

  async function linkTelegramGroup(chatId: number) {
    const workspace = activeWorkspace(auth)
    if (!auth || !workspace) return
    setLinkingChatId(chatId)
    try {
      const response = await fetch(`/api/groups/telegram/link?workspaceId=${workspace.id}&chatId=${chatId}`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${auth.accessToken}` },
      })
      if (!response.ok) {
        const error = await response.json().catch(() => null) as { message?: string } | null
        throw new Error(error?.message ?? t('error.groupAddFailed'))
      }
      await loadGroups(auth)
      setSheet(null)
      showTelegramMessage(t('success.groupAdded'))
    } catch (reason) {
      showTelegramMessage(reason instanceof Error ? reason.message : t('error.groupAddFailed'))
    } finally {
      setLinkingChatId(null)
    }
  }

  async function updateGroupTaskPolicy(groupId: number, policy: string) {
    if (!auth) return
    try {
      const response = await fetch(`/api/groups/${groupId}/rules`, {
        method: 'PATCH',
        headers: {
          Authorization: `Bearer ${auth.accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ taskCreationPolicy: policy }),
      })
      if (!response.ok) {
        const error = await response.json().catch(() => null) as { message?: string } | null
        throw new Error(error?.message ?? t('error.groupRuleSave'))
      }
      setGroups((current) => current.map((group) =>
        group.id === groupId ? { ...group, taskCreationPolicy: policy } : group))
    } catch (reason) {
      showTelegramMessage(reason instanceof Error ? reason.message : t('error.groupRuleSave'))
    }
  }

  async function loadWorkspaceMembers(session: AuthResponse, workspaceId?: number) {
    const workspace = workspaceId ? session.workspaces.find((item) => item.id === workspaceId) : activeWorkspace(session)
    if (!workspace) return []
    const response = await fetch(`/api/workspaces/${workspace.id}/members`, {
      headers: { Authorization: `Bearer ${session.accessToken}` },
    })
    if (!response.ok) return []
    const result = (await response.json()) as WorkspaceMember[]
    const available = result.filter((member) => member.active && !member.temporarilyBlocked)
    if (latestWorkspaceIdRef.current === null || workspace.id === latestWorkspaceIdRef.current) setWorkspaceMembers(available)
    return available
  }

  function addBotToGroup(group: LinkedGroup) {
    if (!group.botUsername) {
      showTelegramMessage(t('error.botUsernameMissing'))
      return
    }
    const url = `https://t.me/${group.botUsername.replace('@', '')}?startgroup=true`
    const webApp = window.Telegram?.WebApp
    if (webApp?.openTelegramLink) webApp.openTelegramLink(url)
    else window.open(url, '_blank', 'noopener,noreferrer')
  }

  async function inviteGroupMembers(group: LinkedGroup) {
    if (!auth || invitingMembers) return
    setInvitingMembers(true)
    try {
      const response = await fetch(`/api/groups/${group.id}/invite-members`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${auth.accessToken}` },
      })
      if (!response.ok) {
        const error = await response.json().catch(() => null) as { message?: string } | null
        throw new Error(error?.message ?? t('error.inviteFailed'))
      }
      showTelegramMessage(t('success.membershipInviteSent'))
      void (async () => {
        for (let attempt = 0; attempt < 15; attempt += 1) {
          await new Promise((resolve) => window.setTimeout(resolve, 4_000))
          await loadGroups(auth)
        }
      })()
    } catch (reason) {
      showTelegramMessage(reason instanceof Error ? reason.message : t('error.inviteFailed'))
    } finally {
      setInvitingMembers(false)
    }
  }

  async function openTelegramGroupPicker() {
    if (groupPickerBusy) return
    const workspace = activeWorkspace(auth)
    const webApp = window.Telegram?.WebApp
    if (!auth || !workspace || !webApp?.initData) {
      showTelegramMessage(t('error.telegramOpenApp'))
      return
    }
    if (!webApp.requestChat || (webApp.isVersionAtLeast && !webApp.isVersionAtLeast('9.6'))) {
      const version = webApp.version ? ` (WebApp ${webApp.version})` : ''
      showTelegramMessage(t('error.telegramVersionRequired', { version }))
      return
    }

    setGroupPickerBusy(true)
    const previousIds = new Set(groups.map((group) => group.id))
    try {
      const response = await fetch(`/api/groups/telegram/prepare?workspaceId=${workspace.id}`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${auth.accessToken}` },
      })
      if (!response.ok) {
        const error = await response.json().catch(() => null) as { message?: string } | null
        throw new Error(error?.message ?? t('error.telegramPickerOpenFailed'))
      }
      const prepared = await response.json() as { preparedButtonId: string; requestId: number }
      let pickerFinished = false
      const pickerTimeout = window.setTimeout(() => {
        if (pickerFinished) return
        pickerFinished = true
        void (async () => {
          try {
            const fallbackResponse = await fetch(
              `/api/groups/telegram/fallback?requestId=${prepared.requestId}`,
              { method: 'POST', headers: { Authorization: `Bearer ${auth.accessToken}` } },
            )
            if (!fallbackResponse.ok) throw new Error(t('error.botFallbackFailed'))
            setGroupPickerBusy(false)
            const message = t('info.groupPickerSentToBot')
            if (webApp.showAlert) webApp.showAlert(message, () => webApp.close?.())
            else {
              window.alert(message)
              webApp.close?.()
            }
          } catch (reason) {
            setGroupPickerBusy(false)
            showTelegramMessage(reason instanceof Error ? reason.message : t('error.groupAddFailed'))
          }
        })()
      }, 8_000)
      webApp.requestChat(prepared.preparedButtonId, (selected) => {
        if (pickerFinished) return
        pickerFinished = true
        window.clearTimeout(pickerTimeout)
        if (!selected) {
          setGroupPickerBusy(false)
          return
        }
        void (async () => {
          for (let attempt = 0; attempt < 10; attempt += 1) {
            await new Promise((resolve) => window.setTimeout(resolve, 900))
            const refreshed = await loadGroups(auth)
            const added = refreshed.find((group) => !previousIds.has(group.id))
            if (added) {
              setSelectedGroupId(added.id)
              setGroupPickerBusy(false)
              return
            }
          }
          setGroupPickerBusy(false)
          showTelegramMessage(t('info.groupSelectedRefresh'))
        })()
      })
    } catch (reason) {
      setGroupPickerBusy(false)
      showTelegramMessage(reason instanceof Error ? reason.message : t('error.groupAddFailed'))
    }
  }

  function goTo(next: Screen) {
    setSheet(null)
    if (screen === next) return
    window.location.hash = next
  }

  function addChecklist() {
    const text = checklistDraft.trim()
    if (!text) return
    setChecklist((items) => [...items, { id: Date.now(), text, done: false }])
    setChecklistDraft('')
  }

  function addAttachments(files: FileList | null) {
    if (!files?.length) return
    const { accepted, rejected } = attachmentItems(files)
    setAttachments((current) => [...current, ...accepted])
    if (rejected.length) showTelegramMessage(t('error.attachmentTooLarge', { names: rejected.join(', ') }))
    setMoreOpen(true)
  }

  function addEditAttachments(files: FileList | null) {
    if (!files?.length) return
    const { accepted, rejected } = attachmentItems(files)
    setEditAttachments((current) => [...current, ...accepted])
    if (rejected.length) showTelegramMessage(t('error.attachmentTooLarge', { names: rejected.join(', ') }))
  }

  async function removeTaskAttachment(taskId: number, fileId: number) {
    if (!auth) throw new Error(t('error.sessionNotFound'))
    setEditError('')
    const response = await fetch(`/api/tasks/${taskId}/files/${fileId}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${auth.accessToken}` },
    })
    if (!response.ok) {
      const error = await response.json().catch(() => null) as { message?: string } | null
      const message = error?.message ?? t('error.fileDeleteFailed')
      setEditError(message)
      throw new Error(message)
    }
    const updated = normalizeTask(await response.json())
    setTasks((current) => current.map((task) => task.id === updated.id ? updated : task))
  }

  async function loadArchivedTasks() {
    const workspace = activeWorkspace(auth)
    if (!auth || !workspace) return
    setArchivedLoading(true)
    try {
      const response = await fetch(`/api/tasks/archived?workspaceId=${workspace.id}`, {
        headers: { Authorization: `Bearer ${auth.accessToken}` },
      })
      if (!response.ok) return
      const result = (await response.json()) as Array<Partial<Task> & { id: number; title: string }>
      setArchivedTasks(result.map(normalizeTask))
    } finally {
      setArchivedLoading(false)
    }
  }

  async function archiveTask(taskId: number) {
    if (!auth) return
    const response = await fetch(`/api/tasks/${taskId}/archive`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${auth.accessToken}` },
    })
    if (!response.ok) {
      const error = await response.json().catch(() => null) as { message?: string } | null
      showTelegramMessage(error?.message ?? t('error.archiveFailed'))
      return
    }
    setTasks((current) => current.filter((task) => task.id !== taskId))
  }

  async function restoreTask(taskId: number) {
    if (!auth) return
    const response = await fetch(`/api/tasks/${taskId}/restore`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${auth.accessToken}` },
    })
    if (!response.ok) {
      const error = await response.json().catch(() => null) as { message?: string } | null
      showTelegramMessage(error?.message ?? t('error.restoreFailed'))
      return
    }
    const restored = normalizeTask(await response.json())
    setArchivedTasks((current) => current.filter((task) => task.id !== taskId))
    setTasks((current) => [restored, ...current])
  }

  async function hardDeleteTask(taskId: number) {
    if (!auth) return
    const response = await fetch(`/api/tasks/${taskId}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${auth.accessToken}` },
    })
    if (!response.ok) {
      const error = await response.json().catch(() => null) as { message?: string } | null
      showTelegramMessage(error?.message ?? t('error.deleteFailed'))
      return
    }
    setArchivedTasks((current) => current.filter((task) => task.id !== taskId))
  }

  function taskShareText(task: Task) {
    const lines = [`📋 ${task.title}`]
    if (task.description) lines.push(task.description)
    lines.push(window.location.origin)
    return lines.join('\n\n')
  }

  function shareTaskViaTelegram(task: Task) {
    const webApp = window.Telegram?.WebApp
    const shareUrl = `https://t.me/share/url?url=${encodeURIComponent(window.location.origin)}&text=${encodeURIComponent(taskShareText(task))}`
    if (webApp?.openTelegramLink) webApp.openTelegramLink(shareUrl)
    else window.open(shareUrl, '_blank', 'noopener,noreferrer')
    setSheet(null)
  }

  async function copyTaskMessage(task: Task) {
    try {
      await navigator.clipboard.writeText(taskShareText(task))
      showTelegramMessage(t('edit.copy.success'))
    } catch {
      showTelegramMessage(t('edit.copy.failed'))
    }
    setSheet(null)
  }

  async function duplicateTask(task: Task) {
    const workspace = activeWorkspace(auth)
    if (!task || !auth || !workspace) return
    try {
      const futureDueAt = task.dueAt && new Date(task.dueAt) > new Date() ? task.dueAt : undefined
      const payload = {
        workspaceId: workspace.id,
        groupId: task.groupId,
        topicId: task.topicId,
        title: `${task.title}${t('edit.duplicate.suffix')}`,
        description: task.description,
        status: 'NEW',
        priority: task.priority,
        visibility: task.visibility,
        dueAt: futureDueAt,
        assigneeIds: task.assigneeIds ?? [],
        checklist: (task.checklistItems ?? []).map((item) => ({ text: item.text, done: false })),
      }
      const response = await fetch('/api/tasks', {
        method: 'POST',
        headers: { Authorization: `Bearer ${auth.accessToken}`, 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
      if (!response.ok) throw new Error(t('edit.duplicate.failed'))
      const created = normalizeTask(await response.json())
      setTasks((current) => [created, ...current])
      showTelegramMessage(t('edit.duplicate.success'))
    } catch (reason) {
      showTelegramMessage(reason instanceof Error ? reason.message : t('edit.duplicate.failed'))
    } finally {
      setSheet(null)
    }
  }

  function openTaskDiscussion(task: Task) {
    setSheet(null)
    showTelegramMessage(task.visibility === 'GROUP' ? t('edit.discussion.info') : t('edit.discussion.groupOnly'))
  }

  async function changeTaskStatus(taskId: number, status: TaskStatus) {
    if (!auth) return
    const previous = tasks
    setTasks((current) => current.map((task) => task.id === taskId ? { ...task, status } : task))
    try {
      const response = await fetch(`/api/tasks/${taskId}/status`, {
        method: 'PATCH',
        headers: { Authorization: `Bearer ${auth.accessToken}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ status }),
      })
      if (!response.ok) {
        const error = await response.json().catch(() => null) as { message?: string } | null
        throw new Error(error?.message ?? t('error.taskSaveFailed'))
      }
      const updated = normalizeTask(await response.json())
      setTasks((current) => current.map((task) => task.id === updated.id ? updated : task))
    } catch (reason) {
      setTasks(previous)
      showTelegramMessage(reason instanceof Error ? reason.message : t('error.taskSaveFailed'))
    }
  }

  async function saveDesktopTaskEdit(
    task: Task,
    changes: Partial<Task> & { authorId?: number },
    newAttachments?: File[],
  ): Promise<boolean> {
    if (!auth) return false
    const merged = { ...task, ...changes }
    const payload = {
      title: merged.title,
      description: merged.description,
      status: merged.status,
      priority: merged.priority,
      visibility: merged.visibility,
      groupId: merged.visibility === 'GROUP' ? merged.groupId : undefined,
      topicId: merged.visibility === 'GROUP' ? merged.topicId : undefined,
      assigneeIds: merged.visibility === 'PERSONAL' ? undefined : (changes.assigneeIds ?? taskAssigneeIds(task)),
      authorId: changes.authorId,
      dueAt: merged.dueAt,
      dueAtProvided: true,
      reminderMinutes: merged.reminderMinutes,
      reminderProvided: true,
      checklist: (merged.checklistItems ?? []).map((item) => ({ text: item.text, done: item.done })),
    }
    try {
      let response: Response
      if (newAttachments && newAttachments.length) {
        const formData = new FormData()
        formData.append('task', new Blob([JSON.stringify(payload)], { type: 'application/json' }), 'task.json')
        newAttachments.forEach((file) => formData.append('files', file, file.name))
        response = await fetch(`/api/tasks/${task.id}`, {
          method: 'PUT',
          headers: { Authorization: `Bearer ${auth.accessToken}` },
          body: formData,
        })
      } else {
        response = await fetch(`/api/tasks/${task.id}`, {
          method: 'PUT',
          headers: { Authorization: `Bearer ${auth.accessToken}`, 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        })
      }
      if (!response.ok) {
        const error = await response.json().catch(() => null) as { message?: string } | null
        throw new Error(error?.message ?? t('error.taskSaveFailed'))
      }
      const updated = normalizeTask(await response.json())
      setTasks((current) => current.map((item) => item.id === updated.id ? updated : item))
      return true
    } catch (reason) {
      showTelegramMessage(reason instanceof Error ? reason.message : t('error.taskSaveFailed'))
      return false
    }
  }

  async function createDesktopTask(input: {
    title: string
    description?: string
    visibility: TaskVisibility
    groupId?: number
    topicId?: number
    assigneeIds: number[]
    priority: TaskPriority
    status?: TaskStatus
    dueAt?: string
    reminderMinutes?: number
    checklist?: Array<{ text: string; done: boolean }>
    attachments?: File[]
  }) {
    const workspace = activeWorkspace(auth)
    if (!auth || !workspace) return
    const payload = {
      workspaceId: workspace.id,
      groupId: input.visibility === 'GROUP' ? input.groupId : undefined,
      topicId: input.visibility === 'GROUP' ? input.topicId : undefined,
      title: input.title,
      description: input.description,
      status: input.status ?? 'NEW',
      priority: input.priority,
      visibility: input.visibility,
      dueAt: input.dueAt,
      reminderMinutes: input.reminderMinutes,
      assigneeIds: input.visibility === 'GROUP' ? input.assigneeIds : [auth.user.id],
      checklist: input.checklist ?? [],
    }
    try {
      let response: Response
      if (input.attachments && input.attachments.length) {
        const formData = new FormData()
        formData.append('task', new Blob([JSON.stringify(payload)], { type: 'application/json' }), 'task.json')
        input.attachments.forEach((file) => formData.append('files', file, file.name))
        response = await fetch('/api/tasks', {
          method: 'POST',
          headers: { Authorization: `Bearer ${auth.accessToken}` },
          body: formData,
        })
      } else {
        response = await fetch('/api/tasks', {
          method: 'POST',
          headers: { Authorization: `Bearer ${auth.accessToken}`, 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        })
      }
      if (!response.ok) {
        const error = await response.json().catch(() => null) as { message?: string } | null
        throw new Error(error?.message ?? t('error.taskSaveFailed'))
      }
      const created = normalizeTask(await response.json())
      setTasks((current) => [created, ...current])
      return created
    } catch (reason) {
      showTelegramMessage(reason instanceof Error ? reason.message : t('error.taskSaveFailed'))
      return undefined
    }
  }

  function resetForm() {
    setTitle('')
    setDescription('')
    setStatus('NEW')
    setPriority('NORMAL')
    setVisibility('PERSONAL')
    setSelectedGroupId(null)
    setAssigneeIds([])
    setDeadline(undefined)
    setReminderMinutes(undefined)
    setChecklist([])
    setAttachments([])
    setMoreOpen(false)
    setFormError('')
  }

  async function createTask(event: FormEvent) {
    event.preventDefault()
    const taskTitle = title.trim()
    if (!taskTitle) {
      setFormError(t('error.nameRequired'))
      return
    }
    if (visibility === 'GROUP' && (!selectedGroupId || assigneeIds.length === 0)) {
      setFormError(!selectedGroupId ? t('error.groupRequired') : t('error.assigneeRequired'))
      return
    }

    setSaving(true)
    setFormError('')
    try {
      let created: Task
      const currentWorkspace = activeWorkspace(auth)
      if (auth && currentWorkspace) {
        const payload = {
          workspaceId: currentWorkspace.id,
          title: taskTitle,
          description: description.trim() || undefined,
          status,
          priority,
          visibility,
          groupId: visibility === 'GROUP' ? selectedGroupId : undefined,
          topicId: visibility === 'GROUP' ? selectedTopicId ?? undefined : undefined,
          dueAt: deadline,
          reminderMinutes,
          assigneeIds: visibility === 'GROUP' ? assigneeIds : [auth.user.id],
          checklist: checklist.map((item) => ({ text: item.text, done: item.done })),
        }
        let response: Response
        if (attachments.length === 0) {
          response = await fetch('/api/tasks', {
            method: 'POST',
            headers: {
              Authorization: `Bearer ${auth.accessToken}`,
              'Content-Type': 'application/json',
            },
            body: JSON.stringify(payload),
          })
        } else {
          const formData = new FormData()
          formData.append('task', new Blob([JSON.stringify(payload)], { type: 'application/json' }), 'task.json')
          attachments.forEach((item) => formData.append('files', item.file, item.name))
          response = await fetch('/api/tasks', {
            method: 'POST',
            headers: { Authorization: `Bearer ${auth.accessToken}` },
            body: formData,
          })
        }
        if (!response.ok) {
          const error = await response.json().catch(() => null) as { message?: string } | null
          throw new Error(error?.message ?? t('error.taskSaveFailed'))
        }
        created = normalizeTask(await response.json())
        if (visibility === 'GROUP') created.groupName = selectedGroup?.title
      } else {
        created = {
          id: Date.now(),
          title: taskTitle,
          description: description.trim() || undefined,
          status,
          priority,
          visibility,
          groupName: visibility === 'GROUP' ? selectedGroup?.title : undefined,
          assigneeIds: visibility === 'GROUP' ? assigneeIds : auth ? [auth.user.id] : [],
          dueAt: deadline,
          reminderMinutes,
          checklist: checklist.length ? `0/${checklist.length}` : undefined,
          files: attachments.length,
        }
      }
      setTasks((current) => [created, ...current])
      resetForm()
      goTo('tasks')
    } catch (reason) {
      const message = reason instanceof Error ? reason.message : t('error.taskSaveFailed')
      setFormError(message === 'Load failed' || message === 'Failed to fetch'
        ? t('common.error.network')
        : message)
    } finally {
      setSaving(false)
    }
  }

  function fillEditForm(task: Task) {
    setSelectedTaskId(task.id)
    setEditTitle(task.title)
    setEditDescription(task.description ?? '')
    setEditStatus(task.status)
    setEditPriority(task.priority)
    setEditDeadline(task.dueAt)
    setEditReminderMinutes(task.reminderMinutes)
    setEditChecklist(task.checklistItems?.map((item) => ({ ...item })) ?? [])
    setEditChecklistDraft('')
    setEditVisibility(task.visibility)
    setEditGroupId(task.groupId ?? null)
    setEditTopicId(task.topicId ?? null)
    setEditAssigneeIds(taskAssigneeIds(task))
    setEditAuthorId(task.authorId ?? null)
    setEditError('')
  }

  function addEditChecklist() {
    const text = editChecklistDraft.trim()
    if (!text) return
    setEditChecklist((items) => [...items, { id: Date.now(), text, done: false }])
    setEditChecklistDraft('')
  }

  function openTask(task: Task) {
    setEditAttachments([])
    setEditDetailsLoaded(false)
    fillEditForm(task)
    goTo('edit')
    if (!auth) {
      setEditDetailsLoaded(true)
      return
    }
    void fetch(`/api/tasks/${task.id}`, {
      headers: { Authorization: `Bearer ${auth.accessToken}` },
    }).then(async (response) => {
      if (!response.ok) throw new Error(t('error.taskLoadFailed'))
      const detailed = normalizeTask(await response.json())
      setTasks((current) => current.map((item) => item.id === detailed.id ? detailed : item))
      fillEditForm(detailed)
      setEditDetailsLoaded(true)
    }).catch((reason) => setEditError(reason instanceof Error ? reason.message : t('error.taskLoadFailed')))
  }

  async function persistTaskEdit(nextStatus: TaskStatus, closeAfterSave: boolean) {
    if (!selectedTaskId || !auth || !editDetailsLoaded) return
    const nextTitle = editTitle.trim()
    if (!nextTitle) {
      setEditError(t('error.nameRequired'))
      return
    }
    setEditSaving(true)
    setEditError('')
    try {
      const payload = {
        title: nextTitle,
        description: editDescription.trim() || undefined,
        status: nextStatus,
        priority: editPriority,
        visibility: editVisibility,
        groupId: editVisibility === 'GROUP' ? editGroupId ?? undefined : undefined,
        topicId: editVisibility === 'GROUP' ? editTopicId ?? undefined : undefined,
        assigneeIds: editVisibility === 'PERSONAL' ? undefined : editAssigneeIds,
        authorId: editAuthorId ?? undefined,
        dueAt: editDeadline,
        dueAtProvided: true,
        reminderMinutes: editReminderMinutes,
        reminderProvided: true,
        checklist: editChecklist.map((item) => ({ text: item.text.trim(), done: item.done }))
          .filter((item) => item.text),
      }
      let response: Response
      if (editAttachments.length) {
        const formData = new FormData()
        formData.append('task', new Blob([JSON.stringify(payload)], { type: 'application/json' }), 'task.json')
        editAttachments.forEach((item) => formData.append('files', item.file, item.name))
        response = await fetch(`/api/tasks/${selectedTaskId}`, {
          method: 'PUT',
          headers: { Authorization: `Bearer ${auth.accessToken}` },
          body: formData,
        })
      } else {
        response = await fetch(`/api/tasks/${selectedTaskId}`, {
          method: 'PUT',
          headers: {
            Authorization: `Bearer ${auth.accessToken}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(payload),
        })
      }
      if (!response.ok) {
        const error = await response.json().catch(() => null) as { message?: string } | null
        throw new Error(error?.message ?? t('error.taskSaveFailed'))
      }
      const updated = normalizeTask(await response.json())
      setTasks((current) => current.map((task) => task.id === updated.id ? updated : task))
      setEditAttachments([])
      fillEditForm(updated)
      if (closeAfterSave) goTo('tasks')
    } catch (reason) {
      const message = reason instanceof Error ? reason.message : t('error.taskSaveFailed')
      setEditError(message === 'Load failed' || message === 'Failed to fetch'
        ? t('common.error.network')
        : message)
    } finally {
      setEditSaving(false)
    }
  }

  async function saveTaskEdit(event: FormEvent) {
    event.preventDefault()
    await persistTaskEdit(editStatus, true)
  }

  function quickEditStatus(nextStatus: TaskStatus) {
    setEditStatus(nextStatus)
    void persistTaskEdit(nextStatus, false)
  }

  const visibleTasks = useMemo(
    () => tasks.filter((task) => {
      if (scope === 'WORKSPACE') return true
      if (scope === 'GROUP') return task.visibility === 'GROUP' && task.groupId === scopeGroupId
      return task.visibility === scope
    }),
    [tasks, scope, scopeGroupId],
  )

  const dashboardTasks = useMemo(
    () => visibleTasks.filter((task) => matchesDashboardFilter(task, taskFilter, auth?.user.id, taskSearch)),
    [visibleTasks, taskFilter, auth?.user.id, taskSearch],
  )

  const selectedGroup = groups.find((group) => group.id === selectedGroupId)
  const selectedTask = tasks.find((task) => task.id === selectedTaskId)
  const createPlaceLabel = visibility === 'GROUP' ? selectedGroup?.title ?? t('place.GROUP') : placeLabel(t, visibility)
  const userName = profileName
  const userInitials = initials(profileName)

  if (isDesktop && auth) {
    return (
      <I18nContext.Provider value={{ lang, t }}>
        <DesktopShell
          auth={auth}
          profileName={profileName}
          userInitials={userInitials}
          workspaceName={workspaceName}
          tasks={tasks}
          groups={groups}
          workspaceMembers={workspaceMembers}
          activeWorkspaceId={activeWorkspaceId}
          workspaceSwitching={workspaceSwitching}
          newWorkspaceName={newWorkspaceName}
          creatingWorkspace={creatingWorkspace}
          onNewWorkspaceNameChange={setNewWorkspaceName}
          onSwitchWorkspace={(workspaceId) => void switchWorkspace(workspaceId)}
          onCreateWorkspace={() => void createWorkspace()}
          theme={theme}
          remindersEnabled={remindersEnabled}
          onSavePreferences={(patch) => void savePreferences(patch)}
          onChangeStatus={(taskId, status) => void changeTaskStatus(taskId, status)}
          onSaveTask={(task, changes, files) => saveDesktopTaskEdit(task, changes, files)}
          onCreateTask={createDesktopTask}
          onRemoveAttachment={(taskId, fileId) => void removeTaskAttachment(taskId, fileId)}
          onDuplicateTask={(task) => void duplicateTask(task)}
          onShareTelegram={shareTaskViaTelegram}
          onCopyMessage={(task) => void copyTaskMessage(task)}
          onOpenDiscussion={openTaskDiscussion}
          availableGroups={availableGroups}
          availableGroupsLoading={availableGroupsLoading}
          linkingChatId={linkingChatId}
          onLoadAvailableGroups={() => void loadAvailableGroups()}
          groupTopics={groupTopics}
          topicsLoading={topicsLoading}
          onLoadGroupTopics={(groupId) => void loadGroupTopics(groupId)}
          onLinkGroup={(chatId) => void linkTelegramGroup(chatId)}
          groupPickerBusy={groupPickerBusy}
          onOpenNativeGroupPicker={() => void openTelegramGroupPicker()}
          onInviteMembers={(group) => void inviteGroupMembers(group)}
          invitingMembers={invitingMembers}
          onAddBotToGroup={addBotToGroup}
          onChangeGroupTaskPolicy={(groupId, policy) => void updateGroupTaskPolicy(groupId, policy)}
          archivedTasks={archivedTasks}
          archivedLoading={archivedLoading}
          onLoadArchived={() => void loadArchivedTasks()}
          onArchiveTask={(taskId) => void archiveTask(taskId)}
          onRestoreTask={(taskId) => void restoreTask(taskId)}
          onDeleteTaskForever={(taskId) => void hardDeleteTask(taskId)}
        />
      </I18nContext.Provider>
    )
  }

  return (
    <I18nContext.Provider value={{ lang, t }}>
    <div className="viewport-shell">
      <div className={`mobile-app screen-${screen}`}>
        {screen === 'tasks' && (
          <TasksScreen
            tasks={dashboardTasks}
            allTasks={visibleTasks}
            members={workspaceMembers}
            currentUserId={auth?.user.id}
            filter={taskFilter}
            grouping={taskGrouping}
            search={taskSearch}
            profileName={profileName}
            profileInitials={userInitials}
            onWorkspace={() => setSheet('workspace')}
            onScope={() => setSheet('scope')}
            onSettings={() => goTo('settings')}
            onCreate={() => goTo('create')}
            onTask={openTask}
            onSearch={setTaskSearch}
            onQuickView={(view) => setTaskFilter((current) => ({ ...current, view }))}
            onFilter={() => setSheet('task-filter')}
            onGrouping={() => setSheet('task-group')}
          />
        )}

        {screen === 'create' && (
          <CreateScreen
            title={title}
            description={description}
            status={status}
            priority={priority}
            visibility={visibility}
            placeLabel={createPlaceLabel}
            deadline={deadline}
            moreOpen={moreOpen}
            checklist={checklist}
            checklistDraft={checklistDraft}
            attachments={attachments}
            selectedGroup={selectedGroup}
            assigneeIds={assigneeIds}
            currentUserId={auth?.user.id}
            saving={saving}
            error={formError}
            galleryRef={galleryRef}
            cameraRef={cameraRef}
            fileRef={fileRef}
            onTitle={setTitle}
            onDescription={setDescription}
            onStatus={() => setSheet('create-status')}
            onPriority={() => setSheet('create-priority')}
            onDeadline={() => goTo('deadline')}
            onPlace={() => setSheet('task-place')}
            onAssigneeToggle={(memberId) => setAssigneeIds((current) =>
              current.includes(memberId)
                ? current.filter((id) => id !== memberId)
                : [...current, memberId])}
            onAddBot={() => selectedGroup && addBotToGroup(selectedGroup)}
            onMore={() => setMoreOpen((value) => !value)}
            onChecklistDraft={setChecklistDraft}
            onChecklistAdd={addChecklist}
            onChecklistToggle={(id) =>
              setChecklist((items) => items.map((item) => (item.id === id ? { ...item, done: !item.done } : item)))
            }
            onAttachments={addAttachments}
            onRemoveAttachment={(id) => setAttachments((items) => items.filter((item) => item.id !== id))}
            onSubmit={createTask}
            onClose={() => {
              resetForm()
              goTo('tasks')
            }}
          />
        )}

        {screen === 'edit' && selectedTask && (
          <EditTaskScreen
            task={selectedTask}
            title={editTitle}
            description={editDescription}
            status={editStatus}
            priority={editPriority}
            deadline={editDeadline}
            checklist={editChecklist}
            checklistDraft={editChecklistDraft}
            saving={editSaving}
            detailsLoaded={editDetailsLoaded}
            error={editError}
            accessToken={auth?.accessToken}
            newAttachments={editAttachments}
            galleryRef={editGalleryRef}
            cameraRef={editCameraRef}
            fileRef={editFileRef}
            visibility={editVisibility}
            groupId={editGroupId}
            groups={groups}
            assigneeIds={editAssigneeIds}
            workspaceMembers={workspaceMembers}
            authorId={editAuthorId}
            currentUserId={auth?.user.id}
            onTitle={setEditTitle}
            onDescription={setEditDescription}
            onQuickStatus={quickEditStatus}
            onPriority={() => setSheet('edit-priority')}
            onDeadline={() => goTo('edit-deadline')}
            onPlace={() => setSheet('edit-place')}
            onAuthor={() => setSheet('edit-author')}
            onAssigneeToggle={(memberId) => setEditAssigneeIds((current) =>
              current.includes(memberId)
                ? current.filter((id) => id !== memberId)
                : [...current, memberId])}
            onChecklistDraft={setEditChecklistDraft}
            onChecklistAdd={addEditChecklist}
            onChecklistToggle={(id) => setEditChecklist((items) => items.map((item) =>
              item.id === id ? { ...item, done: !item.done } : item))}
            onChecklistText={(id, value) => setEditChecklist((items) => items.map((item) =>
              item.id === id ? { ...item, text: value } : item))}
            onChecklistRemove={(id) => setEditChecklist((items) => items.filter((item) => item.id !== id))}
            onAttachments={addEditAttachments}
            onRemoveAttachment={(id) => setEditAttachments((items) => items.filter((item) => item.id !== id))}
            onRemoveExistingAttachment={(fileId) => removeTaskAttachment(selectedTask.id, fileId)}
            onOtherStatus={() => setSheet('edit-status')}
            onShare={() => setSheet('edit-share')}
            onActions={() => setSheet('edit-actions')}
            onSubmit={saveTaskEdit}
            onBack={() => goTo('tasks')}
          />
        )}

        {screen === 'deadline' && (
          <DeadlineScreen
            value={deadline}
            reminderMinutes={reminderMinutes}
            subtitle={createPlaceLabel}
            onChange={setDeadline}
            onReminderChange={setReminderMinutes}
            onDone={() => goTo('create')}
            onBack={() => goTo('create')}
          />
        )}

        {screen === 'edit-deadline' && selectedTask && (
          <DeadlineScreen
            value={editDeadline}
            reminderMinutes={editReminderMinutes}
            subtitle={selectedTask.groupName ?? placeLabel(t, selectedTask.visibility)}
            onChange={setEditDeadline}
            onReminderChange={setEditReminderMinutes}
            onDone={() => goTo('edit')}
            onBack={() => goTo('edit')}
          />
        )}

        {screen === 'settings' && (
          <SettingsScreen
            userName={userName}
            workspaceName={workspaceName}
            theme={theme}
            remindersEnabled={remindersEnabled}
            groupsCount={groups.length}
            membersCount={workspaceMembers.length}
            onWorkspace={() => goTo('workspace')}
            onSwitchWorkspace={() => setSheet('workspace-switch')}
            onAppearance={() => setSheet('appearance')}
            onLanguage={() => setSheet('language')}
            onNotificationsToggle={() => void savePreferences({ remindersEnabled: !remindersEnabled })}
            onHelp={() => goTo('help')}
            onGroups={() => goTo('groups')}
            onMembers={() => goTo('members')}
            onTaskRules={() => goTo('task-rules')}
            onArchive={() => { goTo('archive'); void loadArchivedTasks() }}
            onClose={() => goTo('tasks')}
            onLogout={browserSession ? () => {
              localStorage.removeItem(BROWSER_SESSION_KEY)
              window.location.hash = '#login'
              window.location.reload()
            } : undefined}
          />
        )}

        {screen === 'workspace' && (
          <WorkspaceScreen
            workspaceName={workspaceName}
            onWorkspaceName={setWorkspaceName}
            onBack={() => goTo('settings')}
          />
        )}

        {screen === 'help' && <HelpScreen onBack={() => goTo('settings')} />}

        {screen === 'groups' && (
          <GroupsScreen groups={groups} onBack={() => goTo('settings')} onAddGroup={openGroupPicker} />
        )}

        {screen === 'members' && (
          <MembersScreen members={workspaceMembers} currentUserId={auth?.user.id} onBack={() => goTo('settings')} />
        )}

        {screen === 'task-rules' && (
          <TaskRulesScreen
            groups={groups}
            onBack={() => goTo('settings')}
            onChangePolicy={(groupId, policy) => void updateGroupTaskPolicy(groupId, policy)}
          />
        )}

        {screen === 'archive' && (
          <ArchiveScreen
            tasks={archivedTasks}
            loading={archivedLoading}
            onBack={() => goTo('settings')}
            onRestore={(taskId) => void restoreTask(taskId)}
            onDeleteForever={(taskId) => void hardDeleteTask(taskId)}
            currentUserId={auth?.user.id}
            isOwner={activeWorkspace(auth)?.role === 'OWNER'}
          />
        )}

        {sheet && (
          <BottomSheet onClose={() => setSheet(null)}>
            {sheet === 'workspace' && (
              <WorkspaceSheet
                profileName={profileName}
                profileInitials={userInitials}
                groups={groups}
                selected={scope}
                selectedGroupId={scopeGroupId}
                onSwitch={() => setSheet('workspace-switch')}
                onAddGroup={openGroupPicker}
                groupPickerBusy={groupPickerBusy}
                onSelect={(value, groupId) => {
                  setScope(value)
                  setScopeGroupId(value === 'GROUP' ? groupId ?? null : null)
                  setSheet(null)
                }}
              />
            )}
            {sheet === 'scope' && (
              <ScopeSheet
                profileName={profileName}
                profileInitials={userInitials}
                groups={groups}
                selected={scope}
                selectedGroupId={scopeGroupId}
                onSwitch={() => setSheet('workspace-switch')}
                onAddGroup={openGroupPicker}
                groupPickerBusy={groupPickerBusy}
                onSelect={(value, groupId) => {
                  setScope(value)
                  setScopeGroupId(value === 'GROUP' ? groupId ?? null : null)
                  setSheet(null)
                }}
              />
            )}
            {sheet === 'task-place' && (
              <TaskPlaceSheet
                groups={groups}
                selected={visibility}
                selectedGroupId={selectedGroupId}
                onAddGroup={openGroupPicker}
                groupPickerBusy={groupPickerBusy}
                onSelect={(value, groupId) => {
                  const groupChanged = (groupId ?? null) !== selectedGroupId
                  setVisibility(value)
                  setSelectedGroupId(groupId ?? null)
                  if (groupChanged) {
                    setSelectedTopicId(null)
                    if (value === 'GROUP' && groupId) {
                      const group = groups.find((item) => item.id === groupId)
                      const currentUserId = auth?.user.id
                      setAssigneeIds(currentUserId && group?.memberList.some((member) => member.id === currentUserId)
                        ? [currentUserId]
                        : [])
                    } else {
                      setAssigneeIds(auth?.user.id ? [auth.user.id] : [])
                    }
                  }
                  if (value === 'GROUP' && groupId) {
                    setSheet('task-topic')
                    void loadGroupTopics(groupId)
                  } else {
                    setSheet(null)
                  }
                }}
              />
            )}
            {sheet === 'task-topic' && (
              <TopicPickerSheet
                topics={groupTopics}
                loading={topicsLoading}
                selectedTopicId={selectedTopicId}
                onSelect={(topicId) => { setSelectedTopicId(topicId ?? null); setSheet(null) }}
              />
            )}
            {sheet === 'group-picker' && (
              <GroupPickerSheet
                groups={availableGroups}
                loading={availableGroupsLoading}
                linkingChatId={linkingChatId}
                onLink={linkTelegramGroup}
                onNativePicker={openTelegramGroupPicker}
                nativeBusy={groupPickerBusy}
              />
            )}
            {sheet === 'edit-status' && selectedTask && (
              <StatusSheet
                taskTitle={editTitle || selectedTask.title}
                selected={editStatus}
                onSelect={(value) => {
                  setEditStatus(value)
                  setSheet(null)
                }}
              />
            )}
            {sheet === 'create-status' && (
              <StatusSheet
                taskTitle={title.trim() || t('create.title')}
                selected={status}
                onSelect={(value) => {
                  setStatus(value)
                  setSheet(null)
                }}
              />
            )}
            {sheet === 'create-priority' && (
              <PrioritySheet
                selected={priority}
                onSelect={(value) => {
                  setPriority(value)
                  setSheet(null)
                }}
              />
            )}
            {sheet === 'edit-priority' && selectedTask && (
              <PrioritySheet
                selected={editPriority}
                onSelect={(value) => {
                  setEditPriority(value)
                  setSheet(null)
                }}
              />
            )}
            {sheet === 'edit-place' && selectedTask && (
              <TaskPlaceSheet
                groups={groups}
                selected={editVisibility}
                selectedGroupId={editGroupId}
                onAddGroup={openGroupPicker}
                groupPickerBusy={groupPickerBusy}
                onSelect={(value, groupId) => {
                  const groupChanged = (value === 'GROUP' ? groupId ?? null : null) !== editGroupId
                  setEditVisibility(value)
                  setEditGroupId(value === 'GROUP' ? groupId ?? null : null)
                  if (groupChanged) {
                    setEditTopicId(null)
                    if (value === 'GROUP' && groupId) {
                      const group = groups.find((item) => item.id === groupId)
                      setEditAssigneeIds((current) =>
                        current.filter((id) => group?.memberList.some((member) => member.id === id)))
                    }
                  }
                  if (value === 'GROUP' && groupId) {
                    setSheet('edit-topic')
                    void loadGroupTopics(groupId)
                  } else {
                    setSheet(null)
                  }
                }}
              />
            )}
            {sheet === 'edit-topic' && selectedTask && (
              <TopicPickerSheet
                topics={groupTopics}
                loading={topicsLoading}
                selectedTopicId={editTopicId}
                onSelect={(topicId) => { setEditTopicId(topicId ?? null); setSheet(null) }}
              />
            )}
            {sheet === 'edit-author' && selectedTask && (
              <AuthorSheet
                members={workspaceMembers}
                selected={editAuthorId}
                currentUserId={auth?.user.id}
                onSelect={(userId) => {
                  setEditAuthorId(userId)
                  setSheet(null)
                }}
              />
            )}
            {sheet === 'edit-actions' && selectedTask && (
              <ActionsSheet
                taskTitle={selectedTask.title}
                onDuplicate={() => void duplicateTask(selectedTask)}
                onShare={() => setSheet('edit-share')}
                onDiscussion={() => openTaskDiscussion(selectedTask)}
                canArchive={(selectedTask.authorId != null && selectedTask.authorId === auth?.user.id)
                  || activeWorkspace(auth)?.role === 'OWNER'}
                onArchive={() => { void archiveTask(selectedTask.id); setSheet(null); goTo('tasks') }}
              />
            )}
            {sheet === 'edit-share' && selectedTask && (
              <ShareSheet
                onSendTelegram={() => shareTaskViaTelegram(selectedTask)}
                onCopy={() => void copyTaskMessage(selectedTask)}
              />
            )}
            {sheet === 'workspace-switch' && auth && (
              <WorkspaceSwitchSheet
                workspaces={auth.workspaces}
                activeWorkspaceId={activeWorkspaceId}
                switching={workspaceSwitching}
                newWorkspaceName={newWorkspaceName}
                creating={creatingWorkspace}
                onSwitch={(workspaceId) => void switchWorkspace(workspaceId)}
                onNewNameChange={setNewWorkspaceName}
                onCreate={() => void createWorkspace()}
              />
            )}
            {sheet === 'appearance' && (
              <AppearanceSheet
                selected={theme}
                onSelect={(value) => {
                  void savePreferences({ theme: value })
                  setSheet(null)
                }}
              />
            )}
            {sheet === 'language' && (
              <LanguageSheet
                selected={lang}
                onSelect={(value) => {
                  void savePreferences({ uiLanguage: value })
                  setSheet(null)
                }}
              />
            )}
            {sheet === 'task-filter' && (
              <TaskFilterSheet
                tasks={visibleTasks}
                members={workspaceMembers}
                currentUserId={auth?.user.id}
                value={taskFilter}
                search={taskSearch}
                onApply={(value) => {
                  setTaskFilter(value)
                  setSheet(null)
                }}
              />
            )}
            {sheet === 'task-group' && (
              <TaskGroupingSheet
                selected={taskGrouping}
                onSelect={(value) => {
                  setTaskGrouping(value)
                  setSheet(null)
                }}
              />
            )}
          </BottomSheet>
        )}
      </div>
    </div>
    </I18nContext.Provider>
  )
}

function TasksScreen({
  tasks,
  allTasks,
  members,
  currentUserId,
  filter,
  grouping,
  search,
  profileName,
  profileInitials,
  onWorkspace,
  onScope,
  onSettings,
  onCreate,
  onTask,
  onSearch,
  onQuickView,
  onFilter,
  onGrouping,
}: {
  tasks: Task[]
  allTasks: Task[]
  members: WorkspaceMember[]
  currentUserId?: number
  filter: TaskDashboardFilter
  grouping: TaskGrouping
  search: string
  profileName: string
  profileInitials: string
  onWorkspace: () => void
  onScope: () => void
  onSettings: () => void
  onCreate: () => void
  onTask: (task: Task) => void
  onSearch: (value: string) => void
  onQuickView: (value: TaskView) => void
  onFilter: () => void
  onGrouping: () => void
}) {
  const { t } = useI18n()
  const quickViews: TaskView[] = ['ACTIVE', 'MINE', 'TODAY', 'OVERDUE']
  const groups = groupDashboardTasks(t, tasks, grouping, members)
  const detailCount = filter.statuses.length + filter.assigneeIds.length + filter.priorities.length
  return (
    <main className="tasks-page page-body">
      <div className="tasks-header">
        <section className="workspace-strip">
          <button className="workspace-main" type="button" onClick={onWorkspace}>
            <span className="workspace-avatar">{profileInitials}</span>
            <span>
              <strong>{profileName}</strong>
              <small>{t('nav.tasksCount', { count: tasks.length })}</small>
            </span>
          </button>
          <button className="workspace-switch" type="button" aria-label={t('nav.changeScope')} onClick={onScope}>
            <ArrowRightLeft size={24} />
          </button>
          <button className="round-action dark" type="button" aria-label={t('nav.settings')} onClick={onSettings}>
            <SlidersHorizontal size={23} />
          </button>
        </section>

        {allTasks.length > 0 && <section className="task-dashboard-controls">
          <div className="task-quick-tabs">
            {quickViews.map((item) => {
              const count = allTasks.filter((task) => matchesDashboardFilter(task, { ...defaultTaskFilter, view: item }, currentUserId, '')).length
              return <button key={item} className={filter.view === item ? 'selected' : ''} type="button" onClick={() => onQuickView(item)}>{t(`quickview.${item}`)}{count > 0 && <span>{count}</span>}</button>
            })}
          </div>
          <div className="task-search-row">
            <label><Search size={19} /><input value={search} onChange={(event) => onSearch(event.target.value)} placeholder={t('nav.search')} /></label>
            <button className={detailCount > 0 ? 'active' : ''} type="button" aria-label={t('nav.filters')} onClick={onFilter}><ListFilter size={22} />{detailCount > 0 && <small>{detailCount}</small>}</button>
            <button className="active" type="button" aria-label={t('nav.view')} onClick={onGrouping}><SlidersHorizontal size={22} /></button>
          </div>
        </section>}
      </div>

      <div className="tasks-scroll">
      {allTasks.length === 0 ? (
        <section className="empty-state">
          <div className="sparkle-box"><Sparkles size={43} /></div>
          <h1>{t('tasks.empty.title')}</h1>
          <p>{t('tasks.empty.subtitle')}</p>
          <div className="empty-state-actions">
            <button className="primary-light" type="button" onClick={onCreate}>
              <Plus size={25} /> {t('tasks.empty.cta')}
            </button>
          </div>
        </section>
      ) : tasks.length === 0 ? (
        <section className="empty-state filtered-empty-state">
          <div className="sparkle-box"><Search size={36} /></div>
          <h1>{t('tasks.filteredEmpty.title')}</h1>
          <p>{t('tasks.filteredEmpty.subtitle')}</p>
        </section>
      ) : (
        <section className="task-list">
          <div className="task-list-heading">
            <div><span>{t('tasks.list.heading')}</span><strong>{t('tasks.list.count', { count: tasks.length })}</strong></div>
            <div className="task-list-heading-actions">
              <button type="button" onClick={onCreate}><Plus size={20} /> {t('tasks.list.new')}</button>
            </div>
          </div>
          {groups.map((group) => (
              <div className="task-result-group" key={group.key}>
                {grouping !== 'LIST' && <div className="task-group-heading"><span>{group.label}</span><strong>{group.tasks.length}</strong></div>}
                {group.tasks.map((task) => <TaskCard key={task.id} task={task} currentUserId={currentUserId} onClick={() => onTask(task)} />)}
              </div>
            ))}
        </section>
      )}
      </div>
    </main>
  )
}

function TaskCard({ task, currentUserId, onClick }: { task: Task; currentUserId?: number; onClick: () => void }) {
  const { t } = useI18n()
  const isOwnTask = task.authorId != null && task.authorId === currentUserId
  const isAssignedByOther = !isOwnTask && taskAssigneeIds(task).includes(currentUserId ?? -1)
  return (
    <button className={`task-card ${isOwnTask ? 'task-card-own' : isAssignedByOther ? 'task-card-assigned' : ''}`} type="button" onClick={onClick}>
      <div className={`priority-mark priority-${task.priority.toLowerCase()}`} />
      <div className="task-card-copy">
        <span>{task.visibility === 'GROUP' ? task.groupName ?? t('place.GROUP') : placeLabel(t, task.visibility)}</span>
        <h2>{task.title}</h2>
        <div className="task-meta">
          <span><Flag size={14} /> {statusLabel(t, task.status)}</span>
          {task.dueAt && <span className={`due-${dueUrgency(task.dueAt, task.status)}`}><Clock3 size={14} /> {formatDeadline(task.dueAt)}</span>}
          {task.checklist && <span>{task.checklist}</span>}
        </div>
        {isOwnTask && <span className="task-owner-tag task-owner-tag-own">{t('task.tag.own')}</span>}
        {isAssignedByOther && (
          <span className="task-owner-tag task-owner-tag-assigned">{t('task.tag.assignedBy', { name: task.author ?? t('common.user') })}</span>
        )}
      </div>
      <ChevronRight size={22} />
    </button>
  )
}

function EditTaskScreen({
  task,
  title,
  description,
  status,
  priority,
  deadline,
  checklist,
  checklistDraft,
  saving,
  detailsLoaded,
  error,
  accessToken,
  newAttachments,
  galleryRef,
  cameraRef,
  fileRef,
  visibility,
  groupId,
  groups,
  assigneeIds,
  workspaceMembers,
  authorId,
  currentUserId,
  onTitle,
  onDescription,
  onQuickStatus,
  onPriority,
  onDeadline,
  onPlace,
  onAuthor,
  onAssigneeToggle,
  onChecklistDraft,
  onChecklistAdd,
  onChecklistToggle,
  onChecklistText,
  onChecklistRemove,
  onAttachments,
  onRemoveAttachment,
  onRemoveExistingAttachment,
  onOtherStatus,
  onShare,
  onActions,
  onSubmit,
  onBack,
}: {
  task: Task
  title: string
  description: string
  status: TaskStatus
  priority: TaskPriority
  deadline?: string
  checklist: ChecklistItem[]
  checklistDraft: string
  saving: boolean
  detailsLoaded: boolean
  error: string
  accessToken?: string
  newAttachments: AttachmentItem[]
  galleryRef: React.RefObject<HTMLInputElement | null>
  cameraRef: React.RefObject<HTMLInputElement | null>
  fileRef: React.RefObject<HTMLInputElement | null>
  visibility: TaskVisibility
  groupId: number | null
  groups: LinkedGroup[]
  assigneeIds: number[]
  workspaceMembers: WorkspaceMember[]
  authorId: number | null
  currentUserId?: number
  onTitle: (value: string) => void
  onDescription: (value: string) => void
  onQuickStatus: (value: TaskStatus) => void
  onPriority: () => void
  onDeadline: () => void
  onPlace: () => void
  onAuthor: () => void
  onAssigneeToggle: (memberId: number) => void
  onChecklistDraft: (value: string) => void
  onChecklistAdd: () => void
  onChecklistToggle: (id: number) => void
  onChecklistText: (id: number, value: string) => void
  onChecklistRemove: (id: number) => void
  onAttachments: (files: FileList | null) => void
  onRemoveAttachment: (id: number) => void
  onRemoveExistingAttachment: (fileId: number) => Promise<void>
  onOtherStatus: () => void
  onShare: () => void
  onActions: () => void
  onSubmit: (event: FormEvent) => void
  onBack: () => void
}) {
  const { t } = useI18n()
  const reopen = status === 'COMPLETED'
  const resume = status === 'NEW' || status === 'BLOCKED'
  const primaryLabel = reopen ? t('edit.status.reopen') : resume ? t('edit.status.start') : t('edit.status.finish')
  const primaryStatus: TaskStatus = reopen || resume ? 'IN_PROGRESS' : 'COMPLETED'
  const isAuthor = task.authorId != null && task.authorId === currentUserId
  const editPlaceLabel = visibility === 'GROUP'
    ? groups.find((group) => group.id === groupId)?.title ?? t('place.GROUP')
    : placeLabel(t, visibility)
  const selectedAuthorMember = workspaceMembers.find((member) => member.id === authorId)
  const authorName = selectedAuthorMember ? workspaceMemberName(t, selectedAuthorMember) : task.author ?? t('common.user')
  const editableGroup = visibility === 'GROUP' ? groups.find((group) => group.id === groupId) : undefined
  const assigneeCandidates: GroupMember[] = visibility === 'GROUP'
    ? editableGroup?.memberList ?? []
    : workspaceMembers.filter((member) => member.active && !member.temporarilyBlocked)
        .map((member) => ({ id: member.id, name: workspaceMemberName(t, member), username: member.username, photoUrl: member.photoUrl }))
  const assigneeNames = visibility === 'PERSONAL'
    ? (task.assignees?.map((person) => person.name) ?? [])
    : assigneeCandidates.filter((member) => assigneeIds.includes(member.id)).map((member) => member.name)

  return (
    <form className="edit-page page-body" onSubmit={onSubmit}>
      <header className="edit-heading">
        <button type="button" className="heading-button" onClick={onBack}><ArrowLeft size={28} /></button>
        <div><h1>{t('edit.title')}</h1><p>{editPlaceLabel}</p></div>
        <button type="button" className="heading-button edit-share" aria-label={t('edit.share')} onClick={onShare}><Share2 size={24} /></button>
        <button type="button" className="heading-button" aria-label={t('edit.moreActions')} onClick={onActions}><MoreHorizontal size={26} /></button>
      </header>

      <div className="edit-content">
        <section className="edit-title-card">
          <label>
            <span>{t('create.name')} <b>*</b></span>
            <input value={title} onChange={(event) => onTitle(event.target.value)} maxLength={300} />
          </label>
          <div className="edit-status-bar">
            <span className={`current-status status-${status.toLowerCase()}`}><i /> {statusLabel(t, status)}</span>
            <button className="primary-status-action" type="button" disabled={saving || !detailsLoaded} onClick={() => onQuickStatus(primaryStatus)}>
              {reopen ? <RotateCcw size={21} /> : <Check size={21} />}{primaryLabel}
            </button>
            <button className="other-status-action" type="button" onClick={onOtherStatus}>
              <Flag size={21} /> {t('edit.status.other')} <ChevronRight size={20} />
            </button>
          </div>
        </section>

        <section className="edit-details-card">
          <div className="task-detail-grid">
            {isAuthor ? (
              <button type="button" onClick={onPlace}><span>{t('edit.field.place')}</span><strong>{editPlaceLabel}</strong></button>
            ) : (
              <div><span>{t('edit.field.place')}</span><strong>{editPlaceLabel}</strong></div>
            )}
            {isAuthor ? (
              <button type="button" onClick={onAuthor}><span>{t('edit.field.author')}</span><strong>{authorName}</strong></button>
            ) : (
              <div><span>{t('edit.field.author')}</span><strong>{authorName}</strong></div>
            )}
            <div><span>{t('edit.field.assignees')}</span><strong>{assigneeNames.length ? assigneeNames.join(', ') : t('edit.field.assignees.empty')}</strong></div>
            <div><span>{t('edit.field.deadline')}</span><strong>{deadline ? formatDeadline(deadline) : t('deadline.none')}</strong></div>
          </div>
          {visibility !== 'PERSONAL' && (
            <section className="assignee-picker">
              <div className="assignee-heading">
                <strong>{t('create.assignee.title')}</strong>
                <span>{visibility === 'ONE_TO_ONE' ? t('create.assignee.oneToOne') : t('create.assignee.optional')}</span>
              </div>
              {assigneeCandidates.length > 0 ? (
                <div className="assignee-list">
                  {assigneeCandidates.map((member) => {
                    const isSelected = assigneeIds.includes(member.id)
                    return (
                      <button
                        className={`assignee-person ${isSelected ? 'selected' : ''}`}
                        type="button"
                        key={member.id}
                        aria-pressed={isSelected}
                        onClick={() => onAssigneeToggle(member.id)}
                      >
                        <span className="assignee-avatar">
                          {member.photoUrl ? <img src={member.photoUrl} alt="" /> : initials(member.name)}
                          {isSelected && <i><Check size={13} /></i>}
                        </span>
                        <small>{member.id === currentUserId ? t('common.you') : member.name}</small>
                      </button>
                    )
                  })}
                </div>
              ) : (
                <p className="assignee-empty">{t('create.assignee.empty')}</p>
              )}
            </section>
          )}
          <div className="edit-option-grid">
            <button type="button" onClick={onDeadline}>
              <CalendarClock size={22} /><span><small>{t('edit.field.deadline')}</small><strong>{deadline ? formatDeadline(deadline) : t('deadline.none')}</strong></span><ChevronRight size={20} />
            </button>
            <button type="button" onClick={onPriority}>
              <AlertTriangle size={22} /><span><small>{t('edit.field.priority')}</small><strong>{priorityLabel(t, priority)}</strong></span><ChevronDown size={20} />
            </button>
          </div>
          <label className="edit-description-field">
            <span>{t('edit.field.description')}</span>
            <textarea
              rows={5}
              value={description}
              onChange={(event) => onDescription(event.target.value)}
              placeholder={t('edit.field.descriptionPlaceholder')}
              maxLength={10000}
            />
          </label>
          <div className="edit-summary-row">
            <span>{t('edit.field.checklist')}</span><strong>{checklist.filter((item) => item.done).length}/{checklist.length}</strong>
          </div>
          <div className="edit-checklist-editor">
            {checklist.map((item) => (
              <div key={item.id} className={item.done ? 'done' : ''}>
                <button type="button" onClick={() => onChecklistToggle(item.id)}>{item.done && <Check size={16} />}</button>
                <input value={item.text} maxLength={500} onChange={(event) => onChecklistText(item.id, event.target.value)} />
                <button type="button" className="remove" onClick={() => onChecklistRemove(item.id)}><X size={17} /></button>
              </div>
            ))}
            <div className="edit-checklist-entry">
              <Plus size={20} />
              <input
                value={checklistDraft}
                maxLength={500}
                placeholder={t('create.checklist.new')}
                onChange={(event) => onChecklistDraft(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    event.preventDefault()
                    onChecklistAdd()
                  }
                }}
              />
              <button type="button" disabled={!checklistDraft.trim()} onClick={onChecklistAdd}>{t('create.checklist.add')}</button>
            </div>
          </div>
          <div className="edit-summary-row">
            <span>{t('edit.field.files')}</span><strong>{t('edit.field.filesLabel', { count: task.files ?? 0 })}</strong>
          </div>
          {!!task.attachments?.length && (
            <div className="task-attachment-gallery">
              {task.attachments.map((attachment) => (
                <TaskAttachmentPreview
                  key={attachment.id}
                  attachment={attachment}
                  accessToken={accessToken}
                  onRemove={() => onRemoveExistingAttachment(attachment.id)}
                />
              ))}
            </div>
          )}
          <AttachmentPicker
            attachments={newAttachments}
            galleryRef={galleryRef}
            cameraRef={cameraRef}
            fileRef={fileRef}
            onAttachments={onAttachments}
            onRemoveAttachment={onRemoveAttachment}
          />
        </section>

        <div className="edit-submit-wrap">
          <button type="submit" disabled={saving || !detailsLoaded || !title.trim()}><Save size={22} /> {saving ? t('common.saving') : detailsLoaded ? t('common.save') : t('common.loading')}</button>
          {error && <span className="form-error">{error}</span>}
        </div>
      </div>
    </form>
  )
}

export function TaskAttachmentPreview({ attachment, accessToken, onRemove }: {
  attachment: TaskAttachment
  accessToken?: string
  onRemove: () => Promise<void>
}) {
  const { t } = useI18n()
  const [objectUrl, setObjectUrl] = useState<string>()
  const [failed, setFailed] = useState(false)
  const [viewerOpen, setViewerOpen] = useState(false)
  const [removing, setRemoving] = useState(false)
  const image = isImageAttachment(attachment.name, attachment.contentType)
  const audio = !image && isAudioAttachment(attachment.name, attachment.contentType)
  const available = attachment.available !== false

  useEffect(() => {
    if (!accessToken || !available) {
      if (!available) setFailed(true)
      return
    }
    let disposed = false
    let nextObjectUrl: string | undefined
    void fetch(attachment.url, { headers: { Authorization: `Bearer ${accessToken}` }, cache: 'no-store' })
      .then(async (response) => {
        if (!response.ok) throw new Error('Fayl ochilmadi')
        return response.blob()
      })
      .then((blob) => {
        if (disposed) return
        nextObjectUrl = URL.createObjectURL(blob)
        setObjectUrl(nextObjectUrl)
      })
      .catch(() => {
        if (!disposed) setFailed(true)
      })
    return () => {
      disposed = true
      if (nextObjectUrl) URL.revokeObjectURL(nextObjectUrl)
    }
  }, [accessToken, attachment.url, available])

  return (
    <div className={`task-attachment-preview ${image ? 'image' : audio ? 'audio' : 'file'} ${failed ? 'failed' : ''}`}>
      <button className="attachment-open" type="button" disabled={!objectUrl} onClick={() => setViewerOpen(true)}>
        <span>
          {image && objectUrl ? <img src={objectUrl} alt={attachment.name} />
            : audio ? <Mic size={25} />
              : <FileText size={25} />}
        </span>
        <strong>{attachment.name}</strong>
        <small>{failed ? (available ? t('edit.attachment.failed') : t('edit.attachment.reupload')) : formatFileSize(attachment.size)}</small>
      </button>
      <button
        className="attachment-remove"
        type="button"
        disabled={removing}
        aria-label={t('attachment.delete')}
        onClick={() => {
          setRemoving(true)
          void onRemove().catch(() => setRemoving(false))
        }}
      ><X size={15} /></button>
      {viewerOpen && objectUrl && (
        <AttachmentViewer url={objectUrl} name={attachment.name} image={image} audio={audio} onClose={() => setViewerOpen(false)} />
      )}
    </div>
  )
}

export function StatusSheet({ taskTitle, selected, onSelect }: {
  taskTitle: string
  selected: TaskStatus
  onSelect: (status: TaskStatus) => void
}) {
  const { t } = useI18n()
  const statuses: TaskStatus[] = ['NEW', 'IN_PROGRESS', 'BLOCKED', 'REVIEW', 'COMPLETED']
  const [draftStatus, setDraftStatus] = useState(selected)
  return (
    <div className="status-sheet sheet-content">
      <h2>{t('status.title')}</h2>
      <p>{taskTitle}</p>
      <div className="status-options">
        {statuses.map((status) => (
          <button
            type="button"
            key={status}
            className={`${draftStatus === status ? 'selected' : ''} status-${status.toLowerCase()}`}
            onClick={() => setDraftStatus(status)}
          >
            <i />
            <strong>{statusLabel(t, status)}</strong>
            {draftStatus === status && <Check size={23} />}
          </button>
        ))}
      </div>
      <button className="status-sheet-save" type="button" onClick={() => onSelect(draftStatus)}>
        <Save size={22} /> {t('common.save')}
      </button>
    </div>
  )
}

export function PrioritySheet({ selected, onSelect }: {
  selected: TaskPriority
  onSelect: (priority: TaskPriority) => void
}) {
  const { t } = useI18n()
  const priorities: TaskPriority[] = ['LOW', 'NORMAL', 'IMPORTANT', 'URGENT']
  return (
    <div className="priority-sheet sheet-content">
      <h2>{t('priority.title')}</h2>
      <div className="priority-options">
        {priorities.map((item) => (
          <button
            type="button"
            key={item}
            className={selected === item ? 'selected' : ''}
            onClick={() => onSelect(item)}
          >
            <span className={`priority-option-icon priority-${item.toLowerCase()}`}>
              <AlertTriangle size={21} />
            </span>
            <strong>{priorityLabel(t, item)}</strong>
            {selected === item && <Check size={22} />}
          </button>
        ))}
      </div>
    </div>
  )
}

interface CreateScreenProps {
  title: string
  description: string
  status: TaskStatus
  priority: TaskPriority
  visibility: TaskVisibility
  placeLabel: string
  deadline?: string
  moreOpen: boolean
  checklist: ChecklistItem[]
  checklistDraft: string
  attachments: AttachmentItem[]
  selectedGroup?: LinkedGroup
  assigneeIds: number[]
  currentUserId?: number
  saving: boolean
  error: string
  galleryRef: React.RefObject<HTMLInputElement | null>
  cameraRef: React.RefObject<HTMLInputElement | null>
  fileRef: React.RefObject<HTMLInputElement | null>
  onTitle: (value: string) => void
  onDescription: (value: string) => void
  onStatus: () => void
  onPriority: () => void
  onDeadline: () => void
  onPlace: () => void
  onAssigneeToggle: (memberId: number) => void
  onAddBot: () => void
  onMore: () => void
  onChecklistDraft: (value: string) => void
  onChecklistAdd: () => void
  onChecklistToggle: (id: number) => void
  onAttachments: (files: FileList | null) => void
  onRemoveAttachment: (id: number) => void
  onSubmit: (event: FormEvent) => void
  onClose: () => void
}

function CreateScreen(props: CreateScreenProps) {
  const { t } = useI18n()
  return (
    <form className="create-page page-body" onSubmit={props.onSubmit}>
      <PageHeading title={t('create.title')} subtitle={props.placeLabel} onClose={props.onClose} />

      <div className="create-content">
        <label className="title-field">
          <span>{t('create.name')} <b>*</b></span>
          <textarea
            rows={2}
            value={props.title}
            onChange={(event) => props.onTitle(event.target.value)}
            placeholder={t('create.namePlaceholder')}
          />
        </label>

        <button className="create-row deadline-row" type="button" onClick={props.onDeadline}>
          <span className="row-icon"><CalendarClock size={25} /></span>
          <strong>{props.deadline ? formatDeadline(props.deadline) : t('create.deadline.none')}</strong>
          <ChevronRight size={22} />
        </button>

        <button className="create-row" type="button" onClick={props.onPlace}>
          <span className="row-icon"><Folder size={27} /></span>
          <strong>{t('create.place')}</strong>
          <span className="row-value">{props.placeLabel}</span>
          <ChevronRight size={22} />
        </button>

        {props.visibility === 'GROUP' && props.selectedGroup && (
          <section className="assignee-picker">
            {!props.selectedGroup.botConnected && (
              <div className="bot-membership-warning">
                <div>
                  <strong>{t('create.bot.warning.title')}</strong>
                  <small>{t('create.bot.warning.subtitle')}</small>
                </div>
                <button type="button" onClick={props.onAddBot}>{t('create.bot.add')}</button>
              </div>
            )}
            <div className="assignee-heading">
              <strong>{t('create.assignee.title')}</strong>
              <span>{t('create.assignee.required')}</span>
            </div>
            {props.selectedGroup.memberList.length > 0 ? (
              <div className="assignee-list">
                {props.selectedGroup.memberList.map((member) => {
                  const selected = props.assigneeIds.includes(member.id)
                  return (
                    <button
                      className={`assignee-person ${selected ? 'selected' : ''}`}
                      type="button"
                      key={member.id}
                      aria-pressed={selected}
                      onClick={() => props.onAssigneeToggle(member.id)}
                    >
                      <span className="assignee-avatar">
                        {member.photoUrl ? <img src={member.photoUrl} alt="" /> : initials(member.name)}
                        {selected && <i><Check size={13} /></i>}
                      </span>
                      <small>{member.id === props.currentUserId ? t('common.you') : member.name}</small>
                    </button>
                  )
                })}
              </div>
            ) : (
              <p className="assignee-empty">{t('create.assignee.emptyGroup')}</p>
            )}
          </section>
        )}

        <section className={`more-card ${props.moreOpen ? 'open' : ''}`}>
          <button className="more-toggle" type="button" onClick={props.onMore}>
            <SlidersHorizontal size={28} />
            <span><strong>{t('create.more')}</strong><small>{props.moreOpen ? t('create.more.open') : t('create.more.closed')}</small></span>
            {props.moreOpen ? <ChevronDown size={23} /> : <ChevronRight size={23} />}
          </button>

          {props.moreOpen && (
            <div className="more-fields">
              <div className="dual-fields">
                <CycleField
                  icon={<Flag size={24} />}
                  label={t('field.status')}
                  value={statusLabel(t, props.status)}
                  onClick={props.onStatus}
                />
                <CycleField
                  icon={<AlertTriangle size={24} />}
                  label={t('edit.field.priority')}
                  value={priorityLabel(t, props.priority)}
                  onClick={props.onPriority}
                />
              </div>

              <label className="description-field">
                <span>{t('edit.field.description')}</span>
                <textarea
                  rows={3}
                  value={props.description}
                  onChange={(event) => props.onDescription(event.target.value)}
                  placeholder={t('edit.field.descriptionPlaceholder')}
                />
              </label>

              <div className="checklist-area">
                <span className="field-caption">{t('edit.field.checklist')}</span>
                {props.checklist.map((item) => (
                  <button
                    key={item.id}
                    className={`checklist-item ${item.done ? 'done' : ''}`}
                    type="button"
                    onClick={() => props.onChecklistToggle(item.id)}
                  >
                    <span>{item.done && <Check size={16} />}</span>{item.text}
                  </button>
                ))}
                <div className="checklist-entry">
                  <Plus size={22} />
                  <input
                    value={props.checklistDraft}
                    onChange={(event) => props.onChecklistDraft(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter') {
                        event.preventDefault()
                        props.onChecklistAdd()
                      }
                    }}
                    placeholder={t('field.checklistPlaceholder')}
                  />
                  {props.checklistDraft && <button type="button" onClick={props.onChecklistAdd}>{t('create.checklist.add')}</button>}
                </div>
              </div>

              <AttachmentPicker
                attachments={props.attachments}
                galleryRef={props.galleryRef}
                cameraRef={props.cameraRef}
                fileRef={props.fileRef}
                onAttachments={props.onAttachments}
                onRemoveAttachment={props.onRemoveAttachment}
              />
            </div>
          )}
        </section>

        <div className="create-submit-wrap">
          <button className="create-submit" type="submit" disabled={!props.title.trim() || props.saving}>
            {props.saving ? t('common.saving') : t('create.submit')}
          </button>
          {props.error && <span className="form-error">{props.error}</span>}
        </div>
      </div>
    </form>
  )
}

function CycleField({ icon, label, value, onClick }: { icon: ReactNode; label: string; value: string; onClick: () => void }) {
  return (
    <button className="cycle-field" type="button" onClick={onClick}>
      {icon}<span><small>{label}</small><strong>{value}</strong></span><ChevronDown size={20} />
    </button>
  )
}

export function AttachmentPicker({ attachments, galleryRef, cameraRef, fileRef, onAttachments, onRemoveAttachment }: {
  attachments: AttachmentItem[]
  galleryRef: React.RefObject<HTMLInputElement | null>
  cameraRef: React.RefObject<HTMLInputElement | null>
  fileRef: React.RefObject<HTMLInputElement | null>
  onAttachments: (files: FileList | null) => void
  onRemoveAttachment: (id: number) => void
}) {
  const { t } = useI18n()
  const selectFiles = (event: React.ChangeEvent<HTMLInputElement>) => {
    const input = event.currentTarget
    if (input.files?.length) onAttachments(input.files)
    window.setTimeout(() => { input.value = '' }, 0)
  }
  return (
    <div className="files-area attachment-picker">
      <div className="files-title"><Paperclip size={24} /><strong>{t('attachment.filesTitle')}</strong></div>
      <div className="file-actions">
        <label className="file-input-action">
          <input ref={galleryRef} className="file-input-control" type="file" accept="image/*" multiple onChange={selectFiles} />
          <ImageIcon size={27} /><strong>{t('attachment.gallery')}</strong>
        </label>
        <label className="file-input-action">
          <input ref={cameraRef} className="file-input-control" type="file" accept="image/*" capture="environment" onChange={selectFiles} />
          <Camera size={27} /><strong>{t('attachment.camera')}</strong>
        </label>
        <label className="file-input-action">
          <input ref={fileRef} className="file-input-control" type="file" multiple onChange={selectFiles} />
          <FileText size={27} /><strong>{t('attachment.file')}</strong>
        </label>
      </div>
      {!!attachments.length && (
        <>
          <div className="attachment-selection-note"><Check size={16} /> {t('attachment.addedCount', { count: attachments.length })}</div>
          <div className="local-attachment-gallery">
            {attachments.map((attachment) => (
              <LocalAttachmentPreview key={attachment.id} attachment={attachment} onRemove={() => onRemoveAttachment(attachment.id)} />
            ))}
          </div>
        </>
      )}
    </div>
  )
}

function LocalAttachmentPreview({ attachment, onRemove }: { attachment: AttachmentItem; onRemove: () => void }) {
  const { t } = useI18n()
  const [objectUrl, setObjectUrl] = useState<string>()
  const [viewerOpen, setViewerOpen] = useState(false)
  const image = isImageAttachment(attachment.name, attachment.file.type)
  const audio = !image && isAudioAttachment(attachment.name, attachment.file.type)
  useEffect(() => {
    const url = URL.createObjectURL(attachment.file)
    setObjectUrl(url)
    return () => URL.revokeObjectURL(url)
  }, [attachment.file, image])
  return (
    <div className={`local-attachment-preview ${audio ? 'audio' : ''}`}>
      <button className="attachment-open" type="button" disabled={!objectUrl} onClick={() => setViewerOpen(true)}>
        <span>
          {image && objectUrl ? <img src={objectUrl} alt={attachment.name} />
            : audio ? <Mic size={25} />
              : <FileText size={25} />}
        </span>
        <strong>{attachment.name}</strong>
        <small>{formatFileSize(attachment.file.size)}</small>
      </button>
      <button className="attachment-remove" type="button" aria-label={t('attachment.removeSelected')} onClick={onRemove}><X size={15} /></button>
      {viewerOpen && objectUrl && (
        <AttachmentViewer url={objectUrl} name={attachment.name} image={image} audio={audio} onClose={() => setViewerOpen(false)} />
      )}
    </div>
  )
}

function AttachmentViewer({ url, name, image, audio, onClose }: {
  url: string
  name: string
  image: boolean
  audio?: boolean
  onClose: () => void
}) {
  const { t } = useI18n()
  return (
    <div className="attachment-viewer" role="dialog" aria-modal="true" onMouseDown={(event) => {
      if (event.currentTarget === event.target) onClose()
    }}>
      <button className="attachment-viewer-close" type="button" aria-label={t('attachment.close')} onClick={onClose}><X size={25} /></button>
      {image
        ? <img src={url} alt={name} />
        : audio
          ? <div className="attachment-viewer-audio"><Mic size={44} /><strong>{name}</strong><audio controls src={url} /></div>
          : <a href={url} target="_blank" rel="noreferrer"><FileText size={44} /><strong>{name}</strong><span>{t('attachment.openFile')}</span></a>}
    </div>
  )
}

export function DeadlineScreen({
  value,
  reminderMinutes,
  subtitle,
  onChange,
  onReminderChange,
  onDone,
  onBack,
}: {
  value?: string
  reminderMinutes?: number
  subtitle: string
  onChange: (value: string | undefined) => void
  onReminderChange: (value: number | undefined) => void
  onDone: () => void
  onBack: () => void
}) {
  const { t, lang } = useI18n()
  const initial = value ? new Date(value) : nextAvailableHour()
  const [month, setMonth] = useState(new Date(initial.getFullYear(), initial.getMonth(), 1))
  const [selectedDate, setSelectedDate] = useState(initial.getDate())
  const [selectedTime, setSelectedTime] = useState(`${pad(initial.getHours())}:${pad(initial.getMinutes())}`)
  const [noDeadline, setNoDeadline] = useState(!value)
  const [timeError, setTimeError] = useState('')
  const [reminderOpen, setReminderOpen] = useState(false)
  const days = calendarDays(month)
  const todayPreset = deadlinePreset('today')
  const tomorrowPreset = deadlinePreset('tomorrow')

  function choosePreset(type: 'none' | 'today' | 'tomorrow') {
    if (type === 'none') {
      setNoDeadline(true)
      onReminderChange(undefined)
      return
    }
    const date = type === 'today' ? deadlinePreset('today') : deadlinePreset('tomorrow')
    setNoDeadline(false)
    setTimeError('')
    setMonth(new Date(date.getFullYear(), date.getMonth(), 1))
    setSelectedDate(date.getDate())
    setSelectedTime(`${pad(date.getHours())}:${pad(date.getMinutes())}`)
  }

  function finish() {
    if (noDeadline) {
      onChange(undefined)
      onReminderChange(undefined)
    }
    else {
      const [hours, minutes] = selectedTime.split(':').map(Number)
      const selected = new Date(month.getFullYear(), month.getMonth(), selectedDate, hours, minutes)
      if (!selectedTime || Number.isNaN(selected.getTime()) || selected <= new Date()) {
        setTimeError(t('deadline.error.future'))
        return
      }
      if (reminderMinutes !== undefined
          && selected.getTime() - reminderMinutes * 60_000 <= Date.now()) {
        setTimeError(t('deadline.error.reminderPast'))
        return
      }
      onChange(selected.toISOString())
    }
    onDone()
  }

  function changeMonth(offset: number) {
    const next = new Date(month.getFullYear(), month.getMonth() + offset, 1)
    setMonth(next)
    setSelectedDate((current) => Math.min(current,
      new Date(next.getFullYear(), next.getMonth() + 1, 0).getDate()))
    setTimeError('')
  }

  return (
    <main className="deadline-page page-body">
      <PageHeading title={t('deadline.pageTitle')} subtitle={subtitle} onBack={onBack} />
      <div className="deadline-content">
        <div className="deadline-presets">
          <button className={noDeadline ? 'active' : ''} type="button" onClick={() => choosePreset('none')}>{t('deadline.none')}</button>
          <button type="button" onClick={() => choosePreset('today')}>{t('deadline.today')} {pad(todayPreset.getHours())}:{pad(todayPreset.getMinutes())}</button>
          <button type="button" onClick={() => choosePreset('tomorrow')}>{t('deadline.tomorrow')} {pad(tomorrowPreset.getHours())}:{pad(tomorrowPreset.getMinutes())}</button>
        </div>
        <div className="time-row">
          <Clock3 size={27} />
          <label className="custom-time-field">
            <span>{t('deadline.customTime')}</span>
            <input
              type="time"
              step="60"
              value={selectedTime}
              onChange={(event) => {
                setNoDeadline(false)
                setSelectedTime(event.target.value)
                setTimeError('')
              }}
            />
          </label>
          {['10:00', '15:00', '18:00'].map((time) => (
            <button className={!noDeadline && selectedTime === time ? 'active' : ''} key={time} type="button" onClick={() => { setNoDeadline(false); setSelectedTime(time); setTimeError('') }}>{time}</button>
          ))}
        </div>
        <section className="calendar-card">
          <div className="calendar-head">
            <button type="button" onClick={() => changeMonth(-1)}><ChevronLeft /></button>
            <strong>{monthName(month, lang)}</strong>
            <button type="button" onClick={() => changeMonth(1)}><ChevronRight /></button>
          </div>
          <div className="weekdays">{[0, 1, 2, 3, 4, 5, 6].map((day) => <span key={day}>{t(`deadline.weekday.${day}`)}</span>)}</div>
          <div className="calendar-grid">
            {days.map((day, index) => (
              <button
                key={`${day.date}-${index}`}
                className={`${day.current ? '' : 'outside'} ${day.current && selectedDate === day.date && !noDeadline ? 'selected' : ''}`}
                type="button"
                onClick={() => {
                  if (!day.current) return
                  setNoDeadline(false)
                  setSelectedDate(day.date)
                  setTimeError('')
                }}
              >{day.date}</button>
            ))}
          </div>
        </section>
        <button
          className="deadline-reminder-row"
          type="button"
          disabled={noDeadline}
          onClick={() => setReminderOpen(true)}
        >
          <Bell size={22} />
          <strong>{t('deadline.reminder')}</strong>
          <span>{noDeadline ? t('deadline.reminder.chooseFirst') : reminderMinutes === undefined ? t('deadline.reminder.notSet') : reminderLabel(t, reminderMinutes)}</span>
          <ChevronRight size={20} />
        </button>
        {timeError && <span className="deadline-error">{timeError}</span>}
        <button className="deadline-done" type="button" onClick={finish}>{t('deadline.done')} · {noDeadline ? t('deadline.none') : `${selectedDate} ${monthName(month, lang).split(',')[0]} · ${selectedTime}`}</button>
      </div>
      {reminderOpen && (
        <BottomSheet onClose={() => setReminderOpen(false)}>
          <ReminderPickerSheet
            selected={reminderMinutes}
            onSave={(value) => {
              onReminderChange(value)
              setReminderOpen(false)
              setTimeError('')
            }}
          />
        </BottomSheet>
      )}
    </main>
  )
}

export function ReminderPickerSheet({ selected, onSave }: {
  selected?: number
  onSave: (value: number | undefined) => void
}) {
  const { t } = useI18n()
  const [draft, setDraft] = useState<number | undefined>(selected)
  const options = [0, 5, 15, 30, 60, 1440]
  return (
    <div className="reminder-sheet sheet-content">
      <h2>{t('reminder.pickerTitle')}</h2>
      <p>{t('reminder.pickerSubtitle')}</p>
      <div className="reminder-options">
        {options.map((minutes) => (
          <button
            type="button"
            key={minutes}
            className={draft === minutes ? 'selected' : ''}
            onClick={() => setDraft(minutes)}
          >
            <strong>{reminderLabel(t, minutes)}</strong>
            {draft === minutes && <Check size={21} />}
          </button>
        ))}
      </div>
      <div className="reminder-sheet-actions">
        <button type="button" onClick={() => { setDraft(undefined); onSave(undefined) }}>{t('reminder.clear')}</button>
        <button type="button" onClick={() => onSave(draft)}>{t('common.save')}</button>
      </div>
    </div>
  )
}

function SettingsScreen({
  userName,
  workspaceName,
  theme,
  remindersEnabled,
  groupsCount,
  membersCount,
  onWorkspace,
  onSwitchWorkspace,
  onAppearance,
  onLanguage,
  onNotificationsToggle,
  onHelp,
  onGroups,
  onMembers,
  onTaskRules,
  onArchive,
  onClose,
  onLogout,
}: {
  userName: string
  workspaceName: string
  theme: Theme
  remindersEnabled: boolean
  groupsCount: number
  membersCount: number
  onWorkspace: () => void
  onSwitchWorkspace: () => void
  onAppearance: () => void
  onLanguage: () => void
  onNotificationsToggle: () => void
  onHelp: () => void
  onGroups: () => void
  onMembers: () => void
  onTaskRules: () => void
  onArchive: () => void
  onClose: () => void
  onLogout?: () => void
}) {
  const { t, lang } = useI18n()
  const appearanceLabel = theme === 'dark' ? t('settings.appearance.dark')
    : theme === 'light' ? t('settings.appearance.light') : t('settings.appearance.system')
  const languageLabel = LANGUAGES.find((item) => item.value === lang)?.label ?? lang
  return (
    <main className="settings-page page-body">
      <PageHeading title={t('settings.title')} subtitle={`${userName} · ${t('settings.owner')}`} onClose={onClose} compactClose />
      <button className="profile-workspace" type="button" onClick={onSwitchWorkspace}>
        <span className="profile-avatar">@W</span>
        <span><strong>{workspaceName}</strong><small>{userName} · {t('settings.owner')}</small></span>
        <ArrowRightLeft size={24} />
      </button>
      <label className="settings-search"><Search size={24} /><input placeholder={t('settings.search')} /></label>
      <SettingsGroup title={t('settings.group.personal')}>
        <SettingsRow tone="purple" icon={<Moon />} title={t('settings.appearance')} subtitle={appearanceLabel} onClick={onAppearance} />
        <SettingsRow tone="cyan" icon={<Languages />} title={t('settings.language')} subtitle={languageLabel} onClick={onLanguage} />
        <SettingsRow tone="red" icon={<Bell />} title={t('settings.notifications')}
          subtitle={remindersEnabled ? t('settings.notifications.on') : t('settings.notifications.off')}
          onClick={onNotificationsToggle} arrow={false} />
        <SettingsRow tone="blue" icon={<HelpCircle />} title={t('settings.help')} subtitle={t('settings.help.subtitle')} onClick={onHelp} />
      </SettingsGroup>
      <SettingsGroup title={t('settings.group.workspace')}>
        <SettingsRow tone="gray" icon={<Settings2 />} title={t('settings.workspace')} subtitle={workspaceName} onClick={onWorkspace} />
        <SettingsRow tone="cyan" icon={<ShieldCheck />} title={t('settings.access')}
          subtitle={t('settings.access.subtitle', { count: membersCount })} onClick={onMembers} />
        <SettingsRow tone="green" icon={<Folder />} title={t('settings.groups')}
          subtitle={t('settings.groups.subtitle', { count: groupsCount })} onClick={onGroups} />
        <SettingsRow tone="purple" icon={<SlidersHorizontal />} title={t('settings.taskRules')} subtitle={t('settings.taskRules.subtitle')} onClick={onTaskRules} />
        <SettingsRow tone="gray" icon={<Archive />} title={t('settings.archive')} subtitle={t('settings.archive.subtitle')} onClick={onArchive} />
      </SettingsGroup>
      {onLogout && (
        <div className="settings-group logout-group">
          <SettingsRow tone="red" icon={<LogOut />} title={t('settings.logout')} subtitle={t('settings.logout.subtitle')} arrow={false} onClick={onLogout} />
        </div>
      )}
    </main>
  )
}

export function SettingsGroup({ title, children }: { title: string; children: ReactNode }) {
  return <section className="settings-group"><h2>{title}</h2>{children}</section>
}

export function SettingsRow({
  tone,
  icon,
  title,
  subtitle,
  arrow = true,
  onClick,
}: {
  tone: string
  icon: ReactNode
  title: string
  subtitle: string
  arrow?: boolean
  onClick?: () => void
}) {
  return (
    <button className="settings-row" type="button" onClick={onClick}>
      <span className={`settings-icon tone-${tone}`}>{icon}</span>
      <span className="settings-copy"><strong>{title}</strong><small>{subtitle}</small></span>
      {arrow && <ChevronRight size={23} />}
    </button>
  )
}

function WorkspaceSwitchSheet({
  workspaces,
  activeWorkspaceId,
  switching,
  newWorkspaceName,
  creating,
  onSwitch,
  onNewNameChange,
  onCreate,
}: {
  workspaces: Array<{ id: number; name: string; role: string }>
  activeWorkspaceId: number | null
  switching: boolean
  newWorkspaceName: string
  creating: boolean
  onSwitch: (workspaceId: number) => void
  onNewNameChange: (value: string) => void
  onCreate: () => void
}) {
  const { t } = useI18n()
  return (
    <div className="sheet-content">
      <h2>{t('workspace.switch.title')}</h2>
      {workspaces.map((workspace) => (
        <ChoiceRow
          key={workspace.id}
          selected={workspace.id === activeWorkspaceId}
          initials={initials(workspace.name)}
          title={workspace.name}
          subtitle={workspace.role === 'OWNER' ? t('workspace.switch.role.OWNER') : t('workspace.switch.role.MEMBER')}
          onClick={() => onSwitch(workspace.id)}
        />
      ))}
      {switching && <p className="sheet-intro">{t('workspace.switch.switching')}</p>}
      <div className="workspace-add-row">
        <input
          value={newWorkspaceName}
          onChange={(event) => onNewNameChange(event.target.value)}
          placeholder={t('workspace.switch.addPlaceholder')}
          maxLength={160}
        />
        <button type="button" disabled={creating || !newWorkspaceName.trim()} onClick={onCreate}>
          <Plus size={20} /> {creating ? t('workspace.switch.creating') : t('workspace.switch.add')}
        </button>
      </div>
    </div>
  )
}

function AppearanceSheet({ selected, onSelect }: { selected: Theme; onSelect: (value: Theme) => void }) {
  const { t } = useI18n()
  const options: Array<{ value: Theme; icon: ReactNode; subtitleKey: string }> = [
    { value: 'system', icon: <SlidersHorizontal />, subtitleKey: 'appearance.system.subtitle' },
    { value: 'dark', icon: <Moon />, subtitleKey: 'appearance.dark.subtitle' },
    { value: 'light', icon: <Sparkles />, subtitleKey: 'appearance.light.subtitle' },
  ]
  return (
    <div className="sheet-content">
      <h2>{t('appearance.title')}</h2>
      {options.map((option) => (
        <ChoiceRow
          key={option.value}
          selected={selected === option.value}
          icon={option.icon}
          title={t(`settings.appearance.${option.value}`)}
          subtitle={t(option.subtitleKey)}
          onClick={() => onSelect(option.value)}
        />
      ))}
    </div>
  )
}

function LanguageSheet({ selected, onSelect }: { selected: Lang; onSelect: (value: Lang) => void }) {
  const { t } = useI18n()
  return (
    <div className="sheet-content">
      <h2>{t('language.title')}</h2>
      {LANGUAGES.map((option) => (
        <ChoiceRow
          key={option.value}
          selected={selected === option.value}
          icon={<Languages />}
          title={option.label}
          subtitle=""
          onClick={() => onSelect(option.value)}
        />
      ))}
    </div>
  )
}

export function HelpScreen({ onBack }: { onBack: () => void }) {
  const { t } = useI18n()
  const faqItems = [
    { q: t('help.faq.q1'), a: t('help.faq.a1') },
    { q: t('help.faq.q2'), a: t('help.faq.a2') },
    { q: t('help.faq.q3'), a: t('help.faq.a3') },
  ]
  return (
    <main className="settings-page page-body">
      <PageHeading title={t('help.title')} subtitle={t('help.intro')} onBack={onBack} onClose={onBack} />
      <SettingsGroup title={t('help.contact.title')}>
        <SettingsRow
          tone="blue"
          icon={<Send size={20} />}
          title={t('help.contact.title')}
          subtitle={t('help.contact.subtitle')}
          onClick={() => window.Telegram?.WebApp?.openTelegramLink?.('https://t.me/task_appbot')}
        />
      </SettingsGroup>
      <section className="settings-group">
        <h2>{t('help.faq.title')}</h2>
        <div className="faq-list">
          {faqItems.map((item) => (
            <div className="faq-item" key={item.q}>
              <strong>{item.q}</strong>
              <p>{item.a}</p>
            </div>
          ))}
        </div>
      </section>
    </main>
  )
}

export function ArchiveScreen({
  tasks,
  loading,
  onBack,
  onRestore,
  onDeleteForever,
  currentUserId,
  isOwner,
}: {
  tasks: Task[]
  loading: boolean
  onBack: () => void
  onRestore: (taskId: number) => void
  onDeleteForever: (taskId: number) => void
  currentUserId?: number
  isOwner?: boolean
}) {
  const { t } = useI18n()
  async function confirmDelete(taskId: number) {
    const confirmed = await showTelegramConfirm(t('archive.deleteForever.confirm'))
    if (confirmed) onDeleteForever(taskId)
  }
  return (
    <main className="settings-page page-body">
      <PageHeading title={t('archive.title')} subtitle={t('archive.subtitle', { count: tasks.length })} onBack={onBack} onClose={onBack} />
      <section className="settings-group archive-list">
        {loading ? (
          <p className="sheet-intro">{t('common.loading')}</p>
        ) : tasks.length === 0 ? (
          <p className="sheet-intro">{t('archive.empty')}</p>
        ) : (
          tasks.map((task) => (
            <div className="archive-row" key={task.id}>
              <div className="archive-row-copy">
                <strong>{task.title}</strong>
                <small>{task.archivedAt ? t('archive.archivedAt', { date: formatDeadline(task.archivedAt) }) : ''}</small>
              </div>
              <div className="archive-row-actions">
                {(isOwner || (task.authorId != null && task.authorId === currentUserId)) && (
                  <>
                    <button type="button" className="archive-restore" onClick={() => onRestore(task.id)}>{t('archive.restore')}</button>
                    <button type="button" className="archive-delete" onClick={() => void confirmDelete(task.id)}>{t('archive.deleteForever')}</button>
                  </>
                )}
              </div>
            </div>
          ))
        )}
      </section>
    </main>
  )
}

function GroupsScreen({
  groups,
  onBack,
  onAddGroup,
}: {
  groups: LinkedGroup[]
  onBack: () => void
  onAddGroup: () => void
}) {
  const { t } = useI18n()
  return (
    <main className="settings-page page-body">
      <PageHeading title={t('groups.title')} subtitle={t('settings.groups.subtitle', { count: groups.length })} onBack={onBack} onClose={onBack} />
      <section className="settings-group">
        {groups.length === 0 ? (
          <p className="sheet-intro">{t('groups.empty')}</p>
        ) : (
          groups.map((group) => (
            <SettingsRow
              key={group.id}
              tone={group.botConnected ? 'green' : 'orange'}
              icon={<Folder />}
              title={group.title}
              subtitle={`${t('groups.membersCount', { count: group.members })} · ${group.botConnected ? t('groups.botConnected') : t('groups.botMissing')}`}
              arrow={false}
            />
          ))
        )}
      </section>
      <div className="settings-group logout-group">
        <SettingsRow tone="blue" icon={<Plus />} title={t('groups.add')} subtitle="" onClick={onAddGroup} />
      </div>
    </main>
  )
}

export function MembersScreen({
  members,
  currentUserId,
  onBack,
}: {
  members: WorkspaceMember[]
  currentUserId?: number
  onBack: () => void
}) {
  const { t } = useI18n()
  return (
    <main className="settings-page page-body">
      <PageHeading title={t('members.title')} subtitle={t('settings.access.subtitle', { count: members.length })} onBack={onBack} onClose={onBack} />
      <section className="settings-group">
        {members.length === 0 ? (
          <p className="sheet-intro">{t('members.empty')}</p>
        ) : (
          members.map((member) => {
            const name = workspaceMemberName(t, member)
            const roleLabel = member.roleCode === 'OWNER' ? t('members.role.OWNER') : t('members.role.MEMBER')
            const statusLabel = member.temporarilyBlocked ? t('members.status.blocked') : t('members.status.active')
            return (
              <SettingsRow
                key={member.id}
                tone={member.roleCode === 'OWNER' ? 'purple' : 'gray'}
                icon={<span>{initials(name)}</span>}
                title={member.id === currentUserId ? `${name} (${t('common.you')})` : name}
                subtitle={`${roleLabel} · ${statusLabel}`}
                arrow={false}
              />
            )
          })
        )}
      </section>
    </main>
  )
}

export function TaskRulesScreen({
  groups,
  onBack,
  onChangePolicy,
}: {
  groups: LinkedGroup[]
  onBack: () => void
  onChangePolicy: (groupId: number, policy: string) => void
}) {
  const { t } = useI18n()
  return (
    <main className="settings-page page-body">
      <PageHeading title={t('taskRules.title')} subtitle={t('taskRules.subtitle')} onBack={onBack} onClose={onBack} />
      {groups.length === 0 ? (
        <p className="sheet-intro">{t('taskRules.empty')}</p>
      ) : (
        groups.map((group) => (
          <SettingsGroup title={group.title} key={group.id}>
            <ChoiceRow
              selected={(group.taskCreationPolicy ?? 'EVERYONE') === 'EVERYONE'}
              icon={<UsersRound />}
              title={t('taskRules.policy.EVERYONE')}
              subtitle={t('taskRules.policy.EVERYONE.subtitle')}
              onClick={() => onChangePolicy(group.id, 'EVERYONE')}
            />
            <ChoiceRow
              selected={group.taskCreationPolicy === 'OWNER_ONLY'}
              icon={<ShieldCheck />}
              title={t('taskRules.policy.OWNER_ONLY')}
              subtitle={t('taskRules.policy.OWNER_ONLY.subtitle')}
              onClick={() => onChangePolicy(group.id, 'OWNER_ONLY')}
            />
          </SettingsGroup>
        ))
      )}
    </main>
  )
}

export function WorkspaceScreen({
  workspaceName,
  onWorkspaceName,
  onBack,
}: {
  workspaceName: string
  onWorkspaceName: (value: string) => void
  onBack: () => void
}) {
  const { t } = useI18n()
  const [saved, setSaved] = useState(false)
  return (
    <main className="workspace-page page-body">
      <PageHeading title={t('workspace.title')} subtitle={workspaceName} onBack={onBack} onClose={onBack} />
      <section className="workspace-form-card">
        <label><span>{t('workspace.name')}</span><input value={workspaceName} onChange={(event) => { onWorkspaceName(event.target.value); setSaved(false) }} /></label>
        <button className="save-workspace" type="button" onClick={() => setSaved(true)}><Save size={22} /> {saved ? t('common.saved') : t('common.save')}</button>
      </section>
    </main>
  )
}

function PageHeading({
  title,
  subtitle,
  onBack,
  onClose,
  compactClose = false,
}: {
  title: string
  subtitle: string
  onBack?: () => void
  onClose?: () => void
  compactClose?: boolean
}) {
  return (
    <header className="page-heading">
      {onBack ? <button className="heading-button" type="button" onClick={onBack}><ArrowLeft size={28} /></button> : <span className="heading-spacer" />}
      <div><h1>{title}</h1><p>{subtitle}</p></div>
      {onClose ? <button className={`heading-button ${compactClose ? 'compact' : ''}`} type="button" onClick={onClose}><X size={compactClose ? 21 : 28} /></button> : <span className="heading-spacer" />}
    </header>
  )
}

export function BottomSheet({ children, onClose }: { children: ReactNode; onClose: () => void }) {
  return (
    <div className="sheet-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) onClose() }}>
      <section className="bottom-sheet" role="dialog" aria-modal="true">
        <span className="sheet-handle" />
        <button className="sheet-close" type="button" onClick={onClose}><X size={26} /></button>
        {children}
      </section>
    </div>
  )
}

function TaskFilterSheet({ tasks, members, currentUserId, value, search, onApply }: {
  tasks: Task[]
  members: WorkspaceMember[]
  currentUserId?: number
  value: TaskDashboardFilter
  search: string
  onApply: (value: TaskDashboardFilter) => void
}) {
  const { t } = useI18n()
  const [draft, setDraft] = useState<TaskDashboardFilter>(value)
  const views: Array<{ value: TaskView; icon: ReactNode }> = [
    { value: 'ACTIVE', icon: <Sparkles /> },
    { value: 'MINE', icon: <CircleUserRound /> },
    { value: 'CREATED', icon: <Share2 /> },
    { value: 'TODAY', icon: <CalendarClock /> },
    { value: 'OVERDUE', icon: <AlertTriangle /> },
    { value: 'NO_DEADLINE', icon: <CalendarClock /> },
    { value: 'UNASSIGNED', icon: <MoreHorizontal /> },
    { value: 'COMPLETED', icon: <Check /> },
    { value: 'ARCHIVE', icon: <Folder /> },
  ]
  const statuses: TaskStatus[] = ['NEW', 'IN_PROGRESS', 'BLOCKED', 'REVIEW', 'COMPLETED', 'CANCELLED']
  const priorities: TaskPriority[] = ['LOW', 'NORMAL', 'IMPORTANT', 'URGENT']
  const resultCount = tasks.filter((task) => matchesDashboardFilter(task, draft, currentUserId, search)).length
  const toggle = <T,>(items: T[], item: T) => items.includes(item) ? items.filter((value) => value !== item) : [...items, item]

  return (
    <div className="sheet-content task-filter-sheet">
      <h2>{t('filter.title')}</h2>
      <p className="sheet-intro">{t('filter.intro')}</p>
      <section className="filter-section">
        <h3>{t('filter.quickviews')}</h3>
        <p>{t('filter.quickviews.subtitle')}</p>
        <div className="quick-view-grid">
          {views.map((item) => {
            const count = tasks.filter((task) => matchesDashboardFilter(task, { ...defaultTaskFilter, view: item.value }, currentUserId, '')).length
            return (
              <button key={item.value} className={draft.view === item.value ? 'selected' : ''} type="button" onClick={() => setDraft((current) => ({ ...current, view: item.value }))}>
                {item.icon}<span>{t(`quickview.${item.value}`)}</span>{count > 0 && <small>{count}</small>}
              </button>
            )
          })}
        </div>
      </section>
      <section className="filter-section">
        <h3>{t('filter.status')}</h3><p>{t('filter.status.subtitle')}</p>
        <div className="filter-pills">
          {statuses.map((item) => <button key={item} className={draft.statuses.includes(item) ? 'selected' : ''} type="button" onClick={() => setDraft((current) => ({ ...current, statuses: toggle(current.statuses, item) }))}>{statusLabel(t, item)}</button>)}
        </div>
      </section>
      <section className="filter-section">
        <h3>{t('filter.assignee')}</h3><p>{t('filter.assignee.subtitle')}</p>
        {members.length === 0 ? <div className="filter-empty">{t('filter.assignee.empty')}</div> : <div className="filter-pills member-pills">
          {members.map((member) => {
            const name = workspaceMemberName(t, member)
            return <button key={member.id} className={draft.assigneeIds.includes(member.id) ? 'selected' : ''} type="button" onClick={() => setDraft((current) => ({ ...current, assigneeIds: toggle(current.assigneeIds, member.id) }))}><span>{initials(name)}</span>{name}</button>
          })}
        </div>}
      </section>
      <section className="filter-section">
        <h3>{t('filter.priority')}</h3><p>{t('filter.priority.subtitle')}</p>
        <div className="filter-pills">
          {priorities.map((item) => <button key={item} className={draft.priorities.includes(item) ? 'selected' : ''} type="button" onClick={() => setDraft((current) => ({ ...current, priorities: toggle(current.priorities, item) }))}>{priorityLabel(t, item)}</button>)}
        </div>
      </section>
      <div className="filter-actions">
        <button type="button" onClick={() => setDraft(defaultTaskFilter)}>{t('filter.clear')}</button>
        <button type="button" onClick={() => onApply(draft)}>{t('filter.viewResults', { count: resultCount })}</button>
      </div>
    </div>
  )
}

function TaskGroupingSheet({ selected, onSelect }: { selected: TaskGrouping; onSelect: (value: TaskGrouping) => void }) {
  const { t } = useI18n()
  return (
    <div className="sheet-content grouping-sheet">
      <h2>{t('nav.view')}</h2>
      <p className="sheet-intro">{t('grouping.subtitle')}</p>
      <ChoiceRow selected={selected === 'LIST'} icon={<ListFilter />} title={t('grouping.list.title')} subtitle={t('grouping.list.subtitle')} onClick={() => onSelect('LIST')} />
      <ChoiceRow selected={selected === 'STATUS'} icon={<Flag />} title={t('status.title')} subtitle={t('grouping.status.subtitle')} onClick={() => onSelect('STATUS')} />
      <ChoiceRow selected={selected === 'ASSIGNEE'} icon={<UsersRound />} title={t('grouping.assignee.title')} subtitle={t('grouping.assignee.subtitle')} onClick={() => onSelect('ASSIGNEE')} />
    </div>
  )
}

export function TaskPlaceSheet({
  groups,
  selected,
  selectedGroupId,
  onSelect,
  onAddGroup,
  groupPickerBusy,
}: {
  groups: LinkedGroup[]
  selected: TaskVisibility
  selectedGroupId: number | null
  onSelect: (value: TaskVisibility, groupId?: number) => void
  onAddGroup: () => void
  groupPickerBusy: boolean
}) {
  const { t } = useI18n()
  return (
    <div className="sheet-content">
      <h2>{t('taskPlace.title')}</h2>
      <ChoiceRow selected={selected === 'PERSONAL'} icon={<CircleUserRound />} title={t('place.PERSONAL')} subtitle={t('place.PERSONAL.subtitle')} onClick={() => onSelect('PERSONAL')} />
      <ChoiceRow selected={selected === 'ONE_TO_ONE'} icon={<UsersRound />} title={t('place.ONE_TO_ONE')} subtitle={t('place.ONE_TO_ONE.subtitle')} onClick={() => onSelect('ONE_TO_ONE')} />
      {groups.map((group) => (
        <ChoiceRow
          key={group.id}
          selected={selected === 'GROUP' && selectedGroupId === group.id}
          initials={initials(group.title)}
          title={group.title}
          subtitle={t('group.membersShort', { count: group.members })}
          onClick={() => onSelect('GROUP', group.id)}
        />
      ))}
      <GroupLinkRow onClick={onAddGroup} busy={groupPickerBusy} />
    </div>
  )
}

interface WorkspaceSheetProps {
  profileName: string
  profileInitials: string
  groups: LinkedGroup[]
  selected: TaskVisibility
  selectedGroupId: number | null
  onSelect: (value: TaskVisibility, groupId?: number) => void
  onSwitch: () => void
  onAddGroup: () => void
  groupPickerBusy: boolean
}

function WorkspaceSheet({ profileName, profileInitials, groups, selected, selectedGroupId, onSelect, onSwitch, onAddGroup, groupPickerBusy }: WorkspaceSheetProps) {
  const { t } = useI18n()
  return (
    <div className="sheet-content">
      <h2>{t('scope.title')}</h2>
      <ChoiceRow selected={selected === 'PERSONAL'} icon={<CircleUserRound />} title={t('place.PERSONAL')} subtitle={t('place.PERSONAL.subtitle')} badge={t('common.activeBadge')} onClick={() => onSelect('PERSONAL')} />
      <div className="workspace-choice-card">
        <div className="workspace-choice-title">
          <span>{profileInitials}</span>
          <div><strong>{profileName}</strong><small>{t('workspace.owner')} · {t('workspace.groupsCount', { count: groups.length })}</small></div>
          <button className="workspace-switch-button" type="button" onClick={onSwitch}>{t('scope.switch')} <ArrowRightLeft size={19} /></button>
        </div>
        <ChoiceRow selected={selected === 'WORKSPACE'} icon={<Layers3 />} title={t('place.WORKSPACE')} subtitle={t('place.WORKSPACE.subtitle')} badge={t('common.activeBadge')} onClick={() => onSelect('WORKSPACE')} />
        <ChoiceRow selected={selected === 'ONE_TO_ONE'} icon={<LockKeyhole />} title={t('place.ONE_TO_ONE')} subtitle={t('place.ONE_TO_ONE.subtitle')} onClick={() => onSelect('ONE_TO_ONE')} />
        {groups.length > 0 && <span className="choice-section-label">{t('groups.title')}</span>}
        {groups.map((group) => (
          <ChoiceRow
            key={group.id}
            selected={selected === 'GROUP' && selectedGroupId === group.id}
            initials={initials(group.title)}
            title={group.title}
            subtitle={t('group.membersShort', { count: group.members })}
            onClick={() => onSelect('GROUP', group.id)}
          />
        ))}
        <GroupLinkRow onClick={onAddGroup} busy={groupPickerBusy} />
      </div>
    </div>
  )
}

function ScopeSheet(props: WorkspaceSheetProps) {
  return <WorkspaceSheet {...props} />
}

export function GroupPickerSheet({
  groups,
  loading,
  linkingChatId,
  onLink,
  onNativePicker,
  nativeBusy,
}: {
  groups: AvailableTelegramGroup[]
  loading: boolean
  linkingChatId: number | null
  onLink: (chatId: number) => void
  onNativePicker: () => void
  nativeBusy: boolean
}) {
  const { t } = useI18n()
  return (
    <div className="sheet-content">
      <h2>{t('group.picker.title')}</h2>
      {loading && <p className="sheet-intro">{t('group.picker.loading')}</p>}
      {!loading && groups.length === 0 && (
        <p className="sheet-intro">
          {t('group.picker.empty')}
        </p>
      )}
      {groups.map((group) => (
        <ChoiceRow
          key={group.chatId}
          selected={false}
          initials={initials(group.title)}
          title={group.title}
          subtitle={linkingChatId === group.chatId ? t('group.picker.linking') : t('group.picker.select')}
          onClick={() => onLink(group.chatId)}
        />
      ))}
      <button className="group-link-row" type="button" onClick={onNativePicker} disabled={nativeBusy}>
        <span><Plus size={26} /></span>
        <span><strong>{nativeBusy ? t('group.picker.opening') : t('group.picker.native')}</strong><small>{t('group.picker.native.subtitle')}</small></span>
        <ChevronRight size={22} />
      </button>
    </div>
  )
}

export function TopicPickerSheet({
  topics,
  loading,
  selectedTopicId,
  onSelect,
}: {
  topics: TelegramTopic[]
  loading: boolean
  selectedTopicId: number | null
  onSelect: (topicId?: number) => void
}) {
  const { t } = useI18n()
  return (
    <div className="sheet-content">
      <h2>{t('topic.picker.title')}</h2>
      {loading && <p className="sheet-intro">{t('topic.picker.loading')}</p>}
      <ChoiceRow
        selected={selectedTopicId == null}
        icon={<Layers3 />}
        title={t('topic.picker.general')}
        subtitle={t('topic.picker.select')}
        onClick={() => onSelect(undefined)}
      />
      {!loading && topics.length === 0 && (
        <p className="topic-picker-empty">{t('topic.picker.empty')}</p>
      )}
      {topics.map((topic) => (
        <ChoiceRow
          key={topic.id}
          selected={selectedTopicId === topic.id}
          initials={initials(topic.name)}
          title={topic.name}
          subtitle={t('topic.picker.select')}
          onClick={() => onSelect(topic.id)}
        />
      ))}
    </div>
  )
}

export function AuthorSheet({
  members,
  selected,
  currentUserId,
  onSelect,
}: {
  members: WorkspaceMember[]
  selected: number | null
  currentUserId?: number
  onSelect: (userId: number) => void
}) {
  const { t } = useI18n()
  const available = members.filter((member) => member.active && !member.temporarilyBlocked)
  return (
    <div className="sheet-content">
      <h2>{t('author.picker.title')}</h2>
      {available.length === 0 && <p className="sheet-intro">{t('author.picker.empty')}</p>}
      {available.map((member) => {
        const name = workspaceMemberName(t, member)
        return (
          <ChoiceRow
            key={member.id}
            selected={selected === member.id}
            initials={initials(name)}
            title={member.id === currentUserId ? `${name} (${t('common.you')})` : name}
            subtitle={member.username ? `@${member.username}` : t('author.picker.member')}
            onClick={() => onSelect(member.id)}
          />
        )
      })}
    </div>
  )
}

function ActionsSheet({
  taskTitle,
  onDuplicate,
  onShare,
  onDiscussion,
  canArchive,
  onArchive,
}: {
  taskTitle: string
  onDuplicate: () => void
  onShare: () => void
  onDiscussion: () => void
  canArchive?: boolean
  onArchive?: () => void
}) {
  const { t } = useI18n()
  return (
    <div className="sheet-content">
      <h2>{t('edit.actions.title')}</h2>
      <p className="sheet-intro">{taskTitle}</p>
      <SettingsRow tone="blue" icon={<Copy size={20} />} title={t('edit.actions.copy')} subtitle="" arrow={false} onClick={onDuplicate} />
      <SettingsRow tone="cyan" icon={<Share2 size={20} />} title={t('edit.actions.sendLink')} subtitle="" onClick={onShare} />
      <SettingsRow tone="green" icon={<MessageSquare size={20} />} title={t('edit.actions.discussion')} subtitle="" arrow={false} onClick={onDiscussion} />
      {canArchive && onArchive && (
        <SettingsRow tone="red" icon={<Archive size={20} />} title={t('edit.actions.archive')} subtitle="" arrow={false} onClick={onArchive} />
      )}
    </div>
  )
}

function ShareSheet({ onSendTelegram, onCopy }: { onSendTelegram: () => void; onCopy: () => void }) {
  const { t } = useI18n()
  return (
    <div className="sheet-content">
      <h2>{t('edit.share.title')}</h2>
      <p className="sheet-intro">{t('edit.share.subtitle')}</p>
      <div className="share-sheet-actions">
        <button className="share-primary" type="button" onClick={onSendTelegram}>
          <Send size={20} /> {t('edit.share.viaTelegram')}
        </button>
        <button className="share-secondary" type="button" onClick={onCopy}>
          <Copy size={20} /> {t('edit.share.copyMessage')}
        </button>
      </div>
    </div>
  )
}

function GroupLinkRow({ onClick, busy }: { onClick: () => void; busy: boolean }) {
  const { t } = useI18n()
  return (
    <button className="group-link-row" type="button" onClick={onClick} disabled={busy}>
      <span><Plus size={26} /></span>
      <span><strong>{busy ? t('group.link.opening') : t('group.link.title')}</strong><small>{t('group.link.subtitle')}</small></span>
      <ChevronRight size={22} />
    </button>
  )
}

export function ChoiceRow({
  selected,
  icon,
  initials,
  title,
  subtitle,
  badge,
  onClick,
}: {
  selected: boolean
  icon?: ReactNode
  initials?: string
  title: string
  subtitle: string
  badge?: string
  onClick: () => void
}) {
  return (
    <button className={`choice-row ${selected ? 'selected' : ''}`} type="button" onClick={onClick}>
      <span className={`choice-icon ${initials ? 'initials' : ''}`}>{initials ?? icon}</span>
      <span className="choice-copy"><strong>{title}</strong><small>{subtitle}</small></span>
      {badge && <span className="choice-badge">{badge}</span>}
      <span className="choice-radio">{selected && <Check size={22} />}</span>
    </button>
  )
}

function normalizeTask(task: Partial<Task> & { id: number; title: string }): Task {
  return {
    id: task.id,
    groupId: task.groupId,
    topicId: task.topicId,
    topicName: task.topicName,
    title: task.title,
    description: task.description,
    status: task.status ?? 'NEW',
    priority: task.priority ?? 'NORMAL',
    dueAt: task.dueAt,
    visibility: task.visibility ?? 'WORKSPACE',
    checklist: task.checklist,
    files: task.files,
    groupName: task.groupName,
    assigneeIds: task.assigneeIds ?? [],
    authorId: task.authorId,
    author: task.author,
    createdAt: task.createdAt,
    updatedAt: task.updatedAt,
    assignees: task.assignees ?? [],
    checklistItems: task.checklistItems ?? [],
    attachments: task.attachments ?? [],
    reminderMinutes: task.reminderMinutes,
    archivedAt: task.archivedAt,
  }
}

export function taskAssigneeIds(task: Task) {
  return task.assigneeIds?.length ? task.assigneeIds : (task.assignees ?? []).map((person) => person.id)
}

export function matchesTaskFilterExtras(task: Task, filter: TaskDashboardFilter, search = '') {
  const assignees = taskAssigneeIds(task)
  if (filter.statuses.length && !filter.statuses.includes(task.status)) return false
  if (filter.assigneeIds.length && !filter.assigneeIds.some((id) => assignees.includes(id))) return false
  if (filter.priorities.length && !filter.priorities.includes(task.priority)) return false
  const query = search.trim().toLocaleLowerCase('uz')
  return !query || `${task.title} ${task.description ?? ''} ${task.groupName ?? ''} ${task.author ?? ''}`.toLocaleLowerCase('uz').includes(query)
}

export function matchesDashboardFilter(
  task: Task,
  filter: TaskDashboardFilter,
  currentUserId?: number,
  search = '',
  includeClosed = false,
) {
  const assignees = taskAssigneeIds(task)
  const due = task.dueAt ? new Date(task.dueAt) : null
  const now = new Date()
  const today = due && due.getFullYear() === now.getFullYear() && due.getMonth() === now.getMonth() && due.getDate() === now.getDate()
  const closed = task.status === 'COMPLETED' || task.status === 'CANCELLED'
  const matchesView = (() => {
    switch (filter.view) {
      case 'ACTIVE': return includeClosed || !closed
      case 'MINE': return currentUserId !== undefined && assignees.includes(currentUserId)
      case 'CREATED': return currentUserId !== undefined && task.authorId === currentUserId
      case 'TODAY': return Boolean(today) && (includeClosed || !closed)
      case 'OVERDUE': return Boolean(due && due < now) && (includeClosed || !closed)
      case 'NO_DEADLINE': return !due
      case 'UNASSIGNED': return assignees.length === 0
      case 'COMPLETED': return task.status === 'COMPLETED'
      case 'ARCHIVE': return task.status === 'CANCELLED'
    }
  })()
  if (!matchesView) return false
  return matchesTaskFilterExtras(task, filter, search)
}

export function workspaceMemberName(t: Translator, member: WorkspaceMember) {
  return [member.firstName, member.lastName].filter(Boolean).join(' ').trim() || (member.username ? `@${member.username}` : t('common.user'))
}

function groupDashboardTasks(t: Translator, tasks: Task[], grouping: TaskGrouping, members: WorkspaceMember[]) {
  if (grouping === 'LIST') return [{ key: 'list', label: t('tasks.list.heading'), tasks }]
  if (grouping === 'STATUS') {
    const order: TaskStatus[] = ['NEW', 'IN_PROGRESS', 'BLOCKED', 'REVIEW', 'COMPLETED', 'CANCELLED']
    return order.map((status) => ({ key: status, label: statusLabel(t, status), tasks: tasks.filter((task) => task.status === status) })).filter((group) => group.tasks.length)
  }
  const memberNames = new Map(members.map((member) => [member.id, workspaceMemberName(t, member)]))
  const buckets = new Map<string, { key: string; label: string; tasks: Task[] }>()
  tasks.forEach((task) => {
    const assignee = task.assignees?.[0]
    const assigneeId = taskAssigneeIds(task)[0]
    const key = assigneeId ? String(assigneeId) : 'unassigned'
    const label = assignee?.name ?? (assigneeId ? memberNames.get(assigneeId) : undefined) ?? t('grouping.unassigned')
    const bucket = buckets.get(key) ?? { key, label, tasks: [] }
    bucket.tasks.push(task)
    buckets.set(key, bucket)
  })
  return Array.from(buckets.values())
}

function formatFileSize(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${Math.round(value / 1024)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}

export function initials(value: string) {
  const cleaned = value.replace(/^@/, '').trim()
  const parts = cleaned.split(/\s+/).filter(Boolean)
  if (!parts.length) return 'U'
  return parts.slice(0, 2).map((part) => part[0]).join('').toUpperCase()
}

function nextValue<T>(current: T, values: readonly T[]): T {
  const index = values.indexOf(current)
  return values[(index + 1) % values.length]
}

export function formatDeadline(value: string) {
  const date = new Date(value)
  return new Intl.DateTimeFormat('uz-UZ', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(date)
}

export function dueUrgency(dueAt: string, status: TaskStatus): 'red' | 'yellow' | 'normal' {
  if (status === 'COMPLETED' || status === 'CANCELLED') return 'normal'
  const daysLeft = (new Date(dueAt).getTime() - Date.now()) / 86_400_000
  if (daysLeft <= 1) return 'red'
  if (daysLeft <= 3) return 'yellow'
  return 'normal'
}

export function pad(value: number) {
  return String(value).padStart(2, '0')
}

export function nextAvailableHour() {
  const date = new Date()
  date.setSeconds(0, 0)
  date.setMinutes(0)
  date.setHours(date.getHours() + 1)
  return date
}

export function deadlinePreset(type: 'today' | 'tomorrow') {
  const date = new Date()
  if (type === 'tomorrow') date.setDate(date.getDate() + 1)
  date.setHours(type === 'today' ? 18 : 10, 0, 0, 0)
  if (type === 'today' && date <= new Date()) return nextAvailableHour()
  return date
}

const monthNames: Record<Lang, string[]> = {
  uz: ['Yanvar', 'Fevral', 'Mart', 'Aprel', 'May', 'Iyun', 'Iyul', 'Avgust', 'Sentabr', 'Oktabr', 'Noyabr', 'Dekabr'],
  ru: ['Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь', 'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь'],
  en: ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'],
}

function monthName(date: Date, lang: Lang = 'uz') {
  return `${monthNames[lang][date.getMonth()]}, ${date.getFullYear()}`
}

function calendarDays(month: Date) {
  const year = month.getFullYear()
  const monthIndex = month.getMonth()
  const firstDay = new Date(year, monthIndex, 1).getDay()
  const mondayOffset = firstDay === 0 ? 6 : firstDay - 1
  const daysInMonth = new Date(year, monthIndex + 1, 0).getDate()
  const daysInPrevious = new Date(year, monthIndex, 0).getDate()
  return Array.from({ length: 42 }, (_, index) => {
    const raw = index - mondayOffset + 1
    if (raw < 1) return { date: daysInPrevious + raw, current: false }
    if (raw > daysInMonth) return { date: raw - daysInMonth, current: false }
    return { date: raw, current: true }
  })
}

function detectLang(): Lang {
  const code = (navigator.language || 'uz').slice(0, 2).toLowerCase()
  return code === 'ru' ? 'ru' : code === 'en' ? 'en' : 'uz'
}

interface TelegramLoginPayload {
  id: number
  first_name: string
  last_name?: string
  username?: string
  photo_url?: string
  auth_date: number
  hash: string
}

declare global {
  interface Window {
    onTelegramAuth?: (user: TelegramLoginPayload) => void
  }
}

function LoginScreen() {
  const lang = useMemo(detectLang, [])
  const t = (key: string) => translate(lang, key)
  const [botUsername, setBotUsername] = useState<string | null>(null)
  const [error, setError] = useState('')
  const widgetHostRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    fetch('/api/auth/config')
      .then((response) => response.json() as Promise<{ botUsername?: string }>)
      .then((data) => setBotUsername(data.botUsername && data.botUsername.trim() ? data.botUsername.trim() : null))
      .catch(() => setError(t('login.error.config')))
  }, [])

  useEffect(() => {
    if (!botUsername || !widgetHostRef.current) return
    widgetHostRef.current.innerHTML = ''
    window.onTelegramAuth = (user) => {
      const payload = {
        id: String(user.id),
        firstName: user.first_name,
        lastName: user.last_name,
        username: user.username,
        photoUrl: user.photo_url,
        authDate: String(user.auth_date),
        hash: user.hash,
      }
      fetch('/api/auth/telegram-login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
        .then(async (response) => {
          if (!response.ok) {
            const body = await response.json().catch(() => null) as { message?: string } | null
            throw new Error(body?.message || t('login.error.generic'))
          }
          return response.json() as Promise<AuthResponse>
        })
        .then((session) => {
          localStorage.setItem(BROWSER_SESSION_KEY, JSON.stringify(session))
          window.location.hash = '#tasks'
          window.location.reload()
        })
        .catch((reason) => setError(reason instanceof Error ? reason.message : t('login.error.generic')))
    }
    const script = document.createElement('script')
    script.src = 'https://telegram.org/js/telegram-widget.js?22'
    script.async = true
    script.setAttribute('data-telegram-login', botUsername)
    script.setAttribute('data-size', 'large')
    script.setAttribute('data-radius', '12')
    script.setAttribute('data-onauth', 'onTelegramAuth(user)')
    script.setAttribute('data-request-access', 'write')
    widgetHostRef.current.appendChild(script)
  }, [botUsername])

  return (
    <div className="login-screen">
      <div className="login-card">
        <div className="login-mark">TA</div>
        <h1>{t('login.title')}</h1>
        <p>{t('login.subtitle')}</p>
        <div className="login-widget-host" ref={widgetHostRef} />
        {!botUsername && !error && <p className="login-status">{t('login.loading')}</p>}
        {error && <p className="login-error">{error}</p>}
      </div>
    </div>
  )
}

function Root() {
  const isLogin = typeof window !== 'undefined' && window.location.hash.startsWith('#login')
  return isLogin ? <LoginScreen /> : <App />
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Root />
  </StrictMode>,
)
