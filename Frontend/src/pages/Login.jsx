import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    try {
      await login(email, password);
      navigate('/chat');
    } catch (err) {
      setError(err?.response?.data?.error?.message || err.message || 'Login failed');
    }
  };

  return (
    <div className="auth-container">
      <h2>Welcome Back</h2>
      {error && <div className="error-message">{error}</div>}
      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label>Email</label>
          <input 
            type="email" 
            value={email} 
            onChange={e => setEmail(e.target.value)} 
            required 
          />
        </div>
        <div className="form-group">
          <label>Password</label>
          <input 
            type="password" 
            value={password} 
            onChange={e => setPassword(e.target.value)} 
            required 
          />
        </div>
        <button type="submit">Login</button>
      </form>

      <div style={{ textAlign: 'center', margin: '20px 0', borderTop: '1px solid #e5e7eb', paddingTop: '20px' }}>
        <button 
          onClick={() => window.location.href = `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'}/oauth2/authorization/google`}
          style={{ background: '#db4437', color: 'white' }}
        >
          Sign in with Google
        </button>
      </div>

      <p>Don't have an account? <Link to="/register">Register here</Link></p>
    </div>
  );
}
