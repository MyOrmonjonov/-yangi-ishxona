import { useEffect, useMemo, useRef, useState } from 'react'
import {
  AlertTriangle,
  Archive,
  ArrowRightLeft,
  Bell,
  CalendarClock,
  Check,
  ChevronRight,
  Columns3,
  Copy,
  Flag,
  Folder,
  HelpCircle,
  LayoutList,
  MessageSquare,
  Moon,
  MoreHorizontal,
  Plus,
  Save,
  Search,
  Send,
  Share2,
  ShieldCheck,
  Sparkles,
  SlidersHorizontal,
  UsersRound,
  X,
} from 'lucide-react'
import { useI18n } from './i18n'
import type { Lang } from './i18n'
import {
  ArchiveScreen,
  AttachmentPicker,
  AuthorSheet,
  BottomSheet,
  ChoiceRow,
  GroupPickerSheet,
  HelpScreen,
  MembersScreen,
  PrioritySheet,
  SettingsGroup,
  SettingsRow,
  StatusSheet,
  TaskAttachmentPreview,
  TaskPlaceSheet,
  TaskRulesScreen,
  TopicPickerSheet,
  attachmentItems,
  deadlinePreset,
  defaultTaskFilter,
  dueUrgency,
  formatDeadline,
  initials,
  matchesDashboardFilter,
  nextAvailableHour,
  pad,
  placeLabel,
  priorityLabel,
  reminderLabel,
  showTelegramConfirm,
  showTelegramMessage,
  statusLabel,
  taskAssigneeIds,
  workspaceMemberName,
} from './main'
import type {
  AttachmentItem,
  AuthResponse,
  AvailableTelegramGroup,
  LinkedGroup,
  Task,
  TaskDashboardFilter,
  TaskPriority,
  TaskStatus,
  TaskView,
  TaskVisibility,
  TelegramTopic,
  WorkspaceMember,
} from './main'

type Theme = 'system' | 'dark' | 'light'
type DesktopTab = 'tasks' | 'groups' | 'statistics' | 'settings'
type DesktopTaskView = 'BOARD' | 'LIST' | 'ASSIGNEE'
type DesktopDetailSheet = 'place' | 'topic' | 'author' | 'priority' | 'status' | 'deadline' | null
type DesktopSettingsTab = 'general' | 'help' | 'members' | 'rules' | 'archive'

const STATUS_COLUMNS: TaskStatus[] = ['NEW', 'IN_PROGRESS', 'BLOCKED', 'REVIEW', 'COMPLETED', 'CANCELLED']

export interface DesktopShellProps {
  auth: AuthResponse
  profileName: string
  userInitials: string
  workspaceName: string
  tasks: Task[]
  groups: LinkedGroup[]
  workspaceMembers: WorkspaceMember[]
  activeWorkspaceId: number | null
  workspaceSwitching: boolean
  newWorkspaceName: string
  creatingWorkspace: boolean
  onNewWorkspaceNameChange: (value: string) => void
  onSwitchWorkspace: (workspaceId: number) => void
  onCreateWorkspace: () => void
  theme: Theme
  remindersEnabled: boolean
  onSavePreferences: (patch: { uiLanguage?: Lang; theme?: Theme; remindersEnabled?: boolean }) => void
  onChangeStatus: (taskId: number, status: TaskStatus) => void
  onSaveTask: (task: Task, changes: Partial<Task> & { authorId?: number }, newAttachments?: File[]) => Promise<boolean>
  onCreateTask: (input: {
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
  }) => Promise<Task | undefined>
  onRemoveAttachment: (taskId: number, fileId: number) => void
  onDuplicateTask: (task: Task) => void
  onShareTelegram: (task: Task) => void
  onCopyMessage: (task: Task) => void
  onOpenDiscussion: (task: Task) => void
  availableGroups: AvailableTelegramGroup[]
  availableGroupsLoading: boolean
  linkingChatId: number | null
  onLoadAvailableGroups: () => void
  groupTopics: TelegramTopic[]
  topicsLoading: boolean
  onLoadGroupTopics: (groupId: number) => void
  onLinkGroup: (chatId: number) => void
  groupPickerBusy: boolean
  onOpenNativeGroupPicker: () => void
  onInviteMembers: (group: LinkedGroup) => void
  invitingMembers: boolean
  onAddBotToGroup: (group: LinkedGroup) => void
  onChangeGroupTaskPolicy: (groupId: number, policy: string) => void
  archivedTasks: Task[]
  archivedLoading: boolean
  onLoadArchived: () => void
  onArchiveTask: (taskId: number) => void
  onRestoreTask: (taskId: number) => void
  onDeleteTaskForever: (taskId: number) => void
}

export function DesktopShell(props: DesktopShellProps) {
  const { t } = useI18n()
  const [tab, setTab] = useState<DesktopTab>('tasks')
  const [workspaceMenuOpen, setWorkspaceMenuOpen] = useState(false)
  const [taskNavRequest, setTaskNavRequest] = useState<{ view: TaskView; token: number } | null>(null)

  function navigateFromStatistics(target: QuickStatKey) {
    if (target === 'GROUPS') {
      setTab('groups')
      return
    }
    setTab('tasks')
    setTaskNavRequest({ view: target, token: Date.now() })
  }

  return (
    <div className="desktop-shell">
      <header className="desktop-topbar">
        <div className="desktop-workspace-switch">
          <button type="button" className="desktop-workspace-button" onClick={() => setWorkspaceMenuOpen((value) => !value)}>
            <span className="desktop-workspace-avatar">{props.userInitials}</span>
            <span className="desktop-workspace-copy">
              <strong>{props.workspaceName}</strong>
              <small>{t('nav.tasksCount', { count: props.tasks.length })}</small>
            </span>
            <ArrowRightLeft size={18} />
          </button>
          {workspaceMenuOpen && (
            <div className="desktop-workspace-menu">
              {props.auth.workspaces.map((workspace) => (
                <button
                  key={workspace.id}
                  type="button"
                  className={workspace.id === props.activeWorkspaceId ? 'selected' : ''}
                  onClick={() => { props.onSwitchWorkspace(workspace.id); setWorkspaceMenuOpen(false) }}
                >
                  {workspace.name}
                  {workspace.id === props.activeWorkspaceId && <Check size={16} />}
                </button>
              ))}
              <div className="desktop-workspace-add">
                <input
                  value={props.newWorkspaceName}
                  onChange={(event) => props.onNewWorkspaceNameChange(event.target.value)}
                  placeholder={t('workspace.switch.addPlaceholder')}
                />
                <button type="button" disabled={props.creatingWorkspace || !props.newWorkspaceName.trim()} onClick={props.onCreateWorkspace}>
                  <Plus size={16} /> {props.creatingWorkspace ? t('workspace.switch.creating') : t('workspace.switch.add')}
                </button>
              </div>
            </div>
          )}
        </div>
        <nav className="desktop-tabs">
          <button type="button" className={tab === 'tasks' ? 'active' : ''} onClick={() => setTab('tasks')}>{t('tasks.list.heading')}</button>
          <button type="button" className={tab === 'groups' ? 'active' : ''} onClick={() => setTab('groups')}>{t('groups.title')}</button>
          <button type="button" className={tab === 'statistics' ? 'active' : ''} onClick={() => setTab('statistics')}>{t('desktop.statistics.title')}</button>
          <button type="button" className={tab === 'settings' ? 'active' : ''} onClick={() => setTab('settings')}>{t('settings.title')}</button>
        </nav>
        <div className="desktop-topbar-spacer" />
      </header>

      <main className="desktop-body">
        {tab === 'tasks' && (
          <DesktopTasksView
            auth={props.auth}
            tasks={props.tasks}
            groups={props.groups}
            workspaceMembers={props.workspaceMembers}
            currentUserId={props.auth.user.id}
            activeWorkspaceId={props.activeWorkspaceId}
            onChangeStatus={props.onChangeStatus}
            onSaveTask={props.onSaveTask}
            onCreateTask={props.onCreateTask}
            onRemoveAttachment={props.onRemoveAttachment}
            onDuplicateTask={props.onDuplicateTask}
            onShareTelegram={props.onShareTelegram}
            onCopyMessage={props.onCopyMessage}
            onOpenDiscussion={props.onOpenDiscussion}
            availableGroups={props.availableGroups}
            availableGroupsLoading={props.availableGroupsLoading}
            linkingChatId={props.linkingChatId}
            onLoadAvailableGroups={props.onLoadAvailableGroups}
            groupTopics={props.groupTopics}
            topicsLoading={props.topicsLoading}
            onLoadGroupTopics={props.onLoadGroupTopics}
            onLinkGroup={props.onLinkGroup}
            groupPickerBusy={props.groupPickerBusy}
            onOpenNativeGroupPicker={props.onOpenNativeGroupPicker}
            onAddBotToGroup={props.onAddBotToGroup}
            navRequest={taskNavRequest}
            onArchiveTask={props.onArchiveTask}
          />
        )}
        {tab === 'groups' && (
          <DesktopGroupsView
            groups={props.groups}
            tasks={props.tasks}
            availableGroups={props.availableGroups}
            availableGroupsLoading={props.availableGroupsLoading}
            linkingChatId={props.linkingChatId}
            onLoadAvailableGroups={props.onLoadAvailableGroups}
            onLinkGroup={props.onLinkGroup}
            groupPickerBusy={props.groupPickerBusy}
            onOpenNativeGroupPicker={props.onOpenNativeGroupPicker}
            onInviteMembers={props.onInviteMembers}
            invitingMembers={props.invitingMembers}
            onAddBotToGroup={props.onAddBotToGroup}
          />
        )}
        {tab === 'statistics' && (
          <DesktopStatisticsView activeWorkspaceId={props.activeWorkspaceId} auth={props.auth} onNavigate={navigateFromStatistics} />
        )}
        {tab === 'settings' && (
          <DesktopSettingsView
            theme={props.theme}
            remindersEnabled={props.remindersEnabled}
            onSavePreferences={props.onSavePreferences}
            workspaceMembers={props.workspaceMembers}
            currentUserId={props.auth.user.id}
            groups={props.groups}
            onChangeGroupTaskPolicy={props.onChangeGroupTaskPolicy}
            archivedTasks={props.archivedTasks}
            archivedLoading={props.archivedLoading}
            onLoadArchived={props.onLoadArchived}
            onRestoreTask={props.onRestoreTask}
            onDeleteTaskForever={props.onDeleteTaskForever}
          />
        )}
      </main>
    </div>
  )
}

