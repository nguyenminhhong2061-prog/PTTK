import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

export default function HomePage() {
  const navigate = useNavigate();
  const [modal, setModal] = useState(null); // null | 'student' | 'teacher'
  const [inputId, setInputId] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleRoleSelect = (role) => {
    setModal(role);
    setInputId('');
    setError('');
  };

  const handleLogin = async (e) => {
    e.preventDefault();
    const trimmed = inputId.trim();
    if (!trimmed) { setError('Vui lòng nhập thông tin.'); return; }
    if (modal === 'student' && !/^[A-Za-z0-9]{6,20}$/.test(trimmed)) {
      setError('Mã sinh viên không hợp lệ (ví dụ: B20DCCN001)');
      return;
    }
    setLoading(true);
    setError('');
    // Lưu user vào sessionStorage
    const user = { role: modal, id: trimmed.toUpperCase() };
    sessionStorage.setItem('quizUser', JSON.stringify(user));
    await new Promise(r => setTimeout(r, 400)); // Hiệu ứng loading
    navigate(modal === 'student' ? '/student' : '/teacher');
  };

  return (
    <div style={{ minHeight: '100vh', position: 'relative', overflow: 'hidden', background: 'var(--bg-primary)' }}>
      {/* Animated background blobs */}
      <div style={blobStyle(1)} />
      <div style={blobStyle(2)} />
      <div style={blobStyle(3)} />

      {/* Content */}
      <div style={{ position: 'relative', zIndex: 1, minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '40px 24px' }}>

        {/* Header */}
        <div style={{ textAlign: 'center', marginBottom: '64px', animation: 'fadeIn 0.8s ease' }}>
          <div style={{ fontSize: '4rem', marginBottom: '16px' }}>🎓</div>
          <h1 style={{ fontSize: '2.75rem', fontWeight: 800, marginBottom: '12px', background: 'linear-gradient(135deg, #a5b4fc, #c4b5fd, #f9a8d4)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
            Hệ Thống Thi Trắc Nghiệm
          </h1>
          <p style={{ fontSize: '1.1rem', color: 'var(--text-secondary)', maxWidth: '480px', margin: '0 auto' }}>
            Nền tảng tổ chức thi và học tập trực tuyến hiện đại
          </p>
        </div>

        {/* Role cards */}
        <div style={{ display: 'flex', gap: '32px', flexWrap: 'wrap', justifyContent: 'center', animation: 'slideUp 0.8s ease 0.2s both' }}>
          <RoleCard
            icon="👨‍🏫"
            title="Giáo Viên"
            description="Tạo đề thi, quản lý câu hỏi và theo dõi kết quả học sinh"
            features={['Tạo và quản lý đề thi', 'Xem thống kê kết quả', 'Quản lý ngân hàng câu hỏi']}
            gradient="linear-gradient(135deg, #6366f1, #8b5cf6)"
            shadow="rgba(99,102,241,0.4)"
            onClick={() => handleRoleSelect('teacher')}
          />
          <RoleCard
            icon="👨‍🎓"
            title="Học Sinh"
            description="Tham gia làm bài thi và xem kết quả ngay sau khi nộp bài"
            features={['Xem danh sách đề thi', 'Làm bài với đồng hồ đếm ngược', 'Xem điểm và kết quả chi tiết']}
            gradient="linear-gradient(135deg, #0ea5e9, #6366f1)"
            shadow="rgba(14,165,233,0.4)"
            onClick={() => handleRoleSelect('student')}
          />
        </div>

        {/* Footer */}
        <p style={{ marginTop: '64px', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
          PTIT · Phát Triển Phần Mềm Hướng Dịch Vụ · 2026
        </p>
      </div>

      {/* Modal */}
      {modal && (
        <div className="modal-overlay" onClick={() => setModal(null)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <div style={{ textAlign: 'center', marginBottom: '28px' }}>
              <div style={{ fontSize: '2.5rem', marginBottom: '12px' }}>
                {modal === 'student' ? '👨‍🎓' : '👨‍🏫'}
              </div>
              <h2 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '8px' }}>
                {modal === 'student' ? 'Đăng Nhập Học Sinh' : 'Đăng Nhập Giáo Viên'}
              </h2>
              <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
                {modal === 'student'
                  ? 'Nhập mã sinh viên của bạn để bắt đầu'
                  : 'Nhập tên hoặc mã giáo viên của bạn'}
              </p>
            </div>

            <form onSubmit={handleLogin}>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '0.9rem', color: 'var(--text-secondary)', fontWeight: 500 }}>
                {modal === 'student' ? 'Mã Sinh Viên' : 'Mã / Tên Giáo Viên'}
              </label>
              <input
                className="input"
                type="text"
                placeholder={modal === 'student' ? 'VD: B20DCCN001' : 'VD: GV001 hoặc Nguyen Van A'}
                value={inputId}
                onChange={e => { setInputId(e.target.value); setError(''); }}
                autoFocus
                style={{ marginBottom: error ? '8px' : '24px' }}
              />
              {error && (
                <p style={{ color: 'var(--error)', fontSize: '0.85rem', marginBottom: '16px' }}>⚠ {error}</p>
              )}
              <div style={{ display: 'flex', gap: '12px' }}>
                <button type="button" className="btn btn-secondary" style={{ flex: 1 }} onClick={() => setModal(null)}>
                  Huỷ
                </button>
                <button type="submit" className="btn btn-primary" style={{ flex: 2 }} disabled={loading}>
                  {loading ? (
                    <><div className="spinner" style={{ width: 16, height: 16, borderWidth: 2 }} /> Đang vào...</>
                  ) : (
                    <> Vào {modal === 'student' ? 'Trang Học Sinh' : 'Trang Giáo Viên'} →</>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

function RoleCard({ icon, title, description, features, gradient, shadow, onClick }) {
  const [hovered, setHovered] = useState(false);
  return (
    <div
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        width: 320, padding: '36px 32px', borderRadius: 24, cursor: 'pointer',
        background: 'rgba(255,255,255,0.04)',
        border: `2px solid ${hovered ? 'rgba(129,140,248,0.4)' : 'rgba(255,255,255,0.08)'}`,
        backdropFilter: 'blur(16px)',
        transform: hovered ? 'translateY(-8px) scale(1.01)' : 'translateY(0) scale(1)',
        boxShadow: hovered ? `0 24px 64px ${shadow}` : '0 4px 24px rgba(0,0,0,0.3)',
        transition: 'all 0.3s cubic-bezier(0.4,0,0.2,1)',
      }}
    >
      {/* Icon */}
      <div style={{
        width: 72, height: 72, borderRadius: 20, margin: '0 auto 20px',
        background: gradient, display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontSize: '2rem',
        boxShadow: `0 8px 24px ${shadow}`,
        transform: hovered ? 'scale(1.1) rotate(5deg)' : 'scale(1) rotate(0)',
        transition: 'transform 0.3s ease',
      }}>
        {icon}
      </div>

      <h3 style={{ textAlign: 'center', fontSize: '1.4rem', fontWeight: 700, marginBottom: '10px', background: gradient, WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
        {title}
      </h3>
      <p style={{ textAlign: 'center', color: 'var(--text-secondary)', fontSize: '0.9rem', marginBottom: '24px', lineHeight: 1.6 }}>
        {description}
      </p>

      {/* Features */}
      <ul style={{ listStyle: 'none', marginBottom: '28px' }}>
        {features.map((f, i) => (
          <li key={i} style={{ display: 'flex', alignItems: 'center', gap: '10px', color: 'var(--text-secondary)', fontSize: '0.85rem', padding: '6px 0' }}>
            <span style={{ width: 6, height: 6, borderRadius: '50%', background: gradient, flexShrink: 0 }} />
            {f}
          </li>
        ))}
      </ul>

      {/* CTA */}
      <div style={{
        textAlign: 'center', padding: '12px 24px', borderRadius: 12,
        background: gradient, color: 'white', fontWeight: 600, fontSize: '0.95rem',
        boxShadow: `0 4px 16px ${shadow}`,
        opacity: hovered ? 1 : 0.85, transition: 'opacity 0.2s',
      }}>
        Tôi là {title} →
      </div>
    </div>
  );
}

// Background animated blobs
function blobStyle(n) {
  const configs = [
    { top: '-20%', left: '-10%', width: 600, height: 600, color: 'rgba(99,102,241,0.12)', delay: '0s', duration: '20s' },
    { top: '40%', right: '-15%', left: 'auto', width: 500, height: 500, color: 'rgba(139,92,246,0.1)', delay: '-7s', duration: '25s' },
    { bottom: '-10%', left: '20%', top: 'auto', width: 400, height: 400, color: 'rgba(14,165,233,0.08)', delay: '-14s', duration: '30s' },
  ];
  const c = configs[n - 1];
  return {
    position: 'absolute', borderRadius: '50%', filter: 'blur(80px)',
    background: c.color, width: c.width, height: c.height,
    top: c.top, left: c.left, right: c.right, bottom: c.bottom,
    animation: `float ${c.duration} ease-in-out ${c.delay} infinite`,
    pointerEvents: 'none',
  };
}
