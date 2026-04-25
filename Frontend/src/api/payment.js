import apiClient from './client';

// NOTE: apiClient's response interceptor already unwraps ApiResponse<T> — it returns
// the inner `data` payload directly when success===true. So we just return the result
// of apiClient calls directly, NOT response.data (that would be undefined).

export const paymentAPI = {
  /** GET /payment/status — returns SubscriptionStatusResponse { tier, proExpiresAt, isPro } */
  getStatus: async () => {
    return apiClient.get('/payment/status');
  },

  /** POST /payment/verify — manual verification with a Razorpay payment ID */
  verifyPayment: async (paymentId) => {
    return apiClient.post('/payment/verify', { paymentId });
  },

  // ─── Dev-only helpers ────────────────────────────────────────────────────

  /** Force-upgrades the logged-in user to PRO (no payment). Backend only active when DEV_MODE=true. */
  devForcePro: async () => {
    return apiClient.post('/dev/force-pro');
  },

  /** Force-reverts the logged-in user back to FREE. */
  devForceFree: async () => {
    return apiClient.post('/dev/force-free');
  },
};