function DesktopTasksView({
  auth,
  tasks,
  groups,
  workspaceMembers,
  currentUserId,
  activeWorkspaceId,
  onChangeStatus,
  onSaveTask,
  onCreateTask,
  onRemoveAttachment,
  onDuplicateTask,
  onShareTelegram,
  onCopyMessage,
  onOpenDiscussion,
  availableGroups,
  availableGroupsLoading,
  linkingChatId,
  onLoadAvailableGroups,
  groupTopics,
  topicsLoading,
  onLoadGroupTopics,
  onLinkGroup,
  groupPickerBusy,
  onOpenNativeGroupPicker,
  onAddBotToGroup,
  navRequest,
  onArchiveTask,
}: {
  auth: AuthResponse
  tasks: Task[]
  groups: LinkedGroup[]
  workspaceMembers: WorkspaceMember[]
  currentUserId?: number
  activeWorkspaceId: number | null
  onChangeStatus: (taskId: number, status: TaskStatus) => void
  onSaveTask: DesktopShellProps['onSaveTask']
  onCreateTask: DesktopShellProps['onCreateTask']
  onAddBotToGroup: (group: LinkedGroup) => void
  onRemoveAttachment: DesktopShellProps['onRemoveAttachment']
  onDuplicateTask: DesktopShellProps['onDuplicateTask']
  onShareTelegram: DesktopShellProps['onShareTelegram']
  onCopyMessage: DesktopShellProps['onCopyMessage']
  onOpenDiscussion: DesktopShellProps['onOpenDiscussion']
  availableGroups: AvailableTelegramGroup[]
  availableGroupsLoading: boolean
  linkingChatId: number | null
  onLoadAvailableGroups: () => void
  groupTopics: TelegramTopic[]
  topicsLoading: boolean
  onLoadGroupTopics: (groupId: number) => void
  onLinkGroup: (chatId: number) => void
  groupPickerBusy: boolean
  onOpenNativeGroupPicker: () => void
  navRequest: { view: TaskView; token: number } | null
  onArchiveTask: (taskId: number) => void
}) {
  const { t } = useI18n()
  const [view, setView] = useState<DesktopTaskView>('BOARD')
  const [filter, setFilter] = useState<TaskDashboardFilter>(defaultTaskFilter)
  const [search, setSearch] = useState('')
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const [createDefaults, setCreateDefaults] = useState<TaskStatus>('NEW')
  const detailDirtyRef = useRef(false)

  function requestSelectTask(id: number | null) {
    if (detailDirtyRef.current && !window.confirm(t('edit.discardChangesConfirm'))) return
    detailDirtyRef.current = false
    setSelectedTaskId(id)
  }

  useEffect(() => {
    if (!navRequest) return
    setFilter((current) => ({ ...current, view: navRequest.view }))
    setSearch('')
  }, [navRequest?.token])

  const filtered = useMemo(
    () => tasks.filter((task) => matchesDashboardFilter(task, filter, currentUserId, search)),
    [tasks, filter, currentUserId, search],
  )
  const boardTasks = useMemo(
    () => tasks.filter((task) => matchesDashboardFilter(task, filter, currentUserId, search, true)),
    [tasks, filter, currentUserId, search],
  )
  const selectedTask = tasks.find((task) => task.id === selectedTaskId)

  const quickViews: Array<{ value: typeof filter.view; icon: React.ReactNode }> = [
    { value: 'ACTIVE', icon: <Sparkles size={14} /> },
    { value: 'MINE', icon: <UsersRound size={14} /> },
    { value: 'CREATED', icon: <Share2 size={14} /> },
    { value: 'TODAY', icon: <CalendarClock size={14} /> },
    { value: 'OVERDUE', icon: <AlertTriangle size={14} /> },
    { value: 'NO_DEADLINE', icon: <CalendarClock size={14} /> },
    { value: 'UNASSIGNED', icon: <MoreHorizontal size={14} /> },
    { value: 'COMPLETED', icon: <Check size={14} /> },
    { value: 'ARCHIVE', icon: <Folder size={14} /> },
  ]

  return (
    <div className="desktop-tasks-view">
      <div className="desktop-toolbar">
        <div className="desktop-quick-views">
          {quickViews.map((item) => (
            <button
              key={item.value}
              type="button"
              className={filter.view === item.value ? 'selected' : ''}
              onClick={() => setFilter((current) => ({ ...current, view: item.value }))}
            >
              {item.icon} {t(`quickview.${item.value}`)}
            </button>
          ))}
        </div>
        <label className="desktop-search">
          <Search size={16} />
          <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder={t('nav.search')} />
        </label>
        <div className="desktop-view-toggle">
          <button type="button" className={view === 'BOARD' ? 'active' : ''} onClick={() => setView('BOARD')}><Columns3 size={16} /> {t('desktop.view.board')}</button>
          <button type="button" className={view === 'LIST' ? 'active' : ''} onClick={() => setView('LIST')}><LayoutList size={16} /> {t('desktop.view.list')}</button>
          <button type="button" className={view === 'ASSIGNEE' ? 'active' : ''} onClick={() => setView('ASSIGNEE')}><UsersRound size={16} /> {t('desktop.view.assignee')}</button>
        </div>
        <button type="button" className="desktop-add-task" onClick={() => { setCreateDefaults('NEW'); setCreating(true) }}>
          <Plus size={18} />
        </button>
      </div>

      {view === 'BOARD' ? (
        <KanbanBoard
          tasks={boardTasks}
          workspaceMembers={workspaceMembers}
          onChangeStatus={onChangeStatus}
          onSelectTask={requestSelectTask}
          onAddInColumn={(status) => { setCreateDefaults(status); setCreating(true) }}
        />
      ) : (
        <div className="desktop-split">
          <div className="desktop-list-pane">
            {view === 'LIST' ? (
              <DesktopTaskList tasks={filtered} selectedTaskId={selectedTaskId} currentUserId={currentUserId} onSelect={requestSelectTask} />
            ) : (
              <DesktopAssigneeList
                tasks={filtered}
                members={workspaceMembers}
                selectedTaskId={selectedTaskId}
                currentUserId={currentUserId}
                onSelect={requestSelectTask}
              />
            )}
          </div>
          <div className="desktop-detail-pane">
            {selectedTask ? (
              <DesktopTaskDetail
                auth={auth}
                task={selectedTask}
                groups={groups}
                workspaceMembers={workspaceMembers}
                currentUserId={currentUserId}
                onSave={(changes, files) => onSaveTask(selectedTask, changes, files)}
                onRemoveAttachment={(fileId) => onRemoveAttachment(selectedTask.id, fileId)}
                onDuplicate={() => onDuplicateTask(selectedTask)}
                onShareTelegram={() => onShareTelegram(selectedTask)}
                onCopyMessage={() => onCopyMessage(selectedTask)}
                onOpenDiscussion={() => onOpenDiscussion(selectedTask)}
                availableGroups={availableGroups}
                availableGroupsLoading={availableGroupsLoading}
                linkingChatId={linkingChatId}
                onLoadAvailableGroups={onLoadAvailableGroups}
                groupTopics={groupTopics}
                topicsLoading={topicsLoading}
                onLoadGroupTopics={onLoadGroupTopics}
                onLinkGroup={onLinkGroup}
                groupPickerBusy={groupPickerBusy}
                onOpenNativeGroupPicker={onOpenNativeGroupPicker}
                onDirtyChange={(value) => { detailDirtyRef.current = value }}
                onArchive={() => { onArchiveTask(selectedTask.id); detailDirtyRef.current = false; setSelectedTaskId(null) }}
              />
            ) : (
              <DesktopEmptyDetail />
            )}
          </div>
        </div>
      )}

      {view === 'BOARD' && selectedTask && (
        <div className="desktop-modal-backdrop" onMouseDown={(event) => { if (event.currentTarget === event.target) requestSelectTask(null) }}>
          <div className="desktop-modal desktop-modal-wide">
            <div className="desktop-modal-heading">
              <h2>{selectedTask.title}</h2>
              <button type="button" onClick={() => requestSelectTask(null)}><X size={20} /></button>
            </div>
            <DesktopTaskDetail
              auth={auth}
              task={selectedTask}
              groups={groups}
              workspaceMembers={workspaceMembers}
              currentUserId={currentUserId}
              onSave={(changes, files) => onSaveTask(selectedTask, changes, files)}
              onRemoveAttachment={(fileId) => onRemoveAttachment(selectedTask.id, fileId)}
              onDuplicate={() => onDuplicateTask(selectedTask)}
              onShareTelegram={() => onShareTelegram(selectedTask)}
              onCopyMessage={() => onCopyMessage(selectedTask)}
              onOpenDiscussion={() => onOpenDiscussion(selectedTask)}
              availableGroups={availableGroups}
              availableGroupsLoading={availableGroupsLoading}
              linkingChatId={linkingChatId}
              onLoadAvailableGroups={onLoadAvailableGroups}
              groupTopics={groupTopics}
              topicsLoading={topicsLoading}
              onLoadGroupTopics={onLoadGroupTopics}
              onLinkGroup={onLinkGroup}
              groupPickerBusy={groupPickerBusy}
              onOpenNativeGroupPicker={onOpenNativeGroupPicker}
              onDirtyChange={(value) => { detailDirtyRef.current = value }}
              onSaved={() => { detailDirtyRef.current = false; setSelectedTaskId(null) }}
              onArchive={() => { onArchiveTask(selectedTask.id); detailDirtyRef.current = false; setSelectedTaskId(null) }}
            />
          </div>
        </div>
      )}

      {creating && (
        <NewTaskModal
          groups={groups}
          workspaceMembers={workspaceMembers}
          currentUserId={currentUserId}
          defaultStatus={createDefaults}
          availableGroups={availableGroups}
          availableGroupsLoading={availableGroupsLoading}
          linkingChatId={linkingChatId}
          onLoadAvailableGroups={onLoadAvailableGroups}
          groupTopics={groupTopics}
          topicsLoading={topicsLoading}
          onLoadGroupTopics={onLoadGroupTopics}
          onLinkGroup={onLinkGroup}
          groupPickerBusy={groupPickerBusy}
          onOpenNativeGroupPicker={onOpenNativeGroupPicker}
          onAddBotToGroup={onAddBotToGroup}
          onClose={() => setCreating(false)}
          onCreate={async (input) => onCreateTask(input)}
        />
      )}
    </div>
  )
}

