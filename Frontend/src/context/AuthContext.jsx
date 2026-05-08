import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { authAPI } from '../api/auth';

const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Simple check for saved token
    const token = localStorage.getItem('accessToken');
    const storedUser = localStorage.getItem('user');
    
    if (token) {
      if (storedUser) {
        setUser(JSON.parse(storedUser));
      }
      setIsAuthenticated(true);
    }
    setLoading(false);
  }, []);

  const login = async (email, password) => {
    const data = await authAPI.login(email, password);
    // Remember wrapper gives { success, data, error }
    // We expect the auth.js to return the `data` portion directly, but let's be careful.
    // Actually in auth.js we typed: return response.data;
    // So `data` here IS the AuthResponse directly, if our client interceptor assumption holds.
    // Let's assume it IS the auth response: { accessToken, refreshToken, user }
    
    if (data && data.accessToken) {
      localStorage.setItem('accessToken', data.accessToken);
      if (data.refreshToken) localStorage.setItem('refreshToken', data.refreshToken);
      localStorage.setItem('user', JSON.stringify(data.user));
      
      setUser(data.user);
      setIsAuthenticated(true);
    } else {
        // If the structure is wrapped, we might hit data.data.accessToken
        throw new Error('Invalid response structure');
    }
  };

  const register = async (email, password, displayName) => {
    const data = await authAPI.register(email, password, displayName);
    if (data && data.accessToken) {
      localStorage.setItem('accessToken', data.accessToken);
      if (data.refreshToken) localStorage.setItem('refreshToken', data.refreshToken);
      localStorage.setItem('user', JSON.stringify(data.user));
      
      setUser(data.user);
      setIsAuthenticated(true);
    } else {
      throw new Error('Registration failed or invalid response structure');
    }
  };

  const googleLogin = useCallback(async (token) => {
    const cleanToken = token?.trim();
    if (!cleanToken) throw new Error('No token received');

    localStorage.setItem('accessToken', cleanToken);
    // console.log('=== RECEIVED TOKEN ===');
    // console.log(cleanToken);
    // console.log('Parts:', cleanToken.split('.').length); // must be 3
    // console.log('=== END TOKEN ===');
    
    try {
      // console.log('Token set:', localStorage.getItem('accessToken'));
      console.log('About to call /profile/me');

      const profileData = await authAPI.getProfile();
      const mappedUser = {
        id: profileData.userId,
        email: profileData.email || 'Google User',
        displayName: profileData.preferredName
      };
      localStorage.setItem('user', JSON.stringify(mappedUser));
      setUser(mappedUser);
      setIsAuthenticated(true);
    } catch (err) {
      localStorage.removeItem('accessToken');
      throw new Error('Failed to fetch user profile using OAuth token');
    }
  }, []); 

  const deleteAccount = async () => {
    try {
      await authAPI.deleteAccount();
    } catch (e) {
      console.error('Failed to delete account on server', e);
      throw e;
    } finally {
      // Regardless of failure, try to clear local state to protect user privacy
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      localStorage.removeItem('user');
      setUser(null);
      setIsAuthenticated(false);
      
      // Force a hard page reload to clear JS heap memory and history stack
      window.location.href = '/';
    }
  };

  const logout = () => {
    const refreshToken = localStorage.getItem('refreshToken');
    if (refreshToken) {
        authAPI.logout(refreshToken).catch(console.error);
    }
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    
    setUser(null);
    setIsAuthenticated(false);
    
    // Force a hard page reload to clear JS heap memory and history stack
    window.location.href = '/';
  };

  return (
    <AuthContext.Provider value={{ user, isAuthenticated, login, register, googleLogin, logout, deleteAccount, loading }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
