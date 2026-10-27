import React, { useState, useEffect, useCallback, useRef } from 'react';
import { paymentAPI } from '../api/payment';
import { loadRazorpayCheckout, openRazorpayCheckout } from '../api/razorpayCheckout';
import { useAuth } from './AuthContext';
import { SubscriptionContext } from './subscriptionStore';

export const SubscriptionProvider = ({ children }) => {
  const { isAuthenticated } = useAuth();
  const [tier, setTier] = useState('FREE');
  const [proExpiresAt, setProExpiresAt] = useState(null);
  const [isPro, setIsPro] = useState(false);
  const [loading, setLoading] = useState(false);
  const [statusLoaded, setStatusLoaded] = useState(false);
  const [upgradeLoading, setUpgradeLoading] = useState(false);
  const [upgradeError, setUpgradeError] = useState('');
  const upgradeInFlight = useRef(false);

  const refreshStatus = useCallback(async () => {
    if (!isAuthenticated) return;
    setLoading(true);
    try {
      const data = await paymentAPI.getStatus();
      setTier(data.tier);
      setProExpiresAt(data.proExpiresAt);
      setIsPro(data.isPro);
      setStatusLoaded(true);
      return data;
    } catch (err) {
      setStatusLoaded(false);
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
      setStatusLoaded(false);
    }
  }, [isAuthenticated, refreshStatus]);

  const verifyAndUpgrade = useCallback(async (paymentId) => {
    const data = await paymentAPI.verifyPayment(paymentId);
    setTier(data.tier);
    setProExpiresAt(data.proExpiresAt);
    setIsPro(data.isPro);
    setStatusLoaded(true);
    return data;
  }, []);

  const startUpgrade = useCallback(async (onSuccess) => {
    if (upgradeInFlight.current) return;
    upgradeInFlight.current = true;
    setUpgradeError('');
    setUpgradeLoading(true);
    try {
      const current = await paymentAPI.getStatus();
      setTier(current.tier);
      setProExpiresAt(current.proExpiresAt);
      setIsPro(current.isPro);
      setStatusLoaded(true);
      if (current.isPro) {
        onSuccess?.(current);
        return;
      }
      const Razorpay = await loadRazorpayCheckout();
      const purchase = await paymentAPI.createPurchase();
      openRazorpayCheckout(Razorpay, purchase, async paymentId => {
        if (!paymentId) {
          setUpgradeError('Checkout did not return a payment ID. Please check your payment status.');
          return;
        }
        setUpgradeLoading(true);
        let confirmed;
        try {
          confirmed = await verifyAndUpgrade(paymentId);
          if (!confirmed.isPro) throw new Error('Payment was not confirmed.');
        } catch {
          confirmed = await refreshStatus();
          if (!confirmed?.isPro) {
            setUpgradeError('Payment verification is pending. Please check your plan status before trying again.');
            return;
          }
        } finally {
          setUpgradeLoading(false);
        }
        setUpgradeError('');
        onSuccess?.(confirmed);
      });
    } catch (error) {
      setUpgradeError(error.message || 'Checkout could not start. Please try again.');
    } finally {
      upgradeInFlight.current = false;
      setUpgradeLoading(false);
    }
  }, [verifyAndUpgrade, refreshStatus]);

  // ─── Dev-only helpers (localhost only) ─────────────────────────────────────
  const isDevMode = import.meta.env.DEV
    && (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1');

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
      statusLoaded,
      isDevMode,
      refreshStatus,
      startUpgrade,
      upgradeLoading,
      upgradeError,
      verifyAndUpgrade,
      devForcePro,
      devForceFree,
    }}>
      {children}
    </SubscriptionContext.Provider>
  );
};