function DesktopEmptyDetail() {
  const { t } = useI18n()
  return (
    <div className="desktop-empty-detail">
      <Folder size={32} />
      <strong>{t('desktop.selectTask.title')}</strong>
      <p>{t('desktop.selectTask.subtitle')}</p>
    </div>
  )
}

function DesktopTaskList({ tasks, selectedTaskId, currentUserId, onSelect }: {
  tasks: Task[]
  selectedTaskId: number | null
  currentUserId?: number
  onSelect: (id: number) => void
}) {
  const { t } = useI18n()
  if (tasks.length === 0) {
    return <div className="desktop-list-empty"><Search size={26} /><p>{t('tasks.filteredEmpty.title')}</p></div>
  }
  return (
    <div className="desktop-task-rows">
      {tasks.map((task) => (
        <DesktopTaskRow key={task.id} task={task} selected={task.id === selectedTaskId} currentUserId={currentUserId} onClick={() => onSelect(task.id)} />
      ))}
    </div>
  )
}

function DesktopAssigneeList({ tasks, members, selectedTaskId, currentUserId, onSelect }: {
  tasks: Task[]
  members: WorkspaceMember[]
  selectedTaskId: number | null
  currentUserId?: number
  onSelect: (id: number) => void
}) {
  const { t } = useI18n()
  const buckets = useMemo(() => {
    const memberNames = new Map(members.map((member) => [member.id, workspaceMemberName(t, member)]))
    const map = new Map<string, { label: string; tasks: Task[] }>()
    tasks.forEach((task) => {
      const assigneeId = taskAssigneeIds(task)[0]
      const key = assigneeId ? String(assigneeId) : 'unassigned'
      const label = task.assignees?.[0]?.name ?? (assigneeId ? memberNames.get(assigneeId) : undefined) ?? t('grouping.unassigned')
      const bucket = map.get(key) ?? { label, tasks: [] }
      bucket.tasks.push(task)
      map.set(key, bucket)
    })
    return Array.from(map.values())
  }, [tasks, members, t])

  if (tasks.length === 0) {
    return <div className="desktop-list-empty"><Search size={26} /><p>{t('tasks.filteredEmpty.title')}</p></div>
  }
  return (
    <div className="desktop-task-rows">
      {buckets.map((bucket) => (
        <div className="desktop-assignee-bucket" key={bucket.label}>
          <div className="desktop-assignee-bucket-heading">{bucket.label}<span>{bucket.tasks.length}</span></div>
          {bucket.tasks.map((task) => (
            <DesktopTaskRow key={task.id} task={task} selected={task.id === selectedTaskId} currentUserId={currentUserId} onClick={() => onSelect(task.id)} />
          ))}
        </div>
      ))}
    </div>
  )
}

function DesktopTaskRow({ task, selected, currentUserId, onClick }: { task: Task; selected: boolean; currentUserId?: number; onClick: () => void }) {
  const { t } = useI18n()
  const isOwnTask = task.authorId != null && task.authorId === currentUserId
  const isAssignedByOther = !isOwnTask && taskAssigneeIds(task).includes(currentUserId ?? -1)
  return (
    <button className={`desktop-task-row ${selected ? 'selected' : ''}`} type="button" onClick={onClick}>
      <span className={`priority-mark priority-${task.priority.toLowerCase()}`} />
      <span className="desktop-task-row-copy">
        <strong>{task.title}</strong>
        <small>{task.visibility === 'GROUP' ? task.groupName ?? t('place.GROUP') : placeLabel(t, task.visibility)}</small>
        {isOwnTask && <span className="task-owner-tag task-owner-tag-own">{t('task.tag.own')}</span>}
        {isAssignedByOther && (
          <span className="task-owner-tag task-owner-tag-assigned">{t('task.tag.assignedBy', { name: task.author ?? t('common.user') })}</span>
        )}
      </span>
      <span className={`desktop-status-pill status-${task.status.toLowerCase()}`}>{statusLabel(t, task.status)}</span>
      {task.dueAt && <span className={`desktop-task-row-due due-${dueUrgency(task.dueAt, task.status)}`}>{formatDeadline(task.dueAt)}</span>}
    </button>
  )
}

