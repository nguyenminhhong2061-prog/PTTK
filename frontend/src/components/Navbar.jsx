import { useNavigate } from 'react-router-dom';

export default function Navbar() {
  const navigate = useNavigate();
  const user = JSON.parse(sessionStorage.getItem('quizUser') || 'null');

  const handleLogout = () => {
    sessionStorage.removeItem('quizUser');
    navigate('/');
  };

  if (!user) return null;

  const initials = user.id.slice(0, 2).toUpperCase();
  const roleLabel = user.role === 'student' ? '👨‍🎓 Học Sinh' : '👨‍🏫 Giáo Viên';

  return (
    <nav className="navbar">
      <div className="container">
        {/* Logo */}
        <a className="navbar-logo" onClick={() => navigate(user.role === 'student' ? '/student' : '/teacher')} style={{ cursor: 'pointer' }}>
          <span className="navbar-logo-icon">🎓</span>
          <span className="navbar-logo-text">QuizApp</span>
        </a>

        {/* User info + logout */}
        <div className="navbar-user">
          <div style={{ textAlign: 'right', lineHeight: 1.3 }}>
            <div style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-primary)' }}>{user.id}</div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{roleLabel}</div>
          </div>
          <div className="navbar-avatar">{initials}</div>
          <button className="btn btn-ghost btn-sm" onClick={handleLogout} title="Đăng xuất">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
              <polyline points="16 17 21 12 16 7" />
              <line x1="21" y1="12" x2="9" y2="12" />
            </svg>
            Thoát
          </button>
        </div>
      </div>
    </nav>
  );
}
