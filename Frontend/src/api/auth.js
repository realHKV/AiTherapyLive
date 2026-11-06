import apiClient, { API_BASE_URL } from './client';

export const googleSignInUrl = () =>
  `${(import.meta.env.VITE_OAUTH_BASE_URL || API_BASE_URL).replace(/\/$/, '')}/oauth2/authorization/google`;

export const authAPI = {
  login: async (email, password) => {
    const response = await apiClient.post('/auth/login', { email, password });
    return response; 
  },

  register: async (email, password, displayName) => {
    const response = await apiClient.post('/auth/register', { email, password, displayName });
    return response;
  },

  exchangeOauthTicket: (ticket) => apiClient.post('/auth/oauth/exchange', { ticket }),

  logout: async (refreshToken) => {
    const response = await apiClient.post('/auth/logout', { refreshToken });
    return response;
  },

  getProfile: async (token) => {
  const response = await apiClient.get('/profile/me', {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  });
  return response;
  },

  deleteAccount: async () => {
    const response = await apiClient.delete('/auth/account');
    return response;
  }
};
