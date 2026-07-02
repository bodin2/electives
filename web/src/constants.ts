export const DEV = process.env.NODE_ENV === 'development' || import.meta.env.DEV

export const APP_VERSION = process.env.APP_VERSION || 'unknown'
export const APP_COMMIT = process.env.APP_COMMIT || 'unknown'

export const API_BASE_URL = process.env.API_BASE_URL || `http://${window.location.hostname}:8080`
export const API_CLIENT_NAME = `web@${APP_VERSION}`
