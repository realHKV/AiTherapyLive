import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { chatAPI } from '../api/chat';
import { readChatStream } from '../api/chatStream';
import { profileAPI } from '../api/profile';
import { usageAPI } from '../api/usage';
import { useAuth } from '../context/AuthContext';
import UserProfilePanel from './UserProfilePanel';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import ThemeToggle from '../components/ThemeToggle';

export default function Chat() {
  const { logout, user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [sessionId, setSessionId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const [chatError, setChatError] = useState('');
  const [chatBlocked, setChatBlocked] = useState(false);
  const [isProfileOpen, setIsProfileOpen] = useState(false);
  const [profile, setProfile] = useState(null);
  const [usage, setUsage] = useState(null);
  const [usageError, setUsageError] = useState('');
  const [isSessionStarting, setIsSessionStarting] = useState(true);
  const [loadingMessage, setLoadingMessage] = useState('Connecting to your session...');
  const [isRefreshingProfile, setIsRefreshingProfile] = useState(false);
  const [profileReviewKey, setProfileReviewKey] = useState(0);
  const [profileUpdateNotice, setProfileUpdateNotice] = useState('');
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [enable3DBackground, setEnable3DBackground] = useState(() => {
    return localStorage.getItem('enable3DBackground') === 'true';
  });
  const messagesEndRef = useRef(null);
  const activeStreamRef = useRef(null);
  const usageRequestRef = useRef(0);

  const refreshUsage = useCallback(async () => {
    const request = ++usageRequestRef.current;
    try {
      const status = await usageAPI.getStatus();
      if (request !== usageRequestRef.current) return;
      setUsage(status);
      setUsageError('');
    } catch {
      if (request !== usageRequestRef.current) return;
      setUsage(null);
      setUsageError('Usage status is temporarily unavailable.');
    }
  }, []);

  useEffect(() => { refreshUsage(); }, [refreshUsage]);

  useEffect(() => {
    if (location.state?.showUpgrade) {
      setIsProfileOpen(true);
      navigate(location.pathname, { replace: true, state: null });
    }
  }, [location.pathname, location.state, navigate]);

  useEffect(() => () => activeStreamRef.current?.abort(), []);

  const toggle3DBackground = () => {
    setEnable3DBackground(prev => {
      const next = !prev;
      localStorage.setItem('enable3DBackground', next);
      return next;
    });
  };

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  useEffect(() => {
    const initData = async () => {
      try {
        setLoadingMessage('Connecting to your session...');
        const [sessionRes, profileRes] = await Promise.all([
          chatAPI.createSession(),
          profileAPI.getProfile(),
        ]);
        
        if (profileRes && profileRes.onboardingComplete === false) {
          navigate('/about-you', { replace: true });
          return;
        }
        
        if (sessionRes && sessionRes.id) {
          setSessionId(sessionRes.id);
          setMessages(sessionRes.recentMessages || []);
        }
        if (profileRes) setProfile(profileRes);
      } catch (err) {
        setChatError(err.response?.data?.error?.message || 'Could not connect to your session. Please refresh to try again.');
      } finally {
        setIsSessionStarting(false);
      }
    };
    initData();
  }, [navigate]);

  const handleEndSession = async () => {
    if (!sessionId || isRefreshingProfile || isSessionStarting) return;
    activeStreamRef.current?.abort();
    setIsSessionStarting(true);
    setLoadingMessage('Saving your session...');
    try {
      await chatAPI.endSession(sessionId);
      try {
        const profileRes = await profileAPI.getProfile();
        if (profileRes) setProfile(profileRes);
      } catch (e) {
        console.error('Failed to fetch updated profile', e);
      }
      setSessionId(null);
      setMessages([]);
      setChatBlocked(false);
      setChatError('');
      setLoadingMessage('Starting new session...');
      const sessionRes = await chatAPI.createSession();
      if (sessionRes && sessionRes.id) {
        setSessionId(sessionRes.id);
        setMessages(sessionRes.recentMessages || []);
      }
    } catch (err) {
      setChatError(err.response?.data?.error?.message || 'Could not end the session. Please try again.');
    } finally {
      setIsSessionStarting(false);
      refreshUsage();
    }
  };

  const handleOpenProfile = async () => {
    setIsProfileOpen(true);
    try {
      const profileRes = await profileAPI.getProfile();
      if (profileRes) setProfile(profileRes);
    } catch (e) {
      console.error('Failed to refresh profile', e);
    }
  };

  const handleLogout = async () => {
    try {
      await logout();
    } catch {
      setChatError('Logout could not be confirmed. Please try again.');
    }
  };

  const handleRefreshProfile = async () => {
    if (!sessionId || isRefreshingProfile || isTyping || isSessionStarting) return;
    const finishedSessionId = sessionId;
    setIsRefreshingProfile(true);
    setChatError('');
    setProfileUpdateNotice('Saving this session and preparing profile suggestions...');
    try {
      const started = await chatAPI.refreshProfileMidSession(finishedSessionId);
      setIsSessionStarting(true);
      setLoadingMessage('Starting a new session...');
      setSessionId(null);
      setMessages([]);
      setChatBlocked(false);
      const nextSession = await chatAPI.createSession();
      if (!nextSession?.id) throw new Error('Could not start a new session. Please refresh the page.');
      setSessionId(nextSession.id);
      setMessages(nextSession.recentMessages || []);
      setIsSessionStarting(false);
      refreshUsage();

      let status = started?.status;
      for (let attempt = 0; attempt < 45 && status !== 'COMPLETED' && status !== 'FAILED' && status !== 'SUPERSEDED' && status !== 'NOT_QUEUED'; attempt += 1) {
        await new Promise(resolve => setTimeout(resolve, 2000));
        status = (await chatAPI.getExtractionStatus(finishedSessionId)).status;
      }
      if (status === 'COMPLETED') {
        const updated = await profileAPI.getProfile();
        if (updated) setProfile(updated);
        setProfileReviewKey(key => key + 1);
        setIsProfileOpen(true);
        setProfileUpdateNotice('Your profile suggestions are ready. Review and apply the ones you want to keep.');
      } else if (status === 'NOT_QUEUED') {
        setProfileUpdateNotice('There was no eligible chat content to add to your profile in that session.');
      } else if (status === 'FAILED' || status === 'SUPERSEDED') {
        setProfileUpdateNotice('Profile suggestions could not be prepared from that session. You can keep chatting and try again later.');
      } else {
        setProfileUpdateNotice('Profile suggestions are still processing. Open your profile and refresh suggestions in a moment.');
      }
    } catch (e) {
      setChatError(e.response?.data?.error?.message || e.message || 'Could not update your profile. Please try again.');
      setProfileUpdateNotice('');
    } finally {
      setIsSessionStarting(false);
      setIsRefreshingProfile(false);
    }
  };

  const handleSend = async (e) => {
    e.preventDefault();
    if (!input.trim() || !sessionId || isTyping || chatBlocked) return;
    const userMsg = { id: Date.now(), senderRole: 'USER', content: input };
    const streamMessageId = crypto.randomUUID();
    const controller = new AbortController();
    activeStreamRef.current = controller;
    setChatError('');
    setMessages(prev => [...prev, userMsg]);
    setInput('');
    setIsTyping(true);
    try {
      const response = await chatAPI.createStreamSource(sessionId, userMsg.content, { signal: controller.signal });
      let sawDelta = false;
      let completed = false;
      let terminalError = null;
      let requestId = response.headers.get('X-Request-Id');
      await readChatStream(response, (name, data) => {
        requestId = data.requestId;
        if (name === 'start') {
          setMessages(prev => prev.map(message => message.id === userMsg.id
            ? { ...message, id: data.userMessageId } : message));
        } else if (name === 'delta') {
          sawDelta = true;
          setMessages(prev => {
            const existing = prev.findIndex(message => message.id === streamMessageId);
            if (existing === -1) return [...prev, { id: streamMessageId, senderRole: 'ASSISTANT', content: data.text, isStreaming: true }];
            return prev.map(message => message.id === streamMessageId
              ? { ...message, content: message.content + data.text } : message);
          });
        } else if (name === 'complete') {
          completed = true;
          setMessages(prev => prev.map(message => message.id === streamMessageId
            ? { ...message, id: data.assistantMessageId, isStreaming: false } : message));
        } else if (name === 'error') {
          terminalError = data.message || 'The AI service could not complete this request.';
          setMessages(prev => prev.filter(message => message.id !== streamMessageId));
        }
      });
      if (terminalError) setChatError(terminalError);
      if (completed && !sawDelta && requestId) {
        try {
          const status = await chatAPI.getRequestStatus(requestId);
          if (status.assistantMessage) setMessages(prev => [...prev, status.assistantMessage]);
          else {
            setChatError('The reply is being checked. Refresh the session before sending again.');
            setChatBlocked(true);
          }
        } catch {
          setChatError('The reply could not be loaded. Refresh the session before sending again.');
          setChatBlocked(true);
        }
      }
    } catch (err) {
      if (!controller.signal.aborted) {
        if (err.requestId) {
          try {
            const status = await chatAPI.getRequestStatus(err.requestId);
            if (status.status === 'completed' && status.assistantMessage) {
              setMessages(prev => [...prev.filter(message => message.id !== streamMessageId), status.assistantMessage]);
            } else {
              setMessages(prev => prev.filter(message => message.id !== streamMessageId));
              setChatError(status.status === 'failed'
                ? 'The AI service could not complete this request.'
                : 'The request is still being checked. Refresh the session before sending again.');
              if (status.status !== 'failed') setChatBlocked(true);
            }
          } catch {
            setChatError('The connection ended before the result could be confirmed. Refresh before sending again.');
            setChatBlocked(true);
          }
        } else {
          if (err.status === 429) {
            const reset = err.resetAt || usage?.resetAt;
            const resetTime = reset && !Number.isNaN(Date.parse(reset))
              ? ` Please try again after ${new Date(reset).toLocaleString()}.` : '';
            const waitTime = err.retryAfter && Number.isFinite(Number(err.retryAfter))
              ? ` Please try again in ${err.retryAfter} seconds.` : '';
            setChatError(err.message + (err.code === 'RATE_LIMITED' && err.retryAfter ? waitTime : resetTime));
          } else if (err.code === 'ACCOUNTING_UNAVAILABLE') {
            setChatError('Usage accounting is temporarily unavailable. Please try again later.');
          } else {
            setChatError(err.message || 'Could not send your message.');
          }
          setMessages(prev => prev.filter(message => message.id !== userMsg.id));
          setInput(userMsg.content);
        }
      }
    } finally {
      if (activeStreamRef.current === controller) activeStreamRef.current = null;
      setIsTyping(false);
      refreshUsage();
    }
  };

  const preprocessMarkdown = (text) => {
    if (!text) return text;
    return text
      // Fix jammed sentences after lowercase punctuation (e.g., "listen.As" -> "listen. As")
      .replace(/([a-z][.?!])([A-Z])/g, '$1 $2')
      // Fix jammed sentences after bold markdown finishes (e.g., "support**As" -> "support** As")
      .replace(/([a-zA-Z0-9.?!;'"”]\*\*)([a-zA-Z\d])/g, '$1 $2')
      .replace(/([.?!:])(\s*)(\d+\.\s)/g, '$1\n\n$3')
      .replace(/(\d+\.\s\*\*)/g, '\n\n$1')
      .replace(/(Remember,)/g, '\n\n$1')
      .replace(/\n{3,}/g, '\n\n');
  };

  return (
    <div className="flex flex-col h-[100dvh] bg-background text-on-surface relative overflow-hidden">
      {enable3DBackground ? (
          <div className="absolute inset-0 z-0 bg-surface">
            {/* Desktop: Animated 3D Galaxy */}
            <iframe 
              src="https://my.spline.design/galaxy-1G5qZ7rvP9fNeubiNbAU1an2/" 
              frameBorder="0" 
              width="100%" 
              height="100%"
              title="Spline 3D ModelDesktop"
              className="hidden md:block w-full h-full pointer-events-none transition-all duration-1000 invert hue-rotate-180 brightness-[1.2] contrast-[1.1] dark:invert-0 dark:hue-rotate-0 dark:brightness-100 dark:contrast-100 opacity-60"
            ></iframe>
            
            {/* Mobile: Static Fallback Galaxy */}
            <div className="md:hidden w-full h-full relative overflow-hidden bg-surface dark:bg-[#060e20]">
              <div className="absolute top-10 -left-10 w-72 h-72 bg-[#cc97ff]/15 rounded-full blur-[60px]"></div>
              <div className="absolute top-[50%] -right-10 w-72 h-72 bg-[#53ddfc]/10 rounded-full blur-[70px]"></div>
              <div className="absolute bottom-20 left-[20%] w-60 h-60 bg-[#cc97ff]/10 rounded-full blur-[60px]"></div>
            </div>
          </div>
      ) : (
        <>
          <div className="fixed top-[20%] -left-20 w-[400px] h-[400px] bg-primary/5 rounded-full blur-[120px] pointer-events-none z-0" />
          <div className="fixed bottom-[10%] -right-20 w-[400px] h-[400px] bg-secondary/5 rounded-full blur-[120px] pointer-events-none z-0" />
        </>
      )}

      {/* ── Nav ── */}
      <header className="glass-morphism-nav border-b border-outline-variant/10 z-50 relative">
        <div className="flex justify-between items-center px-4 md:px-8 py-3 md:py-4 max-w-7xl mx-auto">
          {/* Logo + session info */}
          <div className="flex items-center gap-4">
            <span className="text-xl font-bold tracking-tight text-primary font-headline">TrustTherapy.ai</span>
            <div className="hidden md:flex items-center gap-2 glass-morphism rounded-full px-4 py-1.5 border border-outline-variant/10">
              <span className="flex h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
              <span className="text-sm text-on-surface-variant tracking-wide font-medium">Session Active</span>
            </div>
          </div>

          {/* Controls */}
          <div className="flex items-center gap-2 md:gap-3">
            {/* User name */}
            <span className="hidden md:block text-sm text-on-surface-variant">
              {user?.displayName || user?.email}
            </span>

            {/* Update Profile button */}
            {sessionId && !isSessionStarting && (
              <button
                onClick={handleRefreshProfile}
                disabled={isRefreshingProfile || isTyping}
                title="Finish this session and prepare profile suggestions for review"
                className="hidden md:flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold border border-primary/30 text-primary hover:bg-primary/10 transition-all disabled:opacity-50 disabled:cursor-wait"
              >
                <span className="text-sm">🔃</span>
                {isRefreshingProfile ? 'Updating...' : 'Update Profile'}
              </button>
            )}

            {/* End Session button */}
            {sessionId && !isSessionStarting && (
              <button
                onClick={handleEndSession}
                disabled={isRefreshingProfile || isTyping}
                title="End current session and start a new one"
                className="hidden md:flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold border border-error/30 text-error hover:bg-error/10 transition-all"
              >
                <span className="text-sm">🔄</span>
                New Session
              </button>
            )}

            {/* Profile button */}
            <button
              onClick={handleOpenProfile}
              title="View Patient Profile"
              className="hidden md:flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold glass-morphism border border-outline-variant/20 text-on-surface-variant hover:text-primary transition-all hover:scale-105"
            >
              <span className="text-sm">🧠</span>
              View Profile
            </button>

            <ThemeToggle />

            {/* Mobile Menu Toggle */}
            <button
              onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
              className="md:hidden p-2 -mr-2 text-on-surface-variant hover:text-on-surface transition-colors"
            >
              <span className="material-symbols-outlined">menu</span>
            </button>

            {/* Logout */}
            <button
              onClick={handleLogout}
              className="hidden md:block px-4 py-1.5 rounded-full text-sm font-semibold glass-morphism border border-outline-variant/20 text-on-surface-variant hover:text-on-surface transition-all"
            >
              Logout
            </button>
          </div>
        </div>

        {/* Mobile Dropdown */}
        {isMobileMenuOpen && (
          <div className="absolute top-full left-0 w-full bg-surface shadow-xl border-b border-outline-variant/10 md:hidden flex flex-col p-6 gap-3 z-[60] animate-fade-slide">
            <span className="text-[10px] uppercase font-bold text-on-surface-variant mb-1 tracking-widest">Controls</span>
            {sessionId && !isSessionStarting && (
              <>
                <button
                  onClick={() => { handleRefreshProfile(); setIsMobileMenuOpen(false); }}
                  disabled={isRefreshingProfile || isTyping}
                  className="flex items-center gap-3 px-4 py-3.5 rounded-xl bg-surface-container-low hover:bg-primary/5 text-primary text-sm font-semibold text-left transition-colors border border-outline-variant/10"
                >
                  <span className="text-lg">🔃</span> Finish session & update profile
                </button>
                <button
                  onClick={() => { handleOpenProfile(); setIsMobileMenuOpen(false); }}
                  className="flex items-center gap-3 px-4 py-3.5 rounded-xl bg-surface-container-low hover:bg-secondary/5 text-secondary text-sm font-semibold text-left transition-colors border border-outline-variant/10"
                >
                  <span className="text-lg">🧠</span> View Profile
                </button>
                <button
                  onClick={() => { handleEndSession(); setIsMobileMenuOpen(false); }}
                  disabled={isRefreshingProfile || isTyping}
                  className="flex items-center gap-3 px-4 py-3.5 rounded-xl bg-surface-container-low hover:bg-error/5 text-error text-sm font-semibold text-left transition-colors border border-outline-variant/10"
                >
                  <span className="text-lg">🔄</span> Start New Session
                </button>
              </>
            )}
            <button
              onClick={() => { handleLogout(); setIsMobileMenuOpen(false); }}
              className="flex items-center gap-3 px-4 py-3.5 rounded-xl bg-surface-container hover:bg-surface-container-high text-on-surface text-sm font-semibold text-left mt-2 border border-outline-variant/20"
            >
              <span className="material-symbols-outlined text-lg">logout</span> Logout
            </button>
          </div>
        )}
      </header>

      {/* ── Chat Body ── */}
      <div className="flex-1 flex flex-col overflow-hidden relative z-10">
        {isSessionStarting ? (
          /* Loading State */
          <div className="flex-1 flex flex-col items-center justify-center gap-6">
            <div className="w-12 h-12 rounded-full border-4 border-outline-variant/30 border-t-primary animate-spin-slow" />
            <p className="text-on-surface-variant font-medium text-lg animate-pulse">{loadingMessage}</p>
          </div>
        ) : (
          <>
            {/* ── Messages Area ── */}
            <div className="flex-1 overflow-y-auto custom-scrollbar space-y-8 px-4 md:px-12 lg:px-24 py-8 pb-4">
              {/* Date divider */}
              <div className="flex justify-center">
                <span className="text-[10px] font-bold text-on-surface-variant/40 tracking-[0.2em] uppercase">
                  {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}
                </span>
              </div>

              {messages.map((msg, idx) => (
                <div
                  key={msg.id || idx}
                  className={`flex flex-col max-w-[85%] md:max-w-[70%] ${
                    msg.senderRole === 'USER' ? 'items-end self-end ml-auto' : 'items-start self-start mr-auto'
                  }`}
                >
                  {msg.senderRole === 'USER' ? (
                    /* User bubble */
                    <>
                      <div className="bg-surface-container-high px-4 py-3 md:px-6 md:py-4 rounded-xl rounded-tr-none shadow-[0_10px_30px_rgba(0,0,0,0.15)] border-r-2 border-primary/20">
                        <p className="text-on-surface leading-relaxed break-words">{msg.content}</p>
                      </div>
                      <span className="mt-1.5 mr-1 text-[10px] text-on-surface-variant/50 font-medium">
                        You
                      </span>
                    </>
                  ) : (
                    /* AI bubble */
                    <>
                      <div className="glass-morphism px-4 py-3 md:px-6 md:py-4 rounded-xl rounded-tl-none border border-outline-variant/10 shadow-[0_10px_40px_rgba(0,0,0,0.12)] relative overflow-hidden">
                        <div className="absolute -top-8 -right-8 w-20 h-20 bg-primary/5 blur-2xl" />
                        <div className="ai-prose relative z-10 w-full overflow-hidden">
                          <ReactMarkdown remarkPlugins={[remarkGfm]}>
                            {preprocessMarkdown(msg.content)}
                          </ReactMarkdown>
                        </div>
                      </div>
                      <span className="mt-1.5 ml-1 text-[10px] text-on-surface-variant/50 font-medium">
                        AI Guide
                      </span>
                    </>
                  )}
                </div>
              ))}

              {/* Typing indicator */}
              {isTyping && (
                <div className="flex items-center gap-2 ml-2 mr-auto">
                  <div className="flex gap-1.5 px-4 py-3 rounded-full glass-morphism border border-outline-variant/10">
                    <span className="w-2 h-2 rounded-full bg-primary/50 animate-bounce" style={{ animationDelay: '-0.3s' }} />
                    <span className="w-2 h-2 rounded-full bg-primary/50 animate-bounce" style={{ animationDelay: '-0.15s' }} />
                    <span className="w-2 h-2 rounded-full bg-primary/50 animate-bounce" />
                  </div>
                </div>
              )}

              <div ref={messagesEndRef} />
            </div>

            {/* ── Input Bar ── */}
            <div className="p-3 md:p-6 lg:p-8 pointer-events-none relative z-20">
              {chatError && <p role="alert" className="max-w-4xl mx-auto mb-3 text-sm text-error pointer-events-auto">{chatError}</p>}
              {profileUpdateNotice && <p role="status" className="max-w-4xl mx-auto mb-3 text-sm text-primary pointer-events-auto">{profileUpdateNotice}</p>}
              {usage && <p className="max-w-4xl mx-auto mb-2 text-xs text-on-surface-variant pointer-events-auto">
                {usage.tier} · {usage.remainingRequests} daily requests and {usage.remainingTokens.toLocaleString()} tokens remaining
                {usage.resetAt && !Number.isNaN(Date.parse(usage.resetAt)) && ` · Resets ${new Date(usage.resetAt).toLocaleString()}`}
              </p>}
              <form onSubmit={handleSend} className="max-w-4xl mx-auto pointer-events-auto">
                <div className="glass-morphism p-1.5 md:p-2 rounded-full border border-outline-variant/20 shadow-glass flex items-center gap-2 md:gap-3">
                  <input
                    type="text"
                    value={input}
                    onChange={e => setInput(e.target.value)}
                    placeholder="Share your thoughts..."
                    disabled={isTyping || chatBlocked || !sessionId}
                    className="flex-1 min-w-0 bg-transparent border-none outline-none text-on-surface placeholder:text-on-surface-variant/40 px-3 md:px-4 font-medium text-[15px] md:text-base disabled:cursor-not-allowed"
                  />
                  <button
                    type="submit"
                    disabled={!input.trim() || isTyping || chatBlocked || !sessionId}
                    className="h-10 md:h-11 px-5 md:px-7 flex-shrink-0 flex items-center justify-center rounded-full bg-primary text-on-primary font-bold shadow-[0_0_30px_rgba(204,151,255,0.35)] hover:scale-105 active:scale-95 transition-all disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:scale-100"
                  >
                    Send
                  </button>
                </div>
                <p className="text-center mt-3 text-[10px] text-on-surface-variant/30 font-bold uppercase tracking-[0.3em]">
                  Encrypted in transit and sensitive fields protected at rest
                </p>
              </form>
            </div>
          </>
        )}
      </div>

      {/* ── Floating Side Actions ── */}
      <aside className="fixed right-6 top-1/2 -translate-y-1/2 hidden xl:flex flex-col gap-3 z-40">
        <button
          onClick={handleOpenProfile}
          className="w-12 h-12 flex items-center justify-center rounded-2xl glass-morphism border border-outline-variant/10 text-on-surface-variant hover:text-tertiary transition-all hover:scale-110 group relative"
          title="View Profile"
        >
          🧠
          <span className="absolute right-full mr-3 px-3 py-1 rounded bg-surface-container-high text-xs font-bold opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap">Profile</span>
        </button>
        {sessionId && !isSessionStarting && (
          <>
            <button
              onClick={handleRefreshProfile}
              disabled={isRefreshingProfile || isTyping}
              className="w-12 h-12 flex items-center justify-center rounded-2xl glass-morphism border border-outline-variant/10 text-on-surface-variant hover:text-secondary transition-all hover:scale-110 group relative disabled:opacity-50"
              title="Finish this session and prepare profile suggestions for review"
            >
              🔃
              <span className="absolute right-full mr-3 px-3 py-1 rounded bg-surface-container-high text-xs font-bold opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap">Update Profile</span>
            </button>
            <button
              onClick={handleEndSession}
              disabled={isRefreshingProfile || isTyping}
              className="w-12 h-12 flex items-center justify-center rounded-2xl glass-morphism border border-outline-variant/10 text-on-surface-variant hover:text-error transition-all hover:scale-110 group relative"
              title="New Session"
            >
              🔄
              <span className="absolute right-full mr-3 px-3 py-1 rounded bg-surface-container-high text-xs font-bold opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap text-error">New Session</span>
            </button>
          </>
        )}
      </aside>

      {/* ── Profile Panel ── */}
      <UserProfilePanel
        isOpen={isProfileOpen}
        onClose={() => setIsProfileOpen(false)}
        profile={profile}
        profileReviewKey={profileReviewKey}
        onProfileChanged={setProfile}
        onProfileDeleted={() => navigate('/about-you', { replace: true })}
        usage={usage}
        usageError={usageError}
        onRefreshUsage={refreshUsage}
        enable3DBackground={enable3DBackground}
        onToggle3DBackground={toggle3DBackground}
      />
    </div>
  );
}
