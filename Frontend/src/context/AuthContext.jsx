import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { authAPI } from '../api/auth';

const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(() => {
    if (!localStorage.getItem('accessToken')) return null;
    try { return JSON.parse(localStorage.getItem('user') || 'null'); } catch { return null; }
  });
  const [isAuthenticated, setIsAuthenticated] = useState(() => !!localStorage.getItem('accessToken'));
  const loading = false;

  useEffect(() => {
    const onExpired = () => {
      setUser(null);
      setIsAuthenticated(false);
    };
    window.addEventListener('auth:expired', onExpired);
    return () => window.removeEventListener('auth:expired', onExpired);
  }, []);

  const storeSession = useCallback((data) => {
    if (!data?.accessToken || !data?.user) throw new Error('Invalid sign-in response.');
    localStorage.setItem('accessToken', data.accessToken);
    if (data.refreshToken) localStorage.setItem('refreshToken', data.refreshToken);
    else localStorage.removeItem('refreshToken');
    localStorage.setItem('user', JSON.stringify(data.user));
    setUser(data.user);
    setIsAuthenticated(true);
  }, []);

  const login = async (email, password) => {
    storeSession(await authAPI.login(email, password));
  };

  const register = async (email, password, displayName) => {
    storeSession(await authAPI.register(email, password, displayName));
  };

  const googleLogin = useCallback(async ({ ticket, token }) => {
    if (ticket) {
      storeSession(await authAPI.exchangeOauthTicket(ticket));
      return;
    }
    const cleanToken = token?.trim();
    if (!cleanToken) throw new Error('No token received');

    localStorage.removeItem('refreshToken');
    localStorage.setItem('accessToken', cleanToken);
    try {
      const profileData = await authAPI.getProfile();
      const mappedUser = {
        id: profileData.userId,
        email: profileData.email || 'Google User',
        displayName: profileData.preferredName
      };
      localStorage.setItem('user', JSON.stringify(mappedUser));
      setUser(mappedUser);
      setIsAuthenticated(true);
    } catch {
      localStorage.removeItem('accessToken');
      throw new Error('Failed to fetch user profile using OAuth token');
    }
  }, [storeSession]);

  const deleteAccount = async () => {
    await authAPI.deleteAccount();
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    setUser(null);
    setIsAuthenticated(false);
    window.location.href = '/';
  };

  const logout = async () => {
    const refreshToken = localStorage.getItem('refreshToken');
    await authAPI.logout(refreshToken);
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    
    setUser(null);
    setIsAuthenticated(false);
    
    window.location.href = '/';
  };

  return (
    <AuthContext.Provider value={{ user, isAuthenticated, login, register, googleLogin, logout, deleteAccount, loading }}>
      {children}
    </AuthContext.Provider>
  );
};

// Keep the existing hook alongside its provider during the frontend migration.
// eslint-disable-next-line react-refresh/only-export-components
export const useAuth = () => useContext(AuthContext);
