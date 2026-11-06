import apiClient from './client';

export const usageAPI = {
  getStatus: () => apiClient.get('/usage/status'),
};
