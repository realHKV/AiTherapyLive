import apiClient from './client';

export const profileAPI = {
  getProfile: async () => {
    const response = await apiClient.get('/profile/me');
    return response;
  },

  updateProfile: async (profileData) => {
    const response = await apiClient.put('/profile/me', profileData);
    return response;
  },

  saveTherapist: async (therapistId) => {
    const response = await apiClient.put('/profile/me', {
      aiPersona: therapistId,
    });
    return response;
  },

  getMemories: async (params = {}) => {
    // API returns MemoryPageResponse { memories: [], total, offset, limit }
    const response = await apiClient.get('/profile/me/memories', { params });
    return response;
  },

  deleteProfile: async () => {
    const response = await apiClient.delete('/profile/me');
    return response;
  }
};
