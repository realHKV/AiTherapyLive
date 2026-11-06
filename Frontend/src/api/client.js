import axios from 'axios';

export const API_BASE_URL = (import.meta.env?.VITE_API_BASE_URL || 'http://localhost:8080/api/v1')
  .replace(/\/+$/, '');

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

let refreshPromise = null;

export function expireSession() {
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  localStorage.removeItem('user');
  window.dispatchEvent(new Event('auth:expired'));
}

export function refreshSession() {
  const refreshToken = localStorage.getItem('refreshToken');
  if (!refreshToken) return Promise.reject(new Error('Your session has expired. Please sign in again.'));
  if (!refreshPromise) {
    refreshPromise = apiClient.post('/auth/refresh', { refreshToken }, { skipAuthRefresh: true })
      .then(data => {
        if (!data?.accessToken || !data?.refreshToken) throw new Error('Invalid session refresh response.');
        localStorage.setItem('accessToken', data.accessToken);
        localStorage.setItem('refreshToken', data.refreshToken);
        return data.accessToken;
      })
      .catch(error => {
        if (error.response?.status === 401) {
          expireSession();
        }
        throw error;
      })
      .finally(() => { refreshPromise = null; });
  }
  return refreshPromise;
}

// Request interceptor to add the standard Auth string (if any)
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken');
    const publicAuth = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/oauth/exchange']
      .includes(config.url);
    if (publicAuth) delete config.headers.Authorization;
    else if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor to handle token expiry / unauthenticated scenarios
apiClient.interceptors.response.use(
  (response) => {
    const resData = response.data;
    if (resData && resData.success === true) {
      return resData.data;
    }
    return resData;
  },
  async (error) => {
    const config = error.config;
    if (error.response?.status !== 401 || !config || config.skipAuthRefresh
        || config.url?.startsWith('/auth/') || config.retriedAfterRefresh) {
      throw error;
    }
    if (!localStorage.getItem('refreshToken')) {
      expireSession();
      throw error;
    }
    const token = await refreshSession();
    if (['get', 'head'].includes(config.method?.toLowerCase())) {
      config.retriedAfterRefresh = true;
      config.headers.Authorization = `Bearer ${token}`;
      return apiClient(config);
    }
    throw new Error('Your session was refreshed. Please submit that action again.');
  }
);

export default apiClient;
