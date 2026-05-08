import apiClient from './client';

export const chatAPI = {
  // Start a new therapy session
  createSession: async () => {
    const response = await apiClient.post('/chat/sessions');
    console.log('Response:', response);
    return response; // Inner data: ConversationResponse
  },

  // Send a message (non-streamed)
  sendMessage: async (sessionId, content) => {
    const response = await apiClient.post(`/chat/sessions/${sessionId}/messages`, { content });
    return response.data; // Inner data: ChatMessageResponse
  },

  // End active session to trigger summarization
  endSession: async (sessionId) => {
    const response = await apiClient.put(`/chat/sessions/${sessionId}/end`);
    return response;
  },

  // Sync profile without ending session
  refreshProfileMidSession: async (sessionId) => {
    const response = await apiClient.post(`/chat/sessions/${sessionId}/refresh-profile`);
    return response;
  },

  // Note: Streaming via /chat/sessions/{sessionId}/stream usually uses EventSource or fetch directly, 
  // because axios doesn't support SSE standardly in browsers.
  // We will build a helper for fetch stream.
  
  createStreamSource: (sessionId, content) => {
    // For POST with SSE, it's a bit tricky because standard EventSource only supports GET.
    // However, if the backend expects POST, we have to use fetch() and read the stream.
    return fetch(`${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}/chat/sessions/${sessionId}/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('accessToken')}`
      },
      body: JSON.stringify({ content })
    });
  }
};
