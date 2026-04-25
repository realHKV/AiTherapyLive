import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import { SubscriptionProvider } from './context/SubscriptionContext';
import Landing from './pages/Landing';
import AboutYou from './pages/AboutYou';
import ChooseTherapist from './pages/ChooseTherapist';
import Chat from './pages/Chat';
import OAuth2RedirectHandler from './pages/OAuth2RedirectHandler';
import Privacy from './pages/Privacy';
import Terms from './pages/Terms';
import './index.css';
import { useTheme } from './hooks/useTheme';

// Requires authentication — redirects to landing if not logged in
const ProtectedRoute = ({ children }) => {
  const { isAuthenticated, loading } = useAuth();
  if (loading) return (
    <div className="min-h-screen bg-background flex items-center justify-center">
      <div className="w-12 h-12 rounded-full border-4 border-outline-variant/30 border-t-primary animate-spin-slow" />
    </div>
  );
  return isAuthenticated ? children : <Navigate to="/" replace />;
};

// Redirects authenticated users from public pages to the app
const AuthRedirectRoute = ({ children }) => {
  const { isAuthenticated, loading } = useAuth();
  if (loading) return (
    <div className="min-h-screen bg-background flex items-center justify-center">
      <div className="w-12 h-12 rounded-full border-4 border-outline-variant/30 border-t-primary animate-spin-slow" />
    </div>
  );
  return isAuthenticated ? <Navigate to="/chat" replace /> : children;
};

function App() {
  // Initialize theme globally
  useTheme();

  return (
    <SubscriptionProvider>
      <BrowserRouter>
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
          <Route path="/chat" element={<ProtectedRoute><Chat /></ProtectedRoute>} />

          {/* Fallback */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </SubscriptionProvider>
  );
}

export default App;