function DesktopTaskDetail({
  auth,
  task,
  groups,
  workspaceMembers,
  currentUserId,
  onSave,
  onRemoveAttachment,
  onDuplicate,
  onShareTelegram,
  onCopyMessage,
  onOpenDiscussion,
  availableGroups,
  availableGroupsLoading,
  linkingChatId,
  onLoadAvailableGroups,
  groupTopics,
  topicsLoading,
  onLoadGroupTopics,
  onLinkGroup,
  groupPickerBusy,
  onOpenNativeGroupPicker,
  onDirtyChange,
  onSaved,
  onArchive,
}: {
  auth: AuthResponse
  task: Task
  groups: LinkedGroup[]
  workspaceMembers: WorkspaceMember[]
  currentUserId?: number
  onSave: (changes: Partial<Task> & { authorId?: number }, newAttachments?: File[]) => Promise<boolean>
  onRemoveAttachment: (fileId: number) => void
  onDuplicate: () => void
  onShareTelegram: () => void
  onCopyMessage: () => void
  onOpenDiscussion: () => void
  availableGroups: AvailableTelegramGroup[]
  availableGroupsLoading: boolean
  linkingChatId: number | null
  onLoadAvailableGroups: () => void
  groupTopics: TelegramTopic[]
  topicsLoading: boolean
  onLoadGroupTopics: (groupId: number) => void
  onLinkGroup: (chatId: number) => void
  onDirtyChange?: (dirty: boolean) => void
  onSaved?: () => void
  onArchive?: () => void
  groupPickerBusy: boolean
  onOpenNativeGroupPicker: () => void
}) {
  const { t } = useI18n()
  const [draft, setDraft] = useState<Partial<Task> & { authorId?: number }>({})
  const [checklistDraft, setChecklistDraft] = useState('')
  const [activeSheet, setActiveSheet] = useState<DesktopDetailSheet>(null)
  const [groupPickerOpen, setGroupPickerOpen] = useState(false)
  const [newAttachments, setNewAttachments] = useState<AttachmentItem[]>([])
  const [removedAttachmentIds, setRemovedAttachmentIds] = useState<number[]>([])
  const [saving, setSaving] = useState(false)
  const galleryRef = useRef<HTMLInputElement | null>(null)
  const cameraRef = useRef<HTMLInputElement | null>(null)
  const fileRef = useRef<HTMLInputElement | null>(null)
  useEffect(() => {
    setDraft({})
    setNewAttachments([])
    setRemovedAttachmentIds([])
  }, [task.id])

  function update(change: Partial<Task> & { authorId?: number }) {
    setDraft((current) => ({ ...current, ...change }))
  }

  const current = { ...task, ...draft }
  const dirty = Object.keys(draft).length > 0 || newAttachments.length > 0 || removedAttachmentIds.length > 0
  const visibleAttachments = (task.attachments ?? []).filter((attachment) => !removedAttachmentIds.includes(attachment.id))

  useEffect(() => {
    onDirtyChange?.(dirty)
    return () => onDirtyChange?.(false)
  }, [dirty])

  const isAuthor = task.authorId != null && task.authorId === currentUserId
  const isOwner = workspaceMembers.find((member) => member.id === currentUserId)?.roleCode === 'OWNER'
  const group = current.visibility === 'GROUP' ? groups.find((item) => item.id === current.groupId) : undefined
  const currentTopicName = current.visibility === 'GROUP'
    ? groupTopics.find((topic) => topic.id === current.topicId)?.name ?? current.topicName
    : undefined
  const placeWithTopicLabel = `${group?.title ?? placeLabel(t, current.visibility)}${currentTopicName ? ` · ${currentTopicName}` : ''}`
  const assigneeIds = draft.assigneeIds ?? taskAssigneeIds(task)
  const assigneeCandidates: Array<{ id: number; name: string; username?: string; photoUrl?: string }> =
    current.visibility === 'GROUP'
      ? (group?.memberList ?? [])
      : workspaceMembers.filter((member) => member.active && !member.temporarilyBlocked)
          .map((member) => ({ id: member.id, name: workspaceMemberName(t, member), username: member.username, photoUrl: member.photoUrl }))
  const assigneeNames = assigneeIds.map((id) =>
    task.assignees?.find((person) => person.id === id)?.name
    ?? workspaceMembers.find((member) => member.id === id))
    .filter(Boolean)
    .map((entry) => typeof entry === 'string' ? entry : workspaceMemberName(t, entry as WorkspaceMember))
  const authorMember = workspaceMembers.find((member) => member.id === (draft.authorId ?? task.authorId))
  const authorName = authorMember ? workspaceMemberName(t, authorMember) : task.author ?? t('common.user')

  async function saveAll() {
    if (!dirty) return
    setSaving(true)
    let success = true
    if (Object.keys(draft).length > 0 || newAttachments.length > 0) {
      success = await onSave(draft, newAttachments.length ? newAttachments.map((item) => item.file) : undefined)
    }
    if (success) {
      removedAttachmentIds.forEach((fileId) => onRemoveAttachment(fileId))
      setDraft({})
      setNewAttachments([])
      setRemovedAttachmentIds([])
    }
    setSaving(false)
    if (success) onSaved?.()
  }

  return (
    <div className="desktop-task-detail">
      <div className="desktop-detail-actions">
        <button type="button" onClick={onDuplicate}><Copy size={15} /> {t('edit.actions.copy')}</button>
        <button type="button" onClick={onShareTelegram}><Send size={15} /> {t('edit.share.viaTelegram')}</button>
        <button type="button" onClick={onCopyMessage}><Share2 size={15} /> {t('edit.share.copyMessage')}</button>
        <button type="button" onClick={onOpenDiscussion}><MessageSquare size={15} /> {t('edit.actions.discussion')}</button>
        {(isAuthor || isOwner) && onArchive && (
          <button type="button" className="desktop-detail-actions-danger" onClick={onArchive}>
            <Archive size={15} /> {t('edit.actions.archive')}
          </button>
        )}
      </div>
      <input
        className="desktop-detail-title"
        value={current.title}
        onChange={(event) => update({ title: event.target.value })}
      />
      <div className="desktop-detail-status-row">
        {STATUS_COLUMNS.map((status) => (
          <button
            key={status}
            type="button"
            className={`desktop-status-chip status-${status.toLowerCase()} ${current.status === status ? 'active' : ''}`}
            onClick={() => update({ status })}
          >
            {statusLabel(t, status)}
          </button>
        ))}
      </div>
      <div className="desktop-detail-grid">
        {isAuthor ? (
          <button type="button" onClick={() => setActiveSheet('place')}>
            <span>{t('edit.field.place')}</span><strong>{placeWithTopicLabel}</strong>
          </button>
        ) : (
          <div><span>{t('edit.field.place')}</span><strong>{placeWithTopicLabel}</strong></div>
        )}
        {isAuthor ? (
          <button type="button" onClick={() => setActiveSheet('author')}>
            <span>{t('edit.field.author')}</span><strong>{authorName}</strong>
          </button>
        ) : (
          <div><span>{t('edit.field.author')}</span><strong>{authorName}</strong></div>
        )}
        <button type="button" onClick={() => setActiveSheet('deadline')}>
          <span>{t('edit.field.deadline')}</span><strong>{current.dueAt ? formatDeadline(current.dueAt) : t('deadline.none')}</strong>
        </button>
        <div><span>{t('edit.field.assignees')}</span><strong>{assigneeNames.length ? assigneeNames.join(', ') : t('edit.field.assignees.empty')}</strong></div>
      </div>
      <DesktopReminderFields
        dueAt={current.dueAt}
        reminderMinutes={current.reminderMinutes}
        onReminderChange={(value) => update({ reminderMinutes: value })}
      />
      {current.visibility !== 'PERSONAL' && (
        <div className="desktop-assignee-picker">
          <span className="desktop-field-label">{t('create.assignee.title')}</span>
          <div className="desktop-assignee-chips">
            {assigneeCandidates.map((member) => {
              const selected = assigneeIds.includes(member.id)
              return (
                <button
                  key={member.id}
                  type="button"
                  className={selected ? 'selected' : ''}
                  onClick={() => {
                    const next = selected ? assigneeIds.filter((id) => id !== member.id) : [...assigneeIds, member.id]
                    update({ assigneeIds: next })
                  }}
                >
                  {selected && <Check size={12} />} {initials(member.name)} {member.name}
                </button>
              )
            })}
          </div>
        </div>
      )}
      <div className="desktop-detail-priority-row">
        {(['LOW', 'NORMAL', 'IMPORTANT', 'URGENT'] as TaskPriority[]).map((priority) => (
          <button
            key={priority}
            type="button"
            className={current.priority === priority ? 'selected' : ''}
            onClick={() => update({ priority })}
          >
            {priorityLabel(t, priority)}
          </button>
        ))}
      </div>
      <label className="desktop-detail-description">
        <span>{t('edit.field.description')}</span>
        <textarea
          rows={6}
          value={current.description ?? ''}
          onChange={(event) => update({ description: event.target.value })}
          placeholder={t('edit.field.descriptionPlaceholder')}
        />
      </label>
      <div className="desktop-detail-checklist">
        <span className="desktop-field-label">{t('edit.field.checklist')}</span>
        {(current.checklistItems ?? []).map((item) => (
          <div className="desktop-checklist-row" key={item.id}>
            <input
              type="checkbox"
              checked={item.done}
              onChange={() => update({
                checklistItems: (current.checklistItems ?? []).map((entry) =>
                  entry.id === item.id ? { ...entry, done: !entry.done } : entry),
              })}
            />
            <input
              className={item.done ? 'done' : ''}
              value={item.text}
              onChange={(event) => update({
                checklistItems: (current.checklistItems ?? []).map((entry) =>
                  entry.id === item.id ? { ...entry, text: event.target.value } : entry),
              })}
            />
            <button
              type="button"
              onClick={() => update({ checklistItems: (current.checklistItems ?? []).filter((entry) => entry.id !== item.id) })}
            >
              <X size={14} />
            </button>
          </div>
        ))}
        <div className="desktop-checklist-entry">
          <input
            value={checklistDraft}
            placeholder={t('field.checklistPlaceholder')}
            onChange={(event) => setChecklistDraft(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && checklistDraft.trim()) {
                event.preventDefault()
                update({ checklistItems: [...(current.checklistItems ?? []), { id: Date.now(), text: checklistDraft.trim(), done: false }] })
                setChecklistDraft('')
              }
            }}
          />
          <button
            type="button"
            disabled={!checklistDraft.trim()}
            onClick={() => {
              update({ checklistItems: [...(current.checklistItems ?? []), { id: Date.now(), text: checklistDraft.trim(), done: false }] })
              setChecklistDraft('')
            }}
          >
            {t('create.checklist.add')}
          </button>
        </div>
      </div>
      <div className="desktop-detail-attachments">
        <span className="desktop-field-label">{t('edit.field.files')}</span>
        {!!visibleAttachments.length && (
          <div className="task-attachment-gallery">
            {visibleAttachments.map((attachment) => (
              <TaskAttachmentPreview
                key={attachment.id}
                attachment={attachment}
                accessToken={auth.accessToken}
                onRemove={async () => setRemovedAttachmentIds((current) => [...current, attachment.id])}
              />
            ))}
          </div>
        )}
        <AttachmentPicker
          attachments={newAttachments}
          galleryRef={galleryRef}
          cameraRef={cameraRef}
          fileRef={fileRef}
          onAttachments={(files) => {
            if (!files?.length) return
            const { accepted, rejected } = attachmentItems(files)
            setNewAttachments((current) => [...current, ...accepted])
            if (rejected.length) showTelegramMessage(t('error.attachmentTooLarge', { names: rejected.join(', ') }))
          }}
          onRemoveAttachment={(id) => setNewAttachments((current) => current.filter((item) => item.id !== id))}
        />
      </div>

      <button type="button" className="desktop-detail-save" disabled={!dirty || saving} onClick={saveAll}>
        <Save size={18} /> {saving ? t('common.saving') : t('common.save')}
      </button>

      {activeSheet === 'place' && (
        <BottomSheet onClose={() => setActiveSheet(null)}>
          <TaskPlaceSheet
            groups={groups}
            selected={current.visibility}
            selectedGroupId={current.groupId ?? null}
            groupPickerBusy={groupPickerBusy}
            onAddGroup={() => { setActiveSheet(null); setGroupPickerOpen(true); onLoadAvailableGroups() }}
            onSelect={(value, groupId) => {
              const groupChanged = groupId !== current.groupId
              update({
                visibility: value,
                groupId: value === 'GROUP' ? groupId : undefined,
                topicId: groupChanged ? undefined : current.topicId,
              })
              setActiveSheet(null)
              if (value === 'GROUP' && groupId) {
                onLoadGroupTopics(groupId)
                setActiveSheet('topic')
              }
            }}
          />
        </BottomSheet>
      )}
      {activeSheet === 'topic' && (
        <BottomSheet onClose={() => setActiveSheet(null)}>
          <TopicPickerSheet
            topics={groupTopics}
            loading={topicsLoading}
            selectedTopicId={current.topicId ?? null}
            onSelect={(topicId) => { update({ topicId }); setActiveSheet(null) }}
          />
        </BottomSheet>
      )}
      {activeSheet === 'author' && (
        <BottomSheet onClose={() => setActiveSheet(null)}>
          <AuthorSheet
            members={workspaceMembers}
            selected={draft.authorId ?? task.authorId ?? null}
            currentUserId={currentUserId}
            onSelect={(userId) => { update({ authorId: userId }); setActiveSheet(null) }}
          />
        </BottomSheet>
      )}
      {activeSheet === 'deadline' && (
        <DesktopDeadlineModal
          value={current.dueAt}
          onChange={(value) => update({ dueAt: value })}
          onDone={() => setActiveSheet(null)}
          onClose={() => setActiveSheet(null)}
        />
      )}
      {groupPickerOpen && (
        <BottomSheet onClose={() => setGroupPickerOpen(false)}>
          <GroupPickerSheet
            groups={availableGroups}
            loading={availableGroupsLoading}
            linkingChatId={linkingChatId}
            nativeBusy={groupPickerBusy}
            onLink={(chatId) => { onLinkGroup(chatId); setGroupPickerOpen(false) }}
            onNativePicker={() => { setGroupPickerOpen(false); onOpenNativeGroupPicker() }}
          />
        </BottomSheet>
      )}
    </div>
  )
}

const REMINDER_OPTIONS = [0, 5, 15, 30, 60, 1440]

