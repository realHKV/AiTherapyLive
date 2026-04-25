import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { chatAPI } from '../api/chat';
import { profileAPI } from '../api/profile';
import { useAuth } from '../context/AuthContext';
import UserProfilePanel from './UserProfilePanel';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import ThemeToggle from '../components/ThemeToggle';

export default function Chat() {
  const { logout, user } = useAuth();
  const navigate = useNavigate();
  const [sessionId, setSessionId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const [isProfileOpen, setIsProfileOpen] = useState(false);
  const [profile, setProfile] = useState(null);
  const [memories, setMemories] = useState([]);
  const [isSessionStarting, setIsSessionStarting] = useState(true);
  const [loadingMessage, setLoadingMessage] = useState('Connecting to your session...');
  const [isRefreshingProfile, setIsRefreshingProfile] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [enable3DBackground, setEnable3DBackground] = useState(() => {
    return localStorage.getItem('enable3DBackground') === 'true';
  });
  const messagesEndRef = useRef(null);

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
        const [sessionRes, profileRes, memoriesRes] = await Promise.all([
          chatAPI.createSession(),
          profileAPI.getProfile(),
          profileAPI.getMemories()
        ]);
        
        // Onboarding Guard: if the user lacks a selected therapist (aiPersona), force them to onboard
        if (profileRes && !profileRes.aiPersona) {
          navigate('/about-you', { replace: true });
          return;
        }
        
        if (sessionRes && sessionRes.id) {
          setSessionId(sessionRes.id);
          setMessages(sessionRes.recentMessages || []);
        }
        if (profileRes) setProfile(profileRes);
        if (memoriesRes && memoriesRes.memories) setMemories(memoriesRes.memories);
      } catch (err) {
        console.error('Failed to initialize chat data', err);
      } finally {
        setIsSessionStarting(false);
      }
    };
    initData();
  }, [navigate]);

  const handleEndSession = async () => {
    if (!sessionId) return;
    setIsSessionStarting(true);
    setLoadingMessage('Saving your session & updating profile...');
    try {
      await chatAPI.endSession(sessionId);
      try {
        const [profileRes, memoriesRes] = await Promise.all([
          profileAPI.getProfile(),
          profileAPI.getMemories()
        ]);
        if (profileRes) setProfile(profileRes);
        if (memoriesRes && memoriesRes.memories) setMemories(memoriesRes.memories);
      } catch (e) {
        console.error('Failed to fetch updated profile', e);
      }
      setSessionId(null);
      setMessages([]);
      setLoadingMessage('Starting new session...');
      const sessionRes = await chatAPI.createSession();
      if (sessionRes && sessionRes.id) {
        setSessionId(sessionRes.id);
        setMessages(sessionRes.recentMessages || []);
      }
    } catch (err) {
      console.error('Failed to end session', err);
    } finally {
      setIsSessionStarting(false);
    }
  };

  const handleOpenProfile = async () => {
    setIsProfileOpen(true);
    try {
      const [profileRes, memoriesRes] = await Promise.all([
        profileAPI.getProfile(),
        profileAPI.getMemories()
      ]);
      if (profileRes) setProfile(profileRes);
      if (memoriesRes && memoriesRes.memories) setMemories(memoriesRes.memories);
    } catch (e) {
      console.error('Failed to refresh profile', e);
    }
  };

  const handleRefreshProfile = async () => {
    if (!sessionId || isRefreshingProfile) return;
    setIsRefreshingProfile(true);
    try {
      await chatAPI.refreshProfileMidSession(sessionId);
      const [profileRes, memoriesRes] = await Promise.all([
        profileAPI.getProfile(),
        profileAPI.getMemories()
      ]);
      if (profileRes) setProfile(profileRes);
      if (memoriesRes && memoriesRes.memories) setMemories(memoriesRes.memories);
    } catch (e) {
      console.error('Failed to refresh profile mid-session', e);
    } finally {
      setIsRefreshingProfile(false);
    }
  };

  const readStream = async (reader, decoder) => {
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        const chunk = decoder.decode(value, { stream: true });
        const lines = chunk.split('\n');
        for (const line of lines) {
          if (line.startsWith('data:')) {
            const dataStr = line.slice(5);
            if (dataStr === '[DONE]') { setIsTyping(false); break; }
            if (dataStr) {
              if (dataStr === '[ERROR: RATE_LIMIT_EXCEEDED]') {
                setMessages(prev => {
                  const lastMsg = prev[prev.length - 1];
                  if (lastMsg && lastMsg.isStreaming) {
                    return [...prev.slice(0, -1), { ...lastMsg, content: '⚠️ Rate limit exceeded. The AI provider is receiving too many requests. Please try again in 5 seconds.' }];
                  } else {
                    return [...prev, { id: Date.now(), senderRole: 'ASSISTANT', content: '⚠️ Rate limit exceeded. Please try again in 5 seconds.', isStreaming: false }];
                  }
                });
                setIsTyping(false);
                break;
              }
              setMessages(prev => {
                const lastMsg = prev[prev.length - 1];
                if (lastMsg && lastMsg.senderRole === 'ASSISTANT' && lastMsg.isStreaming) {
                  return [...prev.slice(0, -1), { ...lastMsg, content: lastMsg.content + dataStr }];
                } else {
                  return [...prev, { id: Date.now(), senderRole: 'ASSISTANT', content: dataStr, isStreaming: true }];
                }
              });
            }
          }
        }
      }
    } catch (err) {
      console.error('Stream reading error', err);
    } finally {
      setIsTyping(false);
      setMessages(prev => {
        const lastMsg = prev[prev.length - 1];
        if (lastMsg && lastMsg.isStreaming) {
          return [...prev.slice(0, -1), { ...lastMsg, isStreaming: false }];
        }
        return prev;
      });
    }
  };

  const handleSend = async (e) => {
    e.preventDefault();
    if (!input.trim() || !sessionId) return;
    const userMsg = { id: Date.now(), senderRole: 'USER', content: input };
    setMessages(prev => [...prev, userMsg]);
    setInput('');
    setIsTyping(true);
    try {
      const response = await chatAPI.createStreamSource(sessionId, userMsg.content);
      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8');
      await readStream(reader, decoder);
    } catch (err) {
      console.error('Failed to send message', err);
      setIsTyping(false);
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
            <span className="text-xl font-bold tracking-tight text-primary font-headline">AiTherapy</span>
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
                disabled={isRefreshingProfile}
                title="Update your profile traits from this conversation"
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
              onClick={logout}
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
                  className="flex items-center gap-3 px-4 py-3.5 rounded-xl bg-surface-container-low hover:bg-primary/5 text-primary text-sm font-semibold text-left transition-colors border border-outline-variant/10"
                >
                  <span className="text-lg">🔃</span> Update Tracking
                </button>
                <button
                  onClick={() => { handleOpenProfile(); setIsMobileMenuOpen(false); }}
                  className="flex items-center gap-3 px-4 py-3.5 rounded-xl bg-surface-container-low hover:bg-secondary/5 text-secondary text-sm font-semibold text-left transition-colors border border-outline-variant/10"
                >
                  <span className="text-lg">🧠</span> View Profile
                </button>
                <button
                  onClick={() => { handleEndSession(); setIsMobileMenuOpen(false); }}
                  className="flex items-center gap-3 px-4 py-3.5 rounded-xl bg-surface-container-low hover:bg-error/5 text-error text-sm font-semibold text-left transition-colors border border-outline-variant/10"
                >
                  <span className="text-lg">🔄</span> Start New Session
                </button>
              </>
            )}
            <button
              onClick={() => { logout(); setIsMobileMenuOpen(false); }}
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
            <div className="p-4 md:p-6 lg:p-8 pointer-events-none relative z-20">
              <form onSubmit={handleSend} className="max-w-4xl mx-auto pointer-events-auto">
                <div className="glass-morphism p-2 rounded-full border border-outline-variant/20 shadow-glass flex items-center gap-3">
                  <input
                    type="text"
                    value={input}
                    onChange={e => setInput(e.target.value)}
                    placeholder="Share your thoughts..."
                    disabled={isTyping}
                    className="flex-1 bg-transparent border-none outline-none text-on-surface placeholder:text-on-surface-variant/40 px-4 font-medium text-base disabled:cursor-not-allowed"
                  />
                  <div className="flex items-center gap-2 pr-1">
                    <button
                      type="submit"
                      disabled={!input.trim() || isTyping}
                      className="h-11 px-5 md:px-7 flex items-center justify-center rounded-full bg-primary text-on-primary font-bold shadow-[0_0_30px_rgba(204,151,255,0.35)] hover:scale-105 active:scale-95 transition-all disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:scale-100"
                    >
                      Send
                    </button>
                  </div>
                </div>
                <p className="text-center mt-3 text-[10px] text-on-surface-variant/30 font-bold uppercase tracking-[0.3em]">
                  End-to-End Encrypted Session
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
              disabled={isRefreshingProfile}
              className="w-12 h-12 flex items-center justify-center rounded-2xl glass-morphism border border-outline-variant/10 text-on-surface-variant hover:text-secondary transition-all hover:scale-110 group relative disabled:opacity-50"
              title="Update Profile"
            >
              🔃
              <span className="absolute right-full mr-3 px-3 py-1 rounded bg-surface-container-high text-xs font-bold opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap">Update Profile</span>
            </button>
            <button
              onClick={handleEndSession}
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
        memories={memories}
        enable3DBackground={enable3DBackground}
        onToggle3DBackground={toggle3DBackground}
      />
    </div>
  );
}
