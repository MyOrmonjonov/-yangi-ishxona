/// <reference types="vite/client" />

interface TelegramWebApp {
  initData: string
  version: string
  platform: string
  initDataUnsafe?: {
    user?: {
      id: number
      first_name: string
      last_name?: string
      username?: string
      photo_url?: string
    }
    start_param?: string
  }
  colorScheme: 'light' | 'dark'
  ready(): void
  expand(): void
  requestFullscreen?(): void
  isVersionAtLeast?(version: string): boolean
  close?(): void
  requestChat?(preparedButtonId: string, callback?: (selected: boolean) => void): void
  openTelegramLink?(url: string): void
  showAlert?(message: string, callback?: () => void): void
  showConfirm?(message: string, callback: (confirmed: boolean) => void): void
}

interface Window {
  Telegram?: { WebApp: TelegramWebApp }
}
