import apiClient, { API_BASE_URL, refreshSession, expireSession } from './client';

const requestKey = () => crypto.randomUUID();

export const chatAPI = {
  // Start a new therapy session
  createSession: async () => {
    return apiClient.post('/chat/sessions', null, {
      headers: { 'X-Chat-Request-Version': '2', 'Idempotency-Key': requestKey() },
    });
  },

  // Send a message (non-streamed)
  sendMessage: async (sessionId, content) => {
    return apiClient.post(`/chat/sessions/${sessionId}/messages`, { content }, {
      headers: { 'X-Chat-Request-Version': '2', 'Idempotency-Key': requestKey() },
    });
  },

  // End active session to trigger summarization
  endSession: async (sessionId) => {
    const response = await apiClient.put(`/chat/sessions/${sessionId}/end`);
    return response;
  },

  // Queue a snapshot of the active session for reviewable suggestions
  refreshProfileMidSession: async (sessionId) => {
    const response = await apiClient.post(`/chat/sessions/${sessionId}/refresh-profile`);
    return response;
  },
  getExtractionStatus: (sessionId, jobId) => apiClient.get(`/chat/sessions/${sessionId}/extraction-status`, {
    params: { jobId },
  }),

  getRequestStatus: (requestId) => apiClient.get(`/chat/requests/${requestId}`),

  createStreamSource: async (sessionId, content, { signal, idempotencyKey = requestKey() } = {}) => {
    const response = await fetch(`${API_BASE_URL}/chat/sessions/${sessionId}/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        'Authorization': `Bearer ${localStorage.getItem('accessToken') || ''}`,
        'X-Chat-Stream-Version': '2',
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify({ content }),
      signal,
    });
    if (!response.ok) {
      if (response.status === 401) {
        if (localStorage.getItem('refreshToken')) {
          await refreshSession();
          throw new Error('Your session was refreshed. Please send your message again.');
        }
        expireSession();
        throw new Error('Your session has expired. Please sign in again.');
      }
      const body = await response.json().catch(() => null);
      const error = new Error(body?.error?.message || `Chat request failed (${response.status}).`);
      error.code = body?.error?.code;
      error.status = response.status;
      error.retryAfter = response.headers.get('Retry-After');
      error.resetAt = response.headers.get('X-Usage-Reset-At');
      throw error;
    }
    if (!response.body || !response.headers.get('Content-Type')?.includes('text/event-stream')) {
      throw new Error('The chat server did not return a stream.');
    }
    return response;
  },
};
