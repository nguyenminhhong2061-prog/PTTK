import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import Navbar from '../../components/Navbar.jsx';
import { getExams } from '../../api/examApi.js';
import { getStudentSubmissions } from '../../api/submissionApi.js';

export default function StudentDashboard() {
  const navigate = useNavigate();
  const user = JSON.parse(sessionStorage.getItem('quizUser') || '{}');

  const [tab, setTab] = useState('exams'); // 'exams' | 'history'
  const [exams, setExams] = useState([]);
  const [submissions, setSubmissions] = useState([]);
  const [examMap, setExamMap] = useState({}); // examId → exam
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    setError('');
    try {
      const [examsRes, subRes] = await Promise.all([
        getExams('PUBLISHED'),
        getStudentSubmissions(user.id),
      ]);

      const examList = examsRes.data || examsRes.exams || [];
      const subList = subRes.data || subRes.submissions || [];

      setExams(examList);
      setSubmissions(subList);

      // Build map for history tab
      const map = {};
      examList.forEach(e => { map[e.id] = e; });
      setExamMap(map);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  /** Lấy trạng thái của học sinh với bài thi này */
  const getMyStatus = (examId) => {
    const sub = submissions.find(s => String(s.examId) === String(examId));
    if (!sub) return null;
    return sub;
  };

  const handleStartExam = (examId) => {
    navigate(`/student/exam/${examId}`);
  };

  const handleViewResult = (submissionId) => {
    navigate(`/student/result/${submissionId}`);
  };

  const submittedHistory = submissions.filter(s => s.status === 'SUBMITTED');

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg-primary)' }}>
      <Navbar />

      <div className="container section" style={{ maxWidth: 900 }}>
        {/* Welcome header */}
        <div style={{ marginBottom: 32, animation: 'fadeIn 0.5s ease' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 8 }}>
            <div style={{
              width: 56, height: 56, borderRadius: '50%', background: 'var(--gradient-primary)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: '1.4rem', fontWeight: 700, color: 'white',
            }}>
              {user.id?.slice(0, 2)}
            </div>
            <div>
              <h2 style={{ fontSize: '1.5rem', fontWeight: 700 }}>Chào mừng, <span style={{ color: 'var(--accent-light)' }}>{user.id}</span>!</h2>
              <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
                {submittedHistory.length > 0
                  ? `Bạn đã hoàn thành ${submittedHistory.length} bài thi.`
                  : 'Bắt đầu làm bài thi đầu tiên của bạn.'}
              </p>
            </div>
          </div>

          {/* Stats row */}
          {submissions.length > 0 && (
            <div style={{ display: 'flex', gap: 12, marginTop: 16, flexWrap: 'wrap' }}>
              <StatPill label="Đã hoàn thành" value={submittedHistory.length} color="var(--success)" />
              {submittedHistory.length > 0 && (
                <StatPill
                  label="Điểm trung bình"
                  value={`${(submittedHistory.reduce((s, x) => s + (x.score || 0), 0) / submittedHistory.length).toFixed(1)}đ`}
                  color="var(--accent)"
                />
              )}
              {submissions.find(s => s.status === 'IN_PROGRESS') && (
                <StatPill label="Đang làm" value={submissions.filter(s => s.status === 'IN_PROGRESS').length} color="var(--warning)" />
              )}
            </div>
          )}
        </div>

        {/* Tabs */}
        <div className="tabs" style={{ marginBottom: 24 }}>
          <button className={`tab-btn ${tab === 'exams' ? 'active' : ''}`} onClick={() => setTab('exams')}>
            📋 Bài Thi Có Sẵn ({exams.length})
          </button>
          <button className={`tab-btn ${tab === 'history' ? 'active' : ''}`} onClick={() => setTab('history')}>
            📊 Lịch Sử Làm Bài ({submittedHistory.length})
          </button>
        </div>

        {/* Loading */}
        {loading && (
          <div className="loading-center">
            <div className="spinner" />
            <span>Đang tải dữ liệu...</span>
          </div>
        )}

        {/* Error */}
        {!loading && error && (
          <div style={{ padding: 24, background: 'var(--error-bg)', border: '1px solid rgba(248,113,113,0.3)', borderRadius: 12, color: 'var(--error)', textAlign: 'center' }}>
            <p>⚠ Không thể kết nối đến server: {error}</p>
            <button className="btn btn-sm btn-secondary" style={{ marginTop: 12 }} onClick={loadData}>Thử lại</button>
          </div>
        )}

        {/* Tab: Danh sách bài thi */}
        {!loading && !error && tab === 'exams' && (
          <div style={{ animation: 'fadeIn 0.4s ease' }}>
            {exams.length === 0 ? (
              <EmptyState icon="📝" title="Chưa có bài thi nào" desc="Giáo viên chưa tạo bài thi hoặc chưa công bố." />
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                {exams.map(exam => {
                  const myStatus = getMyStatus(exam.id);
                  return (
                    <ExamCard
                      key={exam.id}
                      exam={exam}
                      myStatus={myStatus}
                      onStart={() => handleStartExam(exam.id)}
                      onViewResult={() => myStatus && handleViewResult(myStatus.id)}
                    />
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* Tab: Lịch sử làm bài */}
        {!loading && !error && tab === 'history' && (
          <div style={{ animation: 'fadeIn 0.4s ease' }}>
            {submittedHistory.length === 0 ? (
              <EmptyState icon="🏆" title="Chưa có lịch sử" desc="Hoàn thành bài thi để xem kết quả ở đây." />
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                {submittedHistory
                  .sort((a, b) => new Date(b.submittedAt) - new Date(a.submittedAt))
                  .map(sub => {
                    const exam = examMap[sub.examId];
                    return <HistoryCard key={sub.id} sub={sub} exam={exam} onView={() => handleViewResult(sub.id)} />;
                  })}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

/* ── Sub-components ── */

function ExamCard({ exam, myStatus, onStart, onViewResult }) {
  const submitted = myStatus?.status === 'SUBMITTED';
  const inProgress = myStatus?.status === 'IN_PROGRESS';

  return (
    <div className="exam-card" style={{ animation: 'fadeIn 0.4s ease' }}>
      <div className="exam-card-header">
        <div style={{ flex: 1 }}>
          <div className="exam-card-title">{exam.title}</div>
          {exam.description && (
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginTop: 4 }}>{exam.description}</p>
          )}
        </div>
        {submitted && <span className="badge badge-success">✓ Đã nộp</span>}
        {inProgress && <span className="badge badge-warning">⏳ Đang làm</span>}
        {!myStatus && <span className="badge badge-info">Mới</span>}
      </div>

      <div className="exam-card-meta">
        <span>⏱ {exam.durationMinutes} phút</span>
        <span>📝 {exam.totalQuestions} câu hỏi</span>
        {submitted && myStatus && (
          <span style={{ color: myStatus.score >= 50 ? 'var(--success)' : 'var(--error)', fontWeight: 600 }}>
            🏆 {myStatus.score?.toFixed(1)}đ ({myStatus.correctCount}/{myStatus.totalQuestions} đúng)
          </span>
        )}
      </div>

      <div style={{ display: 'flex', gap: 10 }}>
        {submitted ? (
          <button className="btn btn-secondary btn-sm" onClick={onViewResult}>
            📊 Xem kết quả
          </button>
        ) : inProgress ? (
          <button className="btn btn-primary btn-sm" onClick={onStart}>
            ▶ Tiếp tục thi
          </button>
        ) : (
          <button className="btn btn-primary btn-sm" onClick={onStart}>
            🚀 Bắt đầu thi
          </button>
        )}
      </div>
    </div>
  );
}

function HistoryCard({ sub, exam, onView }) {
  const passed = sub.score >= 50;
  const scorePercent = sub.score || 0;
  const date = new Date(sub.submittedAt).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' });

  return (
    <div className="card" style={{ padding: '20px 24px', display: 'flex', alignItems: 'center', gap: 20 }}>
      {/* Score ring mini */}
      <div style={{ position: 'relative', width: 56, height: 56, flexShrink: 0 }}>
        <svg width="56" height="56" viewBox="0 0 56 56">
          <circle cx="28" cy="28" r="22" fill="none" stroke="var(--border)" strokeWidth="5" />
          <circle cx="28" cy="28" r="22" fill="none"
            stroke={passed ? 'var(--success)' : 'var(--error)'}
            strokeWidth="5" strokeLinecap="round"
            strokeDasharray={`${2 * Math.PI * 22}`}
            strokeDashoffset={`${2 * Math.PI * 22 * (1 - scorePercent / 100)}`}
            transform="rotate(-90 28 28)" />
        </svg>
        <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.75rem', fontWeight: 700, color: passed ? 'var(--success)' : 'var(--error)' }}>
          {scorePercent.toFixed(0)}
        </div>
      </div>

      {/* Info */}
      <div style={{ flex: 1 }}>
        <div style={{ fontWeight: 600, marginBottom: 4 }}>{exam?.title || `Bài thi #${sub.examId}`}</div>
        <div style={{ fontSize: '0.82rem', color: 'var(--text-muted)', display: 'flex', gap: 12 }}>
          <span>{sub.correctCount}/{sub.totalQuestions} câu đúng</span>
          <span>{date}</span>
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <span className={`badge ${passed ? 'badge-success' : 'badge-error'}`}>
          {passed ? '✓ Đạt' : '✗ Chưa đạt'}
        </span>
        <button className="btn btn-ghost btn-sm" onClick={onView}>Chi tiết →</button>
      </div>
    </div>
  );
}

function StatPill({ label, value, color }) {
  return (
    <div style={{ padding: '8px 16px', background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-full)', display: 'flex', alignItems: 'center', gap: 8 }}>
      <span style={{ width: 8, height: 8, borderRadius: '50%', background: color, display: 'inline-block' }} />
      <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{label}:</span>
      <span style={{ fontSize: '0.9rem', fontWeight: 700, color }}>{value}</span>
    </div>
  );
}

function EmptyState({ icon, title, desc }) {
  return (
    <div className="empty-state">
      <div className="empty-state-icon">{icon}</div>
      <h3 style={{ fontSize: '1.1rem', color: 'var(--text-secondary)', marginBottom: 8 }}>{title}</h3>
      <p style={{ fontSize: '0.9rem' }}>{desc}</p>
    </div>
  );
}
