import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { paymentAPI } from '../api/payment';
import { useAuth } from './AuthContext';

const SubscriptionContext = createContext();

export const SubscriptionProvider = ({ children }) => {
  const { isAuthenticated } = useAuth();
  const [tier, setTier] = useState('FREE');
  const [proExpiresAt, setProExpiresAt] = useState(null);
  const [isPro, setIsPro] = useState(false);
  const [loading, setLoading] = useState(false);

  const RAZORPAY_PAYMENT_LINK = 'https://rzp.io/rzp/jEBZVu6s';

  const refreshStatus = useCallback(async () => {
    if (!isAuthenticated) return;
    setLoading(true);
    try {
      const data = await paymentAPI.getStatus();
      setTier(data.tier);
      setProExpiresAt(data.proExpiresAt);
      setIsPro(data.isPro);
    } catch (err) {
      // Silently fail — defaults stay FREE
      console.error('Failed to fetch subscription status', err);
    } finally {
      setLoading(false);
    }
  }, [isAuthenticated]);

  // Fetch status whenever auth state changes (login / refresh)
  useEffect(() => {
    if (isAuthenticated) {
      refreshStatus();
    } else {
      setTier('FREE');
      setProExpiresAt(null);
      setIsPro(false);
    }
  }, [isAuthenticated, refreshStatus]);

  /** Opens the Razorpay payment link in a new tab. */
  const openUpgradeLink = () => {
    window.open(RAZORPAY_PAYMENT_LINK, '_blank', 'noopener,noreferrer');
  };

  /**
   * After completing the Razorpay payment link, the user gets a payment ID.
   * Call this to verify and upgrade the account on the backend.
   */
  const verifyAndUpgrade = useCallback(async (paymentId) => {
    const data = await paymentAPI.verifyPayment(paymentId);
    setTier(data.tier);
    setProExpiresAt(data.proExpiresAt);
    setIsPro(data.isPro);
    return data;
  }, []);

  // ─── Dev-only helpers (localhost only) ─────────────────────────────────────
  const isDevMode = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';

  const devForcePro = useCallback(async () => {
    const data = await paymentAPI.devForcePro();
    setTier(data.tier);
    setProExpiresAt(data.proExpiresAt);
    setIsPro(data.isPro);
  }, []);

  const devForceFree = useCallback(async () => {
    const data = await paymentAPI.devForceFree();
    setTier(data.tier);
    setProExpiresAt(data.proExpiresAt);
    setIsPro(data.isPro);
  }, []);

  return (
    <SubscriptionContext.Provider value={{
      tier,
      proExpiresAt,
      isPro,
      loading,
      isDevMode,
      refreshStatus,
      openUpgradeLink,
      verifyAndUpgrade,
      devForcePro,
      devForceFree,
      RAZORPAY_PAYMENT_LINK,
    }}>
      {children}
    </SubscriptionContext.Provider>
  );
};

export const useSubscription = () => useContext(SubscriptionContext);
