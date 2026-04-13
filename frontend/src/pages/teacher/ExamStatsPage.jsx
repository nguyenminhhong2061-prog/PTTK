import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import Navbar from '../../components/Navbar.jsx';
import { getExamStatistics, getExamQuestionStatistics, getStudentScores } from '../../api/statisticsApi.js';
import { getExamById } from '../../api/examApi.js';

// ─── Difficulty config ────────────────────────────────────────────────────────
const DIFF = {
  easy:   { label: 'Dễ',         color: '#34d399', bg: 'rgba(52,211,153,0.12)'  },
  medium: { label: 'Trung bình', color: '#fbbf24', bg: 'rgba(251,191,36,0.12)'  },
  hard:   { label: 'Khó',        color: '#f87171', bg: 'rgba(248,113,113,0.12)' },
};

export default function ExamStatsPage() {
  const { examId } = useParams();
  const navigate   = useNavigate();

  const [exam,         setExam]         = useState(null);
  const [overview,     setOverview]     = useState(null);
  const [qStats,       setQStats]       = useState(null);
  const [students,     setStudents]     = useState(null);
  const [loading,      setLoading]      = useState(true);
  const [error,        setError]        = useState('');
  const [activeTab,    setActiveTab]    = useState('students'); // 'students' | 'questions'

  useEffect(() => { loadAll(); }, [examId]);

  const loadAll = async () => {
    setLoading(true); setError('');
    try {
      // Tải song song: exam detail + 3 stats endpoints
      const [examRes, overviewRes, qRes, studentsRes] = await Promise.allSettled([
        getExamById(examId),
        getExamStatistics(examId),
        getExamQuestionStatistics(examId),
        getStudentScores(examId),
      ]);

      if (examRes.status === 'fulfilled') {
        const d = examRes.value;
        setExam(d.data || d);
      }
      if (overviewRes.status === 'fulfilled') {
        const d = overviewRes.value;
        setOverview(d.data || d);
      }
      if (qRes.status === 'fulfilled') {
        const d = qRes.value;
        setQStats(d.data || d);
      }
      if (studentsRes.status === 'fulfilled') {
        const d = studentsRes.value;
        setStudents(d.data || d);
      }

      // Nếu tất cả đều fail → báo lỗi
      if (overviewRes.status === 'rejected' && qRes.status === 'rejected' && studentsRes.status === 'rejected') {
        setError('Không thể tải thống kê. Có thể Statistics Service chưa chạy.');
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  // ─── Loading ──────────────────────────────────────────────────────────────
  if (loading) {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--bg-primary)' }}>
        <Navbar />
        <div className="loading-center" style={{ minHeight: 'calc(100vh - 64px)' }}>
          <div className="spinner" style={{ width: 48, height: 48 }} />
          <p>Đang tải thống kê...</p>
        </div>
      </div>
    );
  }

  // ─── Error ────────────────────────────────────────────────────────────────
  if (error) {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--bg-primary)' }}>
        <Navbar />
        <div className="container section" style={{ maxWidth: 600, textAlign: 'center', paddingTop: 80 }}>
          <div style={{ fontSize: '3rem', marginBottom: 16 }}>📉</div>
          <h2 style={{ color: 'var(--error)', marginBottom: 12 }}>Không thể tải thống kê</h2>
          <p style={{ color: 'var(--text-secondary)', marginBottom: 28 }}>{error}</p>
          <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
            <button className="btn btn-secondary" onClick={() => navigate('/teacher')}>← Về Dashboard</button>
            <button className="btn btn-primary" onClick={loadAll}>🔄 Thử lại</button>
          </div>
        </div>
      </div>
    );
  }

  const examTitle   = overview?.examTitle || exam?.title || `Bài thi #${examId}`;
  const studentList = students?.students || (Array.isArray(students) ? students : []);
  const questionList = qStats?.questions || (Array.isArray(qStats) ? qStats : []);

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg-primary)' }}>
      <Navbar />

      <div className="container section" style={{ maxWidth: 1100 }}>

        {/* ── Breadcrumb & title ── */}
        <div style={{ marginBottom: 28, animation: 'fadeIn 0.5s ease' }}>
          <button className="btn btn-ghost btn-sm" style={{ marginBottom: 12, paddingLeft: 0 }} onClick={() => navigate('/teacher')}>
            ← Về Dashboard
          </button>
          <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 }}>
            <div>
              <h1 style={{ fontSize: '1.6rem', fontWeight: 800, marginBottom: 4 }}>📊 Thống Kê Kết Quả</h1>
              <p style={{ color: 'var(--accent-light)', fontWeight: 600, fontSize: '1rem' }}>{examTitle}</p>
            </div>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
              {exam && (
                <span style={{ padding: '5px 14px', borderRadius: 'var(--radius-full)', background: 'var(--surface)', border: '1px solid var(--border)', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                  ⏱ {exam.durationMinutes} phút · {exam.totalQuestions ?? questionList.length} câu
                </span>
              )}
              <button className="btn btn-sm btn-secondary" onClick={loadAll}>🔄 Làm mới</button>
            </div>
          </div>
        </div>

        {/* ── Overview cards ── */}
        {overview ? (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: 14, marginBottom: 32, animation: 'fadeIn 0.5s ease' }}>
            <OverviewCard icon="👥" label="Đã nộp bài"    value={overview.totalSubmitted ?? 0}                              color="var(--accent)" />
            <OverviewCard icon="📐" label="Điểm TB"       value={`${(overview.averageScore ?? 0).toFixed(1)}đ`}             color="var(--violet)" />
            <OverviewCard icon="🏆" label="Điểm cao nhất" value={`${(overview.highestScore ?? 0).toFixed(1)}đ`}             color="var(--success)" />
            <OverviewCard icon="📉" label="Điểm thấp nhất" value={`${(overview.lowestScore ?? 0).toFixed(1)}đ`}             color="var(--error)" />
            <OverviewCard icon="✅" label="Tỷ lệ đạt"     value={`${(overview.passRate ?? 0).toFixed(1)}%`}                 color="var(--success)" sub={`${overview.passCount} / ${overview.totalSubmitted} em`} />
            <OverviewCard icon="⏳" label="TG TB"         value={overview.averageDurationMinutes != null ? `${overview.averageDurationMinutes.toFixed(0)} phút` : '—'} color="var(--warning)" />
          </div>
        ) : (
          <div style={{ padding: '24px', background: 'var(--surface)', borderRadius: 12, marginBottom: 32, textAlign: 'center', color: 'var(--text-muted)' }}>
            Chưa có dữ liệu thống kê tổng quan (Statistics Service có thể chưa chạy).
          </div>
        )}

        {/* ── Score Distribution ── */}
        {overview?.scoreDistribution && overview.scoreDistribution.length > 0 && (
          <div className="card" style={{ padding: 24, marginBottom: 28, animation: 'fadeIn 0.6s ease' }}>
            <h3 style={{ fontSize: '1rem', fontWeight: 700, marginBottom: 16 }}>Phân bố điểm</h3>
            <ScoreDistributionBar distribution={overview.scoreDistribution} />
          </div>
        )}

        {/* ── Tabs ── */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
          <div className="tabs" style={{ width: 'auto' }}>
            <button className={`tab-btn ${activeTab === 'students' ? 'active' : ''}`} onClick={() => setActiveTab('students')}>
              👤 Bảng điểm học sinh ({studentList.length})
            </button>
            <button className={`tab-btn ${activeTab === 'questions' ? 'active' : ''}`} onClick={() => setActiveTab('questions')}>
              ❓ Phân tích câu hỏi ({questionList.length})
            </button>
          </div>
        </div>

        {/* ── Tab: Students ── */}
        {activeTab === 'students' && (
          <div style={{ animation: 'fadeIn 0.4s ease' }}>
            {studentList.length === 0 ? (
              <EmptyState icon="👤" title="Chưa có học sinh nào nộp bài" desc="Học sinh cần nộp bài trước khi có thống kê." />
            ) : (
              <div className="card" style={{ overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
                  <thead>
                    <tr style={{ background: 'rgba(129,140,248,0.07)', borderBottom: '1px solid var(--border)' }}>
                      {['#', 'Học Sinh', 'Điểm', 'Đúng/Tổng', 'Kết Quả', 'Thời Gian', 'Nộp Lúc'].map(h => (
                        <th key={h} style={{ padding: '12px 16px', textAlign: 'left', fontWeight: 600, color: 'var(--text-secondary)', fontSize: '0.8rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {studentList.map((s, i) => {
                      const passed = s.passed;
                      const isTop3 = s.rank <= 3;
                      return (
                        <tr key={s.studentId} style={{ borderBottom: '1px solid rgba(255,255,255,0.04)', transition: 'background 0.15s' }}
                          onMouseEnter={e => e.currentTarget.style.background = 'rgba(255,255,255,0.03)'}
                          onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
                        >
                          {/* Rank */}
                          <td style={{ padding: '12px 16px' }}>
                            <span style={{ fontWeight: 700, color: isTop3 ? ['#fbbf24','#94a3b8','#c97c3f'][s.rank-1] : 'var(--text-muted)', fontSize: isTop3 ? '1rem' : '0.85rem' }}>
                              {isTop3 ? ['🥇','🥈','🥉'][s.rank-1] : `#${s.rank}`}
                            </span>
                          </td>
                          {/* Student ID */}
                          <td style={{ padding: '12px 16px', fontWeight: 600, color: 'var(--text-primary)' }}>{s.studentId}</td>
                          {/* Score */}
                          <td style={{ padding: '12px 16px' }}>
                            <span style={{ fontSize: '1.05rem', fontWeight: 700, color: passed ? 'var(--success)' : 'var(--error)' }}>
                              {(s.score ?? 0).toFixed(1)}
                            </span>
                          </td>
                          {/* Correct/Total */}
                          <td style={{ padding: '12px 16px', color: 'var(--text-secondary)' }}>
                            {s.correctCount}/{s.totalQuestions}
                          </td>
                          {/* Passed */}
                          <td style={{ padding: '12px 16px' }}>
                            <span className={`badge ${passed ? 'badge-success' : 'badge-error'}`}>
                              {passed ? '✓ Đạt' : '✗ Chưa đạt'}
                            </span>
                          </td>
                          {/* Duration */}
                          <td style={{ padding: '12px 16px', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                            {s.durationMinutes != null ? `${Math.round(s.durationMinutes)} phút` : '—'}
                          </td>
                          {/* SubmittedAt */}
                          <td style={{ padding: '12px 16px', color: 'var(--text-muted)', fontSize: '0.82rem' }}>
                            {s.submittedAt ? new Date(s.submittedAt).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' }) : '—'}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* ── Tab: Questions ── */}
        {activeTab === 'questions' && (
          <div style={{ animation: 'fadeIn 0.4s ease' }}>
            {questionList.length === 0 ? (
              <EmptyState icon="❓" title="Chưa có phân tích câu hỏi" desc="Câu hỏi sẽ được phân tích sau khi có học sinh nộp bài." />
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                {questionList.map((q, i) => (
                  <QuestionStatCard key={q.questionId || i} q={q} index={i} total={students?.totalSubmitted || 1} />
                ))}
              </div>
            )}
          </div>
        )}

      </div>
    </div>
  );
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function OverviewCard({ icon, label, value, color, sub }) {
  return (
    <div className="card" style={{ padding: '20px 20px', textAlign: 'center', animation: 'scaleIn 0.4s ease' }}>
      <div style={{ fontSize: '2rem', marginBottom: 8 }}>{icon}</div>
      <div style={{ fontSize: '1.6rem', fontWeight: 800, color, lineHeight: 1, marginBottom: 4 }}>{value}</div>
      <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', fontWeight: 500 }}>{label}</div>
      {sub && <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: 2 }}>{sub}</div>}
    </div>
  );
}

function ScoreDistributionBar({ distribution }) {
  const max = Math.max(...distribution.map(d => d.count), 1);
  const colors = ['#f87171', '#fb923c', '#fbbf24', '#34d399', '#818cf8'];
  return (
    <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', height: 80 }}>
      {distribution.map((d, i) => (
        <div key={d.range} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
          <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', fontWeight: 600 }}>{d.count}</span>
          <div style={{
            width: '100%', height: `${Math.max((d.count / max) * 60, d.count > 0 ? 8 : 0)}px`,
            background: colors[i], borderRadius: '4px 4px 0 0', minHeight: d.count > 0 ? 8 : 0,
            transition: 'height 0.6s ease',
          }} />
          <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textAlign: 'center', lineHeight: 1.2 }}>{d.range}</span>
          <span style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>{d.percentage?.toFixed(0)}%</span>
        </div>
      ))}
    </div>
  );
}

function QuestionStatCard({ q, index, total }) {
  const diff = DIFF[q.difficulty] || DIFF.medium;
  const correctRate   = q.correctRate   ?? 0;
  const incorrectRate = q.incorrectRate ?? 0;
  const skipRate      = q.skipRate      ?? 0;
  const dist          = q.optionDistribution || {};

  return (
    <div className="card" style={{ padding: '18px 22px', animation: `fadeIn 0.3s ease ${index * 0.04}s both` }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14, marginBottom: 12 }}>
        {/* Index */}
        <div style={{ padding: '4px 12px', borderRadius: 'var(--radius-full)', background: 'var(--surface-active)', border: '1px solid var(--border-accent)', color: 'var(--accent-light)', fontSize: '0.8rem', fontWeight: 700, flexShrink: 0 }}>
          Câu {q.orderIndex ?? index + 1}
        </div>
        {/* Content */}
        <p style={{ flex: 1, fontSize: '0.95rem', color: 'var(--text-primary)', fontWeight: 500, lineHeight: 1.6 }}>
          {q.content}
        </p>
        {/* Difficulty */}
        <span style={{ padding: '4px 10px', borderRadius: 'var(--radius-full)', background: diff.bg, color: diff.color, fontSize: '0.75rem', fontWeight: 600, flexShrink: 0 }}>
          {diff.label}
        </span>
      </div>

      {/* Correct Answer Tag */}
      <div style={{ marginBottom: 10 }}>
        <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Đáp án đúng: </span>
        <span style={{ padding: '3px 10px', borderRadius: 6, background: 'rgba(52,211,153,0.12)', color: 'var(--success)', fontWeight: 700, fontSize: '0.82rem' }}>
          {q.correctAnswer}
        </span>
      </div>

      {/* Rate bars */}
      <div style={{ display: 'flex', gap: 20, marginBottom: 12, flexWrap: 'wrap' }}>
        <RateBar label="Đúng"    pct={correctRate}   color="var(--success)" />
        <RateBar label="Sai"     pct={incorrectRate} color="var(--error)" />
        <RateBar label="Bỏ qua" pct={skipRate}      color="var(--text-muted)" />
      </div>

      {/* Option distribution */}
      {Object.keys(dist).length > 0 && (
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {['A', 'B', 'C', 'D'].map(letter => {
            const count = dist[letter] ?? 0;
            const pct   = total > 0 ? (count / total * 100).toFixed(0) : 0;
            const isCorrect = letter === q.correctAnswer;
            return (
              <div key={letter} style={{
                flex: 1, minWidth: 64, padding: '8px 10px', borderRadius: 8, textAlign: 'center',
                background: isCorrect ? 'rgba(52,211,153,0.08)' : 'var(--surface)',
                border: `1px solid ${isCorrect ? 'rgba(52,211,153,0.3)' : 'var(--border)'}`,
              }}>
                <div style={{ fontWeight: 700, fontSize: '0.85rem', color: isCorrect ? 'var(--success)' : 'var(--text-muted)', marginBottom: 2 }}>{letter}</div>
                <div style={{ fontSize: '1rem', fontWeight: 700, color: isCorrect ? 'var(--success)' : 'var(--text-primary)' }}>{count}</div>
                <div style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>{pct}%</div>
              </div>
            );
          })}
          {dist.skipped != null && (
            <div style={{ flex: 1, minWidth: 64, padding: '8px 10px', borderRadius: 8, textAlign: 'center', background: 'var(--surface)', border: '1px solid var(--border)' }}>
              <div style={{ fontWeight: 700, fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: 2 }}>—</div>
              <div style={{ fontSize: '1rem', fontWeight: 700, color: 'var(--text-secondary)' }}>{dist.skipped}</div>
              <div style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>Bỏ qua</div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function RateBar({ label, pct, color }) {
  return (
    <div style={{ flex: 1, minWidth: 100 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{label}</span>
        <span style={{ fontSize: '0.78rem', fontWeight: 700, color }}>{pct.toFixed(1)}%</span>
      </div>
      <div style={{ height: 6, background: 'var(--border)', borderRadius: 3, overflow: 'hidden' }}>
        <div style={{ height: '100%', width: `${Math.min(pct, 100)}%`, background: color, borderRadius: 3, transition: 'width 0.6s ease' }} />
      </div>
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