function DesktopDeadlineModal({ value, onChange, onDone, onClose }: {
  value?: string
  onChange: (value: string | undefined) => void
  onDone: () => void
  onClose: () => void
}) {
  const { t } = useI18n()
  const initial = value ? new Date(value) : nextAvailableHour()
  const [noDeadline, setNoDeadline] = useState(!value)
  const [dateStr, setDateStr] = useState(`${initial.getFullYear()}-${pad(initial.getMonth() + 1)}-${pad(initial.getDate())}`)
  const [timeStr, setTimeStr] = useState(`${pad(initial.getHours())}:${pad(initial.getMinutes())}`)
  const [timeError, setTimeError] = useState('')

  function applyPreset(preset: Date) {
    setNoDeadline(false)
    setTimeError('')
    setDateStr(`${preset.getFullYear()}-${pad(preset.getMonth() + 1)}-${pad(preset.getDate())}`)
    setTimeStr(`${pad(preset.getHours())}:${pad(preset.getMinutes())}`)
  }

  function finish() {
    if (noDeadline) {
      onChange(undefined)
      onDone()
      return
    }
    const [year, month, day] = dateStr.split('-').map(Number)
    const [hours, minutes] = timeStr.split(':').map(Number)
    const selected = new Date(year, (month || 1) - 1, day || 1, hours || 0, minutes || 0)
    if (!dateStr || !timeStr || Number.isNaN(selected.getTime()) || selected <= new Date()) {
      setTimeError(t('deadline.error.future'))
      return
    }
    onChange(selected.toISOString())
    onDone()
  }

  return (
    <div className="desktop-modal-backdrop" onMouseDown={(event) => { if (event.currentTarget === event.target) onClose() }}>
      <div className="desktop-modal">
        <div className="desktop-modal-heading">
          <h2>{t('deadline.pageTitle')}</h2>
          <button type="button" onClick={onClose}><X size={18} /></button>
        </div>
        <div className="desktop-deadline-presets">
          <button type="button" className={noDeadline ? 'active' : ''} onClick={() => { setNoDeadline(true); setTimeError('') }}>{t('deadline.none')}</button>
          <button type="button" onClick={() => applyPreset(deadlinePreset('today'))}>{t('deadline.today')}</button>
          <button type="button" onClick={() => applyPreset(deadlinePreset('tomorrow'))}>{t('deadline.tomorrow')}</button>
        </div>
        <div className="desktop-modal-row">
          <label className="desktop-modal-field">
            <span>{t('deadline.date')}</span>
            <input type="date" value={dateStr} disabled={noDeadline} onChange={(event) => { setNoDeadline(false); setDateStr(event.target.value); setTimeError('') }} />
          </label>
          <label className="desktop-modal-field">
            <span>{t('deadline.customTime')}</span>
            <input type="time" step="60" value={timeStr} disabled={noDeadline} onChange={(event) => { setNoDeadline(false); setTimeStr(event.target.value); setTimeError('') }} />
          </label>
        </div>
        {timeError && <span className="deadline-error">{timeError}</span>}
        <button type="button" className="desktop-modal-submit" onClick={finish}>{t('deadline.done')}</button>
      </div>
    </div>
  )
}

function DesktopReminderFields({ dueAt, reminderMinutes, onReminderChange }: {
  dueAt?: string
  reminderMinutes?: number
  onReminderChange: (value: number | undefined) => void
}) {
  const { t } = useI18n()
  const due = dueAt ? new Date(dueAt) : null
  const initial = due ? new Date(due.getTime() - (reminderMinutes ?? 0) * 60_000) : new Date()
  const [dateStr, setDateStr] = useState(`${initial.getFullYear()}-${pad(initial.getMonth() + 1)}-${pad(initial.getDate())}`)
  const [timeStr, setTimeStr] = useState(`${pad(initial.getHours())}:${pad(initial.getMinutes())}`)
  const [error, setError] = useState('')

  function applyMinutes(minutes: number) {
    if (!due) return
    onReminderChange(reminderMinutes === minutes ? undefined : minutes)
    const picked = new Date(due.getTime() - minutes * 60_000)
    setDateStr(`${picked.getFullYear()}-${pad(picked.getMonth() + 1)}-${pad(picked.getDate())}`)
    setTimeStr(`${pad(picked.getHours())}:${pad(picked.getMinutes())}`)
    setError('')
  }

  function applyCustom(nextDateStr: string, nextTimeStr: string) {
    setDateStr(nextDateStr)
    setTimeStr(nextTimeStr)
    setError('')
    if (!due) return
    const [year, month, day] = nextDateStr.split('-').map(Number)
    const [hours, minutes] = nextTimeStr.split(':').map(Number)
    const picked = new Date(year, (month || 1) - 1, day || 1, hours || 0, minutes || 0)
    if (!nextDateStr || !nextTimeStr || Number.isNaN(picked.getTime())) return
    if (picked >= due) {
      setError(t('deadline.error.reminderPast'))
      return
    }
    onReminderChange(Math.round((due.getTime() - picked.getTime()) / 60_000))
  }

  return (
    <div className="desktop-reminder-section">
      <span>{t('deadline.reminder')}</span>
      {!due ? (
        <span className="desktop-modal-hint">{t('deadline.reminder.chooseFirst')}</span>
      ) : (
        <>
          <div className="desktop-reminder-options">
            {REMINDER_OPTIONS.map((minutes) => (
              <button
                key={minutes}
                type="button"
                className={reminderMinutes === minutes ? 'selected' : ''}
                onClick={() => applyMinutes(minutes)}
              >
                {reminderLabel(t, minutes)}
              </button>
            ))}
          </div>
          <div className="desktop-modal-row">
            <label className="desktop-modal-field">
              <span>{t('deadline.date')}</span>
              <input type="date" value={dateStr} onChange={(event) => applyCustom(event.target.value, timeStr)} />
            </label>
            <label className="desktop-modal-field">
              <span>{t('deadline.customTime')}</span>
              <input type="time" step="60" value={timeStr} onChange={(event) => applyCustom(dateStr, event.target.value)} />
            </label>
          </div>
          {error && <span className="deadline-error">{error}</span>}
          {reminderMinutes !== undefined && (
            <button type="button" className="desktop-reminder-clear" onClick={() => onReminderChange(undefined)}>
              {t('reminder.clear')}
            </button>
          )}
        </>
      )}
    </div>
  )
}

function KanbanBoard({ tasks, workspaceMembers, onChangeStatus, onSelectTask, onAddInColumn }: {
  tasks: Task[]
  workspaceMembers: WorkspaceMember[]
  onChangeStatus: (taskId: number, status: TaskStatus) => void
  onSelectTask: (id: number) => void
  onAddInColumn: (status: TaskStatus) => void
}) {
  const { t } = useI18n()
  const [dragOverStatus, setDragOverStatus] = useState<TaskStatus | null>(null)
  const now = Date.now()
  const isOverdue = (task: Task) =>
    task.dueAt != null
    && task.status !== 'COMPLETED'
    && task.status !== 'CANCELLED'
    && new Date(task.dueAt).getTime() < now
  const overdueTasks = tasks.filter(isOverdue)

  return (
    <div className="kanban-board">
      {STATUS_COLUMNS.map((status) => {
        const columnTasks = tasks.filter((task) => task.status === status && !isOverdue(task))
        return (
          <div
            key={status}
            className={`kanban-column ${dragOverStatus === status ? 'drag-over' : ''}`}
            onDragOver={(event) => { event.preventDefault(); setDragOverStatus(status) }}
            onDragLeave={() => setDragOverStatus((current) => (current === status ? null : current))}
            onDrop={(event) => {
              event.preventDefault()
              setDragOverStatus(null)
              const taskId = Number(event.dataTransfer.getData('text/plain'))
              if (taskId) onChangeStatus(taskId, status)
            }}
          >
            <div className={`kanban-column-heading status-${status.toLowerCase()}`}>
              <span>{statusLabel(t, status)}</span>
              <small>{columnTasks.length}</small>
            </div>
            <div className="kanban-column-body">
              {columnTasks.map((task) => (
                <KanbanCard
                  key={task.id}
                  task={task}
                  workspaceMembers={workspaceMembers}
                  onSelect={() => onSelectTask(task.id)}
                />
              ))}
              {status !== 'CANCELLED' && (
                <button type="button" className="kanban-add-button" onClick={() => onAddInColumn(status)}>
                  <Plus size={16} /> {t('nav.newTask')}
                </button>
              )}
            </div>
          </div>
        )
      })}
      <div className="kanban-column kanban-column-overdue">
        <div className="kanban-column-heading status-overdue">
          <span>{t('quickview.OVERDUE')}</span>
          <small>{overdueTasks.length}</small>
        </div>
        <div className="kanban-column-body">
          {overdueTasks.map((task) => (
            <KanbanCard
              key={task.id}
              task={task}
              workspaceMembers={workspaceMembers}
              onSelect={() => onSelectTask(task.id)}
            />
          ))}
        </div>
      </div>
    </div>
  )
}

function KanbanCard({ task, workspaceMembers, onSelect }: {
  task: Task
  workspaceMembers: WorkspaceMember[]
  onSelect: () => void
}) {
  const { t } = useI18n()
  const assignees = (task.assigneeIds ?? [])
    .map((id) => workspaceMembers.find((member) => member.id === id))
    .filter((member): member is WorkspaceMember => Boolean(member))
  const author = workspaceMembers.find((member) => member.id === task.authorId)
  const shownAssignees = assignees.slice(0, 3)
  const extraCount = assignees.length - shownAssignees.length

  return (
    <div
      className="kanban-card"
      draggable
      role="button"
      tabIndex={0}
      onDragStart={(event) => event.dataTransfer.setData('text/plain', String(task.id))}
      onClick={onSelect}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          onSelect()
        }
      }}
    >
      {author && (
        <span
          className="kanban-avatar kanban-avatar-author"
          title={`${t('edit.field.author')}: ${workspaceMemberName(t, author)}`}
        >
          {author.photoUrl ? <img src={author.photoUrl} alt="" /> : initials(workspaceMemberName(t, author))}
        </span>
      )}
      <div className="kanban-card-top">
        <span className={`priority-mark priority-${task.priority.toLowerCase()}`} />
        <strong>{task.title}</strong>
      </div>
      {task.groupName && (
        <span className="kanban-card-group"><UsersRound size={11} />{task.groupName}</span>
      )}
      <div className="kanban-card-footer">
        <div className="kanban-card-people">
          {shownAssignees.map((member) => {
            const name = workspaceMemberName(t, member)
            return (
              <span key={member.id} className="kanban-avatar" title={name}>
                {member.photoUrl ? <img src={member.photoUrl} alt="" /> : initials(name)}
              </span>
            )
          })}
          {extraCount > 0 && <span className="kanban-avatar kanban-avatar-more">+{extraCount}</span>}
        </div>
        {task.dueAt && <small className={`kanban-card-due due-${dueUrgency(task.dueAt, task.status)}`}>{formatDeadline(task.dueAt)}</small>}
      </div>
    </div>
  )
}

