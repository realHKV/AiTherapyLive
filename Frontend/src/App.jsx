import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import { SubscriptionProvider } from './context/SubscriptionContext';
import './index.css';
import { useTheme } from './hooks/useTheme';
import DemoScheduleBanner from './components/DemoScheduleBanner';

const Landing = lazy(() => import('./pages/Landing'));
const AboutYou = lazy(() => import('./pages/AboutYou'));
const ChooseTherapist = lazy(() => import('./pages/ChooseTherapist'));
const Home = lazy(() => import('./pages/Home'));
const Memories = lazy(() => import('./pages/Memories'));
const Chat = lazy(() => import('./pages/Chat'));
const OAuth2RedirectHandler = lazy(() => import('./pages/OAuth2RedirectHandler'));
const Privacy = lazy(() => import('./pages/Privacy'));
const Terms = lazy(() => import('./pages/Terms'));

const RouteLoading = () => (
  <div className="min-h-screen bg-background flex items-center justify-center">
    <div className="w-12 h-12 rounded-full border-4 border-outline-variant/30 border-t-primary animate-spin-slow" />
  </div>
);

// Requires authentication — redirects to landing if not logged in
const ProtectedRoute = ({ children }) => {
  const { isAuthenticated, loading } = useAuth();
  if (loading) return <RouteLoading />;
  return isAuthenticated ? children : <Navigate to="/" replace />;
};

// Redirects authenticated users from public pages to the app
const AuthRedirectRoute = ({ children }) => {
  const { isAuthenticated, loading } = useAuth();
  if (loading) return <RouteLoading />;
  return isAuthenticated ? <Navigate to="/home" replace /> : children;
};

function App() {
  // Initialize theme globally
  useTheme();

  return (
    <SubscriptionProvider>
      <BrowserRouter>
        <DemoScheduleBanner />
        <Suspense fallback={<RouteLoading />}>
        <Routes>
          {/* Public */}
          <Route path="/" element={<AuthRedirectRoute><Landing /></AuthRedirectRoute>} />
          <Route path="/login" element={<Navigate to="/" replace />} />
          <Route path="/oauth2/redirect" element={<OAuth2RedirectHandler />} />
          <Route path="/privacy" element={<Privacy />} />
          <Route path="/terms" element={<Terms />} />

          {/* Onboarding (protected) */}
          <Route path="/about-you" element={<ProtectedRoute><AboutYou /></ProtectedRoute>} />
          <Route path="/choose-therapist" element={<ProtectedRoute><ChooseTherapist /></ProtectedRoute>} />

          {/* App (protected) */}
          <Route path="/home" element={<ProtectedRoute><Home /></ProtectedRoute>} />
          <Route path="/memories" element={<ProtectedRoute><Memories /></ProtectedRoute>} />
          <Route path="/chat" element={<ProtectedRoute><Chat /></ProtectedRoute>} />

          {/* Fallback */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        </Suspense>
      </BrowserRouter>
    </SubscriptionProvider>
  );
}

export default App;
