import { createRoot } from 'react-dom/client';
import { MemoryRouter, Routes, Route, Link, Navigate } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import { SubscriptionContext } from '../context/subscriptionStore';
import { profileAPI } from '../api/profile';
import { chatAPI } from '../api/chat';
import { usageAPI } from '../api/usage';
import apiClient from '../api/client';
import { useTheme } from '../hooks/useTheme';
import Home from '../pages/Home';
import Chat from '../pages/Chat';
import Memories from '../pages/Memories';
import ChooseTherapist from '../pages/ChooseTherapist';
import '../index.css';
import '../styles/welcome.css';

// This separate development entry never mounts the real auth/subscription providers.
// Unexpected API calls fail locally instead of reaching an account or billing service.
apiClient.defaults.adapter = async () => { throw new Error('This action is unavailable in the local UI preview.'); };
let profile = { userId: 'preview-user', preferredName: 'Jamie', displayName: 'Jamie', onboardingComplete: true, personaId: 'dr_sarah', communicationStyle: 'Gentle', ageRange: '25–34', totalSessions: 6, topicsOfConcern: ['Finding balance', 'Personal growth'], traits: [] };
let messages = [
  { id: 'welcome', senderRole: 'ASSISTANT', content: 'Welcome back, Jamie. Take your time settling in. What would you like to make space for today?' },
  { id: 'sample-user', senderRole: 'USER', content: 'I’ve had a busy week and would like a moment to slow down.' },
  { id: 'sample-assistant', senderRole: 'ASSISTANT', content: 'We can take this one thought at a time. What has been taking up the most space in your mind this week?' },
];
profileAPI.getProfile = async () => ({ ...profile });
profileAPI.updateProfile = async patch => (profile = { ...profile, ...patch });
profileAPI.saveTherapist = async personaId => (profile = { ...profile, personaId });
profileAPI.getMemories = profileAPI.getCandidates = async () => ({ memories: [], candidates: [], total: 0, offset: 0, limit: 20 });
profileAPI.getProfileCandidates = async () => [];
usageAPI.getStatus = async () => ({ tier: 'FREE', remainingRequests: 17, remainingTokens: 21000 });
const session = () => ({ id: 'preview-session', startedAt: new Date().toISOString(), recentMessages: [...messages] });
chatAPI.getActiveSession = chatAPI.createSession = async () => session();
chatAPI.endSession = async () => { messages = []; };
chatAPI.refreshProfileMidSession = async () => ({ status: 'NOT_QUEUED' });
chatAPI.createStreamSource = async (_sessionId, content, { signal } = {}) => {
  const requestId = crypto.randomUUID();
  const userMessageId = crypto.randomUUID();
  const assistantMessageId = crypto.randomUUID();
  const reply = 'This is a sample reply for testing the interface. You can watch the message appear, scroll through the conversation, and try the profile panel. No message is sent to an AI service.';
  messages.push({ id: userMessageId, senderRole: 'USER', content });
  let timer;
  let stopped = false;
  let cleanup = () => {};
  const stream = new ReadableStream({
    start(controller) {
      const send = (name, data) => controller.enqueue(new TextEncoder().encode(`event: ${name}\ndata: ${JSON.stringify({ requestId, ...data })}\n\n`));
      const stop = () => { if (!stopped) { stopped = true; clearTimeout(timer); cleanup(); controller.close(); } };
      cleanup = () => signal?.removeEventListener('abort', stop);
      signal?.addEventListener('abort', stop, { once: true });
      if (signal?.aborted) { stop(); return; }
      send('start', { userMessageId });
      const chunks = reply.match(/.{1,14}/g);
      let index = 0;
      const next = () => {
        if (stopped) return;
        if (index < chunks.length) { send('delta', { text: chunks[index++] }); timer = setTimeout(next, 65); }
        else { messages.push({ id: assistantMessageId, senderRole: 'ASSISTANT', content: reply }); send('complete', { assistantMessageId }); stop(); }
      };
      timer = setTimeout(next, 650);
    },
    cancel() { stopped = true; clearTimeout(timer); cleanup(); },
  });
  return new Response(stream, { headers: { 'Content-Type': 'text/event-stream', 'X-Request-Id': requestId } });
};
const auth = { user: { id: 'preview-user', displayName: 'Jamie' }, isAuthenticated: true, loading: false, logout: async () => { window.location.href = '/'; }, deleteAccount: async () => { throw new Error('Sample account only.'); } };
const subscription = { isPro: false, statusLoaded: true, loading: false, upgradeLoading: false, upgradeError: '', refreshStatus: async () => {}, startUpgrade: () => window.alert('Checkout is disabled in this sample preview.') };
// Standalone preview entry mounts its own root.
// eslint-disable-next-line react-refresh/only-export-components
function Preview() {
  useTheme();
  return <AuthContext.Provider value={auth}><SubscriptionContext.Provider value={subscription}>
    <MemoryRouter initialEntries={[new URLSearchParams(window.location.search).get('screen') === 'chat' ? '/chat' : '/home']}>
      <nav className="preview-toolbar"><strong>UI preview · sample data</strong><Link to="/home">Home</Link><Link to="/chat">Chat</Link><a href="/">Landing ↗</a></nav>
      <Routes><Route path="/home" element={<Home />} /><Route path="/chat" element={<Chat />} /><Route path="/memories" element={<Memories />} /><Route path="/choose-therapist" element={<ChooseTherapist />} /><Route path="*" element={<Navigate to="/home" replace />} /></Routes>
    </MemoryRouter>
  </SubscriptionContext.Provider></AuthContext.Provider>;
}
createRoot(document.getElementById('root')).render(<Preview />);