function NewTaskModal({
  groups,
  workspaceMembers,
  currentUserId,
  defaultStatus,
  availableGroups,
  availableGroupsLoading,
  linkingChatId,
  onLoadAvailableGroups,
  groupTopics,
  topicsLoading,
  onLoadGroupTopics,
  onLinkGroup,
  groupPickerBusy,
  onOpenNativeGroupPicker,
  onAddBotToGroup,
  onClose,
  onCreate,
}: {
  groups: LinkedGroup[]
  workspaceMembers: WorkspaceMember[]
  currentUserId?: number
  defaultStatus: TaskStatus
  availableGroups: AvailableTelegramGroup[]
  availableGroupsLoading: boolean
  linkingChatId: number | null
  onLoadAvailableGroups: () => void
  groupTopics: TelegramTopic[]
  topicsLoading: boolean
  onLoadGroupTopics: (groupId: number) => void
  onLinkGroup: (chatId: number) => void
  groupPickerBusy: boolean
  onOpenNativeGroupPicker: () => void
  onAddBotToGroup: (group: LinkedGroup) => void
  onClose: () => void
  onCreate: DesktopShellProps['onCreateTask']
}) {
  const { t } = useI18n()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [visibility, setVisibility] = useState<TaskVisibility>('WORKSPACE')
  const [groupId, setGroupId] = useState<number | undefined>(undefined)
  const [topicId, setTopicId] = useState<number | undefined>(undefined)
  const [topicSheetOpen, setTopicSheetOpen] = useState(false)
  const [assigneeIds, setAssigneeIds] = useState<number[]>([])
  const [priority, setPriority] = useState<TaskPriority>('NORMAL')
  const [status, setStatus] = useState<TaskStatus>(defaultStatus)
  const [dueAt, setDueAt] = useState<string | undefined>(undefined)
  const [reminderMinutes, setReminderMinutes] = useState<number | undefined>(undefined)
  const [checklist, setChecklist] = useState<Array<{ id: number; text: string; done: boolean }>>([])
  const [checklistDraft, setChecklistDraft] = useState('')
  const [attachments, setAttachments] = useState<AttachmentItem[]>([])
  const [saving, setSaving] = useState(false)
  const [placeSheetOpen, setPlaceSheetOpen] = useState(false)
  const [deadlineSheetOpen, setDeadlineSheetOpen] = useState(false)
  const [groupPickerOpen, setGroupPickerOpen] = useState(false)
  const galleryRef = useRef<HTMLInputElement | null>(null)
  const cameraRef = useRef<HTMLInputElement | null>(null)
  const fileRef = useRef<HTMLInputElement | null>(null)

  const selectedGroup = groups.find((group) => group.id === groupId)
  const assigneeCandidates: Array<{ id: number; name: string; username?: string; photoUrl?: string }> =
    visibility === 'GROUP'
      ? (selectedGroup?.memberList ?? [])
      : workspaceMembers.filter((member) => member.active && !member.temporarilyBlocked)
          .map((member) => ({ id: member.id, name: workspaceMemberName(t, member), username: member.username, photoUrl: member.photoUrl }))
  const placeLabelText = visibility === 'GROUP' ? selectedGroup?.title ?? t('place.GROUP') : placeLabel(t, visibility)

  async function submit() {
    if (!title.trim()) return
    if (visibility === 'GROUP' && (!groupId || assigneeIds.length === 0)) return
    setSaving(true)
    const created = await onCreate({
      title: title.trim(),
      description: description.trim() || undefined,
      visibility,
      groupId,
      topicId,
      assigneeIds,
      priority,
      status,
      dueAt,
      reminderMinutes,
      checklist: checklist.map((item) => ({ text: item.text, done: item.done })).filter((item) => item.text),
      attachments: attachments.map((item) => item.file),
    })
    setSaving(false)
    if (created) onClose()
  }

  return (
    <div className="desktop-modal-backdrop" onMouseDown={(event) => { if (event.currentTarget === event.target) onClose() }}>
      <div className="desktop-modal desktop-modal-wide">
        <div className="desktop-modal-heading">
          <h2>{t('nav.newTask')}</h2>
          <button type="button" onClick={onClose}><X size={20} /></button>
        </div>
        <label className="desktop-modal-field">
          <span>{t('create.name')}</span>
          <input value={title} onChange={(event) => setTitle(event.target.value)} placeholder={t('create.namePlaceholder')} autoFocus />
        </label>
        <div className="desktop-modal-row">
          <button type="button" className="desktop-modal-pick" onClick={() => setPlaceSheetOpen(true)}>
            <span>{t('create.place')}</span><strong>{placeLabelText}</strong>
          </button>
          <button type="button" className="desktop-modal-pick" onClick={() => setDeadlineSheetOpen(true)}>
            <span>{t('edit.field.deadline')}</span><strong>{dueAt ? formatDeadline(dueAt) : t('deadline.none')}</strong>
          </button>
        </div>
        <DesktopReminderFields dueAt={dueAt} reminderMinutes={reminderMinutes} onReminderChange={setReminderMinutes} />
        {visibility === 'GROUP' && selectedGroup && !selectedGroup.botConnected && (
          <div className="desktop-modal-warning">
            <span>{t('create.bot.warning.subtitle')}</span>
            <button type="button" onClick={() => onAddBotToGroup(selectedGroup)}>{t('create.bot.add')}</button>
          </div>
        )}
        {visibility !== 'PERSONAL' && (
          <div className="desktop-modal-field">
            <span>{t('create.assignee.title')}</span>
            <div className="desktop-modal-assignees">
              {assigneeCandidates.map((member) => {
                const selected = assigneeIds.includes(member.id)
                return (
                  <button
                    key={member.id}
                    type="button"
                    className={selected ? 'selected' : ''}
                    onClick={() => setAssigneeIds((current) =>
                      current.includes(member.id) ? current.filter((id) => id !== member.id) : [...current, member.id])}
                  >
                    {initials(member.name)} {member.name}
                  </button>
                )
              })}
              {assigneeCandidates.length === 0 && <span className="desktop-modal-hint">{t('create.assignee.empty')}</span>}
            </div>
          </div>
        )}
        <div className="desktop-modal-row">
          <label className="desktop-modal-field">
            <span>{t('field.status')}</span>
            <select value={status} onChange={(event) => setStatus(event.target.value as TaskStatus)}>
              {STATUS_COLUMNS.map((item) => <option key={item} value={item}>{statusLabel(t, item)}</option>)}
            </select>
          </label>
          <label className="desktop-modal-field">
            <span>{t('edit.field.priority')}</span>
            <select value={priority} onChange={(event) => setPriority(event.target.value as TaskPriority)}>
              {(['LOW', 'NORMAL', 'IMPORTANT', 'URGENT'] as TaskPriority[]).map((item) => (
                <option key={item} value={item}>{priorityLabel(t, item)}</option>
              ))}
            </select>
          </label>
        </div>
        <label className="desktop-modal-field">
          <span>{t('edit.field.description')}</span>
          <textarea
            rows={4}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            placeholder={t('edit.field.descriptionPlaceholder')}
          />
        </label>
        <div className="desktop-detail-checklist">
          <span className="desktop-field-label">{t('edit.field.checklist')}</span>
          {checklist.map((item) => (
            <div className="desktop-checklist-row" key={item.id}>
              <input
                type="checkbox"
                checked={item.done}
                onChange={() => setChecklist((current) => current.map((entry) =>
                  entry.id === item.id ? { ...entry, done: !entry.done } : entry))}
              />
              <input
                value={item.text}
                onChange={(event) => setChecklist((current) => current.map((entry) =>
                  entry.id === item.id ? { ...entry, text: event.target.value } : entry))}
              />
              <button type="button" onClick={() => setChecklist((current) => current.filter((entry) => entry.id !== item.id))}>
                <X size={14} />
              </button>
            </div>
          ))}
          <div className="desktop-checklist-entry">
            <input
              value={checklistDraft}
              placeholder={t('field.checklistPlaceholder')}
              onChange={(event) => setChecklistDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && checklistDraft.trim()) {
                  event.preventDefault()
                  setChecklist((current) => [...current, { id: Date.now(), text: checklistDraft.trim(), done: false }])
                  setChecklistDraft('')
                }
              }}
            />
            <button
              type="button"
              disabled={!checklistDraft.trim()}
              onClick={() => {
                setChecklist((current) => [...current, { id: Date.now(), text: checklistDraft.trim(), done: false }])
                setChecklistDraft('')
              }}
            >
              {t('create.checklist.add')}
            </button>
          </div>
        </div>
        <div className="desktop-modal-field">
          <span>{t('attachment.filesTitle')}</span>
          <AttachmentPicker
            attachments={attachments}
            galleryRef={galleryRef}
            cameraRef={cameraRef}
            fileRef={fileRef}
            onAttachments={(files) => {
              if (!files?.length) return
              const { accepted, rejected } = attachmentItems(files)
              setAttachments((current) => [...current, ...accepted])
              if (rejected.length) showTelegramMessage(t('error.attachmentTooLarge', { names: rejected.join(', ') }))
            }}
            onRemoveAttachment={(id) => setAttachments((current) => current.filter((item) => item.id !== id))}
          />
        </div>
        <button className="desktop-modal-submit" type="button" disabled={saving || !title.trim()} onClick={() => void submit()}>
          {saving ? t('common.saving') : t('create.submit')}
        </button>
      </div>

      {placeSheetOpen && (
        <BottomSheet onClose={() => setPlaceSheetOpen(false)}>
          <TaskPlaceSheet
            groups={groups}
            selected={visibility}
            selectedGroupId={groupId ?? null}
            groupPickerBusy={groupPickerBusy}
            onAddGroup={() => { setPlaceSheetOpen(false); setGroupPickerOpen(true); onLoadAvailableGroups() }}
            onSelect={(value, nextGroupId) => {
              const groupChanged = nextGroupId !== groupId
              setVisibility(value)
              setGroupId(value === 'GROUP' ? nextGroupId : undefined)
              if (groupChanged) {
                setTopicId(undefined)
                setAssigneeIds([])
              }
              setPlaceSheetOpen(false)
              if (value === 'GROUP' && nextGroupId) {
                onLoadGroupTopics(nextGroupId)
                setTopicSheetOpen(true)
              }
            }}
          />
        </BottomSheet>
      )}
      {topicSheetOpen && (
        <BottomSheet onClose={() => setTopicSheetOpen(false)}>
          <TopicPickerSheet
            topics={groupTopics}
            loading={topicsLoading}
            selectedTopicId={topicId ?? null}
            onSelect={(nextTopicId) => { setTopicId(nextTopicId); setTopicSheetOpen(false) }}
          />
        </BottomSheet>
      )}
      {deadlineSheetOpen && (
        <DesktopDeadlineModal
          value={dueAt}
          onChange={setDueAt}
          onDone={() => setDeadlineSheetOpen(false)}
          onClose={() => setDeadlineSheetOpen(false)}
        />
      )}
      {groupPickerOpen && (
        <BottomSheet onClose={() => setGroupPickerOpen(false)}>
          <GroupPickerSheet
            groups={availableGroups}
            loading={availableGroupsLoading}
            linkingChatId={linkingChatId}
            nativeBusy={groupPickerBusy}
            onLink={(chatId) => { onLinkGroup(chatId); setGroupPickerOpen(false) }}
            onNativePicker={() => { setGroupPickerOpen(false); onOpenNativeGroupPicker() }}
          />
        </BottomSheet>
      )}
    </div>
  )
}

