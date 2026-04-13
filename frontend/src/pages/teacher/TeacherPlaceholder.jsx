import { useNavigate } from 'react-router-dom';
import Navbar from '../../components/Navbar.jsx';

/**
 * Placeholder cho trang Giáo Viên.
 * Thành viên 1 (Exam Service) và Thành viên 3 (Statistics Service) sẽ implement trang này.
 */
export default function TeacherPlaceholder() {
  const navigate = useNavigate();
  const user = JSON.parse(sessionStorage.getItem('quizUser') || '{}');

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg-primary)' }}>
      <Navbar />
      <div className="container section" style={{ maxWidth: 800, textAlign: 'center', paddingTop: 80 }}>
        {/* Coming soon card */}
        <div
          className="card"
          style={{
            padding: '64px 48px',
            background: 'linear-gradient(135deg, rgba(99,102,241,0.08) 0%, rgba(139,92,246,0.08) 100%)',
            borderColor: 'rgba(129,140,248,0.3)',
            animation: 'scaleIn 0.4s ease',
          }}
        >
          <div style={{ fontSize: '4rem', marginBottom: 24 }}>👨‍🏫</div>
          <h1 style={{ fontSize: '2rem', fontWeight: 800, marginBottom: 16, background: 'var(--gradient-primary)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
            Trang Giáo Viên
          </h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '1rem', marginBottom: 8, lineHeight: 1.8 }}>
            Xin chào <strong style={{ color: 'var(--accent-light)' }}>{user.id}</strong>!
          </p>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem', marginBottom: 32, lineHeight: 1.8, maxWidth: 480, margin: '0 auto 32px' }}>
            Trang quản lý giáo viên đang được phát triển bởi{' '}
            <strong style={{ color: 'var(--accent)' }}>Thành Viên 1</strong> (Exam Service){' '}
            và <strong style={{ color: 'var(--violet)' }}>Thành Viên 3</strong> (Statistics Service).
          </p>

          {/* Roadmap */}
          <div style={{ display: 'flex', gap: 16, justifyContent: 'center', flexWrap: 'wrap', marginBottom: 40 }}>
            {[
              { icon: '📝', title: 'Quản lý đề thi', team: 'TV1', color: '#818cf8' },
              { icon: '❓', title: 'Ngân hàng câu hỏi', team: 'TV1', color: '#818cf8' },
              { icon: '📊', title: 'Thống kê kết quả', team: 'TV3', color: '#a78bfa' },
              { icon: '📈', title: 'Báo cáo học sinh', team: 'TV3', color: '#a78bfa' },
            ].map((item, i) => (
              <div
                key={i}
                style={{
                  padding: '16px 20px', borderRadius: 12, minWidth: 140,
                  background: 'var(--surface)', border: '1px solid var(--border)',
                  display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8,
                }}
              >
                <span style={{ fontSize: '1.5rem' }}>{item.icon}</span>
                <p style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-primary)' }}>{item.title}</p>
                <span style={{ fontSize: '0.72rem', padding: '2px 8px', borderRadius: 'var(--radius-full)', background: `${item.color}20`, color: item.color, fontWeight: 600 }}>
                  {item.team}
                </span>
              </div>
            ))}
          </div>

          {/* API endpoints for TV1 & TV3 */}
          <div style={{ textAlign: 'left', background: 'rgba(0,0,0,0.3)', borderRadius: 12, padding: '20px 24px', marginBottom: 32 }}>
            <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: 10, fontWeight: 600, letterSpacing: '0.05em', textTransform: 'uppercase' }}>
              API đã sẵn sàng để tích hợp:
            </p>
            {[
              'GET  /api-exam/exams             — Danh sách bài thi',
              'POST /api-exam/exams             — Tạo bài thi mới',
              'POST /api-exam/questions         — Tạo câu hỏi',
              'GET  /api-sub/submissions?examId — Xem bài nộp',
            ].map((line, i) => (
              <code key={i} style={{ display: 'block', fontSize: '0.78rem', color: 'var(--accent-light)', marginBottom: 4, fontFamily: 'monospace' }}>
                {line}
              </code>
            ))}
          </div>

          <button className="btn btn-secondary" onClick={() => navigate('/')}>
            ← Về trang chủ
          </button>
        </div>
      </div>
    </div>
  );
}
