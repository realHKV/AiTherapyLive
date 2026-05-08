import axios from 'axios';

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to add the standard Auth string (if any)
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken');
    // console.log('Outgoing request:', config.url, 'Token:', token ? 'present' : 'MISSING');
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor to handle token expiry / unauthenticated scenarios
apiClient.interceptors.response.use(
  (response) => {
    // console.log('Incoming response');
    const resData = response.data;
    // console.log('Response data:', resData);
    if (resData && resData.success === true) {
      // console.log('Unwrapped response data:', resData.data); // move this BEFORE return
      return resData.data;
    }
    return resData;
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      console.error('Unauthorized:', error);
      localStorage.removeItem('accessToken');
    }
    return Promise.reject(error);
  }
);

export default apiClient;