function DesktopGroupsView({
  groups,
  tasks,
  availableGroups,
  availableGroupsLoading,
  linkingChatId,
  onLoadAvailableGroups,
  onLinkGroup,
  groupPickerBusy,
  onOpenNativeGroupPicker,
  onInviteMembers,
  invitingMembers,
  onAddBotToGroup,
}: {
  groups: LinkedGroup[]
  tasks: Task[]
  availableGroups: AvailableTelegramGroup[]
  availableGroupsLoading: boolean
  linkingChatId: number | null
  onLoadAvailableGroups: () => void
  onLinkGroup: (chatId: number) => void
  groupPickerBusy: boolean
  onOpenNativeGroupPicker: () => void
  onInviteMembers: (group: LinkedGroup) => void
  invitingMembers: boolean
  onAddBotToGroup: (group: LinkedGroup) => void
}) {
  const { t } = useI18n()
  const [selectedGroupId, setSelectedGroupId] = useState<number | null>(null)
  const [pickerOpen, setPickerOpen] = useState(false)
  const selectedGroup = groups.find((group) => group.id === selectedGroupId)
  const groupIds = new Set(groups.map((group) => group.id))
  const activeTasksTotal = tasks.filter((task) => task.visibility === 'GROUP' && task.groupId != null
    && groupIds.has(task.groupId) && task.status !== 'COMPLETED' && task.status !== 'CANCELLED').length
  const overdueTasksTotal = tasks.filter((task) => task.visibility === 'GROUP' && task.groupId != null
    && groupIds.has(task.groupId) && task.status !== 'COMPLETED' && task.status !== 'CANCELLED'
    && task.dueAt && new Date(task.dueAt) < new Date()).length
  const membersTotal = groups.reduce((sum, group) => sum + group.members, 0)

  return (
    <div className="desktop-groups-view">
      <div className="desktop-groups-header">
        <div>
          <span className="desktop-eyebrow">{t('settings.group.workspace')}</span>
          <h2>{t('groups.title')}</h2>
        </div>
        <button type="button" className="desktop-primary-button" onClick={() => { setPickerOpen(true); onLoadAvailableGroups() }}>
          <Plus size={18} /> {t('groups.add')}
        </button>
      </div>
      <div className="desktop-stat-row">
        <div className="desktop-stat-card"><strong>{groups.length}</strong><span>{t('groups.title')}</span></div>
        <div className="desktop-stat-card"><strong>{activeTasksTotal}</strong><span>{t('quickview.ACTIVE')}</span></div>
        <div className="desktop-stat-card"><strong>{membersTotal}</strong><span>{t('settings.access')}</span></div>
        <div className="desktop-stat-card"><strong>{overdueTasksTotal}</strong><span>{t('quickview.OVERDUE')}</span></div>
      </div>
      <div className="desktop-split">
        <div className="desktop-list-pane">
          {groups.length === 0 ? (
            <div className="desktop-list-empty"><Folder size={26} /><p>{t('groups.empty')}</p></div>
          ) : (
            <div className="desktop-task-rows">
              {groups.map((group) => (
                <button
                  key={group.id}
                  type="button"
                  className={`desktop-task-row ${group.id === selectedGroupId ? 'selected' : ''}`}
                  onClick={() => setSelectedGroupId(group.id)}
                >
                  <span className="desktop-group-avatar">{initials(group.title)}</span>
                  <span className="desktop-task-row-copy">
                    <strong>{group.title}</strong>
                    <small>{t('groups.membersCount', { count: group.members })}</small>
                  </span>
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="desktop-detail-pane">
          {selectedGroup ? (
            <div className="desktop-group-detail">
              <h3>{selectedGroup.title}</h3>
              <p>{t('groups.membersCount', { count: selectedGroup.members })} · {selectedGroup.botConnected ? t('groups.botConnected') : t('groups.botMissing')}</p>
              {!selectedGroup.botConnected && (
                <button type="button" className="desktop-primary-button" onClick={() => onAddBotToGroup(selectedGroup)}>
                  {t('create.bot.add')}
                </button>
              )}
              {selectedGroup.botConnected && (
                <button type="button" className="desktop-primary-button" disabled={invitingMembers} onClick={() => onInviteMembers(selectedGroup)}>
                  <UsersRound size={16} /> {invitingMembers ? t('create.invite.sending') : t('create.invite.send')}
                </button>
              )}
              <div className="desktop-group-member-list">
                {selectedGroup.memberList.map((member) => (
                  <div className="desktop-group-member-row" key={member.id}>
                    <span className="desktop-group-avatar">{initials(member.name)}</span>
                    {member.name}
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <DesktopEmptyDetail />
          )}
        </div>
      </div>
      {pickerOpen && (
        <BottomSheet onClose={() => setPickerOpen(false)}>
          <GroupPickerSheet
            groups={availableGroups}
            loading={availableGroupsLoading}
            linkingChatId={linkingChatId}
            nativeBusy={groupPickerBusy}
            onLink={(chatId) => { onLinkGroup(chatId); setPickerOpen(false) }}
            onNativePicker={() => { setPickerOpen(false); onOpenNativeGroupPicker() }}
          />
        </BottomSheet>
      )}
    </div>
  )
}

interface StatisticsResponse {
  totalTasks: number
  openCount: number
  todayCount: number
  weekCount: number
  overdueCount: number
  overdueRiskLabel: string
  last30DaysCreated: number
  focus: { taskTitle: string | null; completionRate: number; completedToday: number }
  quickStats: Array<{ key: string; label: string; value: string }>
  rhythm: Array<{ date: string; created: number; completed: number }>
  workloadSummary: Array<{ label: string; value: string }>
  statusBreakdown: Array<{ status: TaskStatus; count: number }>
  priorityBreakdown: Array<{ priority: TaskPriority; count: number }>
  assigneeWorkload: Array<{ name: string; active: number; overdue: number }>
  groupBreakdown: Array<{ name: string; active: number; total: number }>
}

type QuickStatKey = 'OVERDUE' | 'TODAY' | 'UNASSIGNED' | 'GROUPS' | 'ACTIVE'

function DesktopStatisticsView({ auth, activeWorkspaceId, onNavigate }: {
  auth: AuthResponse
  activeWorkspaceId: number | null
  onNavigate: (target: QuickStatKey) => void
}) {
  const { t } = useI18n()
  const [data, setData] = useState<StatisticsResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const workspaceId = activeWorkspaceId ?? auth.workspaces[0]?.id

  useEffect(() => {
    if (!workspaceId) return
    let cancelled = false
    setLoading(true)
    fetch(`/api/statistics?workspaceId=${workspaceId}`, {
      headers: { Authorization: `Bearer ${auth.accessToken}` },
    })
      .then((response) => (response.ok ? response.json() : null))
      .then((result) => { if (!cancelled) setData(result) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [workspaceId, auth.accessToken])

  if (loading || !data) {
    return <div className="desktop-list-empty"><Sparkles size={26} /><p>{t('common.loading')}</p></div>
  }

  const maxRhythm = Math.max(1, ...data.rhythm.map((point) => Math.max(point.created, point.completed)))
  const maxStatus = Math.max(1, ...data.statusBreakdown.map((item) => item.count))
  const maxPriority = Math.max(1, ...data.priorityBreakdown.map((item) => item.count))
  const maxGroup = Math.max(1, ...data.groupBreakdown.map((item) => item.total))

  return (
    <div className="desktop-statistics-view">
      <div className="desktop-stats-header">
        <div>
          <span className="desktop-eyebrow">{t('desktop.statistics.eyebrow')}</span>
          <h2>{t('desktop.statistics.title')}</h2>
          <p>{data.openCount === 0 ? t('desktop.statistics.calm') : t('desktop.statistics.busy', { count: data.openCount })}</p>
        </div>
        <div className="desktop-focus-card">
          <span>{t('desktop.statistics.focus')}</span>
          <strong>{data.focus.taskTitle ?? t('desktop.statistics.noOpenTask')}</strong>
          <small>{t('desktop.statistics.completionRate', { rate: data.focus.completionRate, count: data.focus.completedToday })}</small>
        </div>
      </div>

      <div className="desktop-quick-stat-row">
        {data.quickStats.map((item) => (
          <button type="button" className="desktop-quick-stat" key={item.key} onClick={() => onNavigate(item.key as QuickStatKey)}>
            {item.key === 'OVERDUE' && <AlertTriangle size={18} />}
            {item.key === 'TODAY' && <CalendarClock size={18} />}
            {item.key === 'UNASSIGNED' && <UsersRound size={18} />}
            {item.key === 'GROUPS' && <Folder size={18} />}
            <span>{item.label}</span>
            <strong>{item.value}</strong>
            <ChevronRight size={16} />
          </button>
        ))}
      </div>

      <div className="desktop-number-row">
        <button type="button" className="desktop-number-card" onClick={() => onNavigate('ACTIVE')}>
          <span>{t('desktop.statistics.openWork')}</span>
          <strong>{data.openCount}</strong>
          <small>{t('desktop.statistics.ofTotal', { count: data.totalTasks })}</small>
        </button>
        <button type="button" className="desktop-number-card" onClick={() => onNavigate('TODAY')}>
          <span>{t('quickview.TODAY')}</span>
          <strong>{data.todayCount}</strong>
          <small>{t('desktop.statistics.thisWeek', { count: data.weekCount })}</small>
        </button>
        <button type="button" className="desktop-number-card" onClick={() => onNavigate('OVERDUE')}>
          <span>{t('quickview.OVERDUE')}</span>
          <strong>{data.overdueCount}</strong>
          <small>{data.overdueRiskLabel}</small>
        </button>
        <div className="desktop-number-card">
          <span>{t('desktop.statistics.days30')}</span>
          <strong>{data.last30DaysCreated}</strong>
          <small>{t('desktop.statistics.created')}</small>
        </div>
      </div>

      <div className="desktop-stats-split">
        <div className="desktop-rhythm-card">
          <div className="desktop-card-heading">
            <strong>{t('desktop.statistics.rhythm')}</strong>
            <div className="desktop-legend">
              <span className="dot created" /> {t('desktop.statistics.created')}
              <span className="dot completed" /> {t('desktop.statistics.completed')}
            </div>
          </div>
          <p>{t('desktop.statistics.rhythmSubtitle')}</p>
          <div className="desktop-rhythm-chart">
            {data.rhythm.map((point) => (
              <div className="desktop-rhythm-bar" key={point.date} title={point.date}>
                <div className="bar created" style={{ height: `${(point.created / maxRhythm) * 100}%` }} />
                <div className="bar completed" style={{ height: `${(point.completed / maxRhythm) * 100}%` }} />
              </div>
            ))}
          </div>
          <div className="desktop-rhythm-labels">
            {data.rhythm.map((point) => (
              <span key={point.date}>{Number(point.date.slice(-2))}</span>
            ))}
          </div>
        </div>
        <div className="desktop-workload-card">
          <strong>{t('desktop.statistics.workload')}</strong>
          <p>{t('desktop.statistics.workloadSubtitle')}</p>
          <div className="desktop-workload-list">
            {data.workloadSummary.map((item) => (
              <div className="desktop-workload-row" key={item.label}>
                <span>{item.label}</span>
                <small>{item.value}</small>
                <ChevronRight size={16} />
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="desktop-mini-grid">
        <div className="desktop-mini-card">
          <div className="desktop-card-heading"><strong>{t('desktop.statistics.statuses')}</strong><Flag size={16} /></div>
          <p>{t('desktop.statistics.statusSubtitle')}</p>
          {data.statusBreakdown.every((item) => item.count === 0) ? (
            <p className="desktop-mini-empty">{t('desktop.statistics.noData')}</p>
          ) : data.statusBreakdown.map((item) => (
            <div className="desktop-mini-row" key={item.status}>
              <span>{statusLabel(t, item.status)}</span>
              <div className="desktop-mini-bar"><div style={{ width: `${(item.count / maxStatus) * 100}%` }} /></div>
              <small>{item.count}</small>
            </div>
          ))}
        </div>
        <div className="desktop-mini-card">
          <div className="desktop-card-heading"><strong>{t('desktop.statistics.priority')}</strong><Flag size={16} /></div>
          <p>{t('desktop.statistics.prioritySubtitle')}</p>
          {data.priorityBreakdown.every((item) => item.count === 0) ? (
            <p className="desktop-mini-empty">{t('desktop.statistics.noPriorityData')}</p>
          ) : data.priorityBreakdown.map((item) => (
            <div className="desktop-mini-row" key={item.priority}>
              <span>{priorityLabel(t, item.priority)}</span>
              <div className="desktop-mini-bar"><div style={{ width: `${(item.count / maxPriority) * 100}%` }} /></div>
              <small>{item.count}</small>
            </div>
          ))}
        </div>
        <div className="desktop-mini-card">
          <div className="desktop-card-heading"><strong>{t('desktop.statistics.assignee')}</strong><UsersRound size={16} /></div>
          <p>{t('desktop.statistics.assigneeSubtitle')}</p>
          {data.assigneeWorkload.length === 0 ? (
            <p className="desktop-mini-empty">{t('desktop.statistics.noAssigneeData')}</p>
          ) : data.assigneeWorkload.map((item) => (
            <div className="desktop-mini-row" key={item.name}>
              <span>{item.name}</span>
              <small>{item.active} / {item.overdue}</small>
            </div>
          ))}
        </div>
        <div className="desktop-mini-card">
          <div className="desktop-card-heading"><strong>{t('groups.title')}</strong><Folder size={16} /></div>
          <p>{t('desktop.statistics.groupsSubtitle')}</p>
          {data.groupBreakdown.length === 0 ? (
            <p className="desktop-mini-empty">{t('desktop.statistics.noData')}</p>
          ) : data.groupBreakdown.map((item) => (
            <div className="desktop-mini-row" key={item.name}>
              <span>{item.name}</span>
              <div className="desktop-mini-bar"><div style={{ width: `${(item.total ? item.active / item.total : 0) * 100}%` }} /></div>
              <small>{item.active}/{item.total}</small>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function DesktopSettingsView({
  theme,
  remindersEnabled,
  onSavePreferences,
  workspaceMembers,
  currentUserId,
  groups,
  onChangeGroupTaskPolicy,
  archivedTasks,
  archivedLoading,
  onLoadArchived,
  onRestoreTask,
  onDeleteTaskForever,
}: {
  theme: Theme
  remindersEnabled: boolean
  onSavePreferences: (patch: { uiLanguage?: Lang; theme?: Theme; remindersEnabled?: boolean }) => void
  workspaceMembers: WorkspaceMember[]
  currentUserId?: number
  groups: LinkedGroup[]
  onChangeGroupTaskPolicy: (groupId: number, policy: string) => void
  archivedTasks: Task[]
  archivedLoading: boolean
  onLoadArchived: () => void
  onRestoreTask: (taskId: number) => void
  onDeleteTaskForever: (taskId: number) => void
}) {
  const { t, lang } = useI18n()
  const [section, setSection] = useState<DesktopSettingsTab>('general')
  const noop = () => undefined

  return (
    <div className="desktop-settings-view">
      <div className="desktop-settings-tabs">
        <button type="button" className={section === 'general' ? 'active' : ''} onClick={() => setSection('general')}>
          <SlidersHorizontal size={15} /> {t('settings.title')}
        </button>
        <button type="button" className={section === 'members' ? 'active' : ''} onClick={() => setSection('members')}>
          <ShieldCheck size={15} /> {t('settings.access')}
        </button>
        <button type="button" className={section === 'rules' ? 'active' : ''} onClick={() => setSection('rules')}>
          <Flag size={15} /> {t('settings.taskRules')}
        </button>
        <button
          type="button"
          className={section === 'archive' ? 'active' : ''}
          onClick={() => { setSection('archive'); onLoadArchived() }}
        >
          <Archive size={15} /> {t('settings.archive')}
        </button>
        <button type="button" className={section === 'help' ? 'active' : ''} onClick={() => setSection('help')}>
          <HelpCircle size={15} /> {t('settings.help')}
        </button>
      </div>

      {section === 'general' && (
        <div className="desktop-settings-general">
          <section className="desktop-settings-card">
            <h3>{t('settings.appearance')}</h3>
            <div className="desktop-choice-row">
              {(['system', 'dark', 'light'] as Theme[]).map((value) => (
                <button key={value} type="button" className={theme === value ? 'selected' : ''} onClick={() => onSavePreferences({ theme: value })}>
                  <Moon size={16} /> {t(`settings.appearance.${value}`)}
                </button>
              ))}
            </div>
          </section>
          <section className="desktop-settings-card">
            <h3>{t('settings.language')}</h3>
            <div className="desktop-choice-row">
              {(['uz', 'ru', 'en'] as Lang[]).map((value) => (
                <button key={value} type="button" className={lang === value ? 'selected' : ''} onClick={() => onSavePreferences({ uiLanguage: value })}>
                  {value.toUpperCase()}
                </button>
              ))}
            </div>
          </section>
          <section className="desktop-settings-card">
            <h3>{t('settings.notifications')}</h3>
            <button type="button" className={`desktop-toggle ${remindersEnabled ? 'on' : ''}`} onClick={() => onSavePreferences({ remindersEnabled: !remindersEnabled })}>
              <Bell size={16} /> {remindersEnabled ? t('settings.notifications.on') : t('settings.notifications.off')}
            </button>
          </section>
        </div>
      )}
      {section === 'members' && (
        <div className="desktop-settings-embed">
          <MembersScreen members={workspaceMembers} currentUserId={currentUserId} onBack={noop} />
        </div>
      )}
      {section === 'rules' && (
        <div className="desktop-settings-embed">
          <TaskRulesScreen groups={groups} onBack={noop} onChangePolicy={onChangeGroupTaskPolicy} />
        </div>
      )}
      {section === 'archive' && (
        <div className="desktop-settings-embed">
          <ArchiveScreen
            tasks={archivedTasks}
            loading={archivedLoading}
            onBack={noop}
            onRestore={onRestoreTask}
            onDeleteForever={onDeleteTaskForever}
            currentUserId={currentUserId}
            isOwner={workspaceMembers.find((member) => member.id === currentUserId)?.roleCode === 'OWNER'}
          />
        </div>
      )}
      {section === 'help' && (
        <div className="desktop-settings-embed">
          <HelpScreen onBack={noop} />
        </div>
      )}
    </div>
  )
}
