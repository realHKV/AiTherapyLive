import apiClient from './client';

export const authAPI = {
  login: async (email, password) => {
    const response = await apiClient.post('/auth/login', { email, password });
    return response; 
  },

  register: async (email, password, displayName) => {
    const response = await apiClient.post('/auth/register', { email, password, displayName });
    return response;
  },

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
