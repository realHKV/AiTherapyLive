import apiClient from './client.js';

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
      personaId: therapistId,
    });
    return response;
  },

  getMemories: async (params = {}) => {
    // API returns MemoryPageResponse { memories: [], total, offset, limit }
    const response = await apiClient.get('/profile/me/memories', { params });
    return response;
  },

  getCandidates: (params = {}) => apiClient.get('/profile/me/memory-candidates', { params }),

  addMemory: (memory) => apiClient.post('/profile/me/memories', memory),

  confirmCandidate: (candidateId, version, patch = {}) => apiClient.post(
    `/profile/me/memory-candidates/${candidateId}/confirm`, patch,
    { headers: { 'If-Match': `"${version}"` } },
  ),

  dismissCandidate: (candidateId, version) => apiClient.delete(
    `/profile/me/memory-candidates/${candidateId}`,
    { headers: { 'If-Match': `"${version}"` } },
  ),

  editMemory: (memoryId, version, patch) => apiClient.patch(
    `/profile/me/memories/${memoryId}`, patch,
    { headers: { 'If-Match': `"${version}"` } },
  ),

  forgetMemory: (memoryId, version) => apiClient.post(
    `/profile/me/memories/${memoryId}/forget`, null,
    { headers: { 'If-Match': `"${version}"` } },
  ),

  resetContext: () => apiClient.post('/profile/me/context-reset'),

  deleteProfile: async () => {
    const response = await apiClient.delete('/profile/me');
    return response;
  }
};
