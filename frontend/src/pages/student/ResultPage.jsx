import { useEffect, useState } from 'react';
import { useParams, useLocation, useNavigate } from 'react-router-dom';
import Navbar from '../../components/Navbar.jsx';
import { getSubmission } from '../../api/submissionApi.js';

export default function ResultPage() {
  const { submissionId } = useParams();
  const { state } = useLocation();
  const navigate = useNavigate();

  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeFilter, setActiveFilter] = useState('all'); // 'all' | 'correct' | 'incorrect'
  const [scoreVisible, setScoreVisible] = useState(false);

  useEffect(() => {
    if (state?.result) {
      // Đến từ ExamPage với state — result có thể là {data:{...}} hoặc object trực tiếp
      const r = state.result?.data || state.result;
      setResult(r);
      setLoading(false);
      setTimeout(() => setScoreVisible(true), 200);
    } else {
      // Truy cập trực tiếp bằng URL
      fetchResult();
    }
  }, [submissionId]);

  const fetchResult = async () => {
    try {
      const res = await getSubmission(submissionId);
      // Handle cả wrapped {data:{...}} và direct object
      setResult(res.data || res);
      setTimeout(() => setScoreVisible(true), 200);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--bg-primary)' }}>
        <Navbar />
        <div className="loading-center" style={{ minHeight: 'calc(100vh - 64px)' }}>
          <div className="spinner" style={{ width: 48, height: 48 }} />
          <p>Đang tải kết quả...</p>
        </div>
      </div>
    );
  }

  if (error || !result) {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--bg-primary)' }}>
        <Navbar />
        <div className="container section" style={{ maxWidth: 600, textAlign: 'center', paddingTop: 80 }}>
          <p style={{ color: 'var(--error)', marginBottom: 16 }}>⚠ {error || 'Không tìm thấy kết quả.'}</p>
          <button className="btn btn-secondary" onClick={() => navigate('/student')}>← Về dashboard</button>
        </div>
      </div>
    );
  }

  const score = result.score ?? result.data?.score ?? 0;
  const correctCount = result.correctCount ?? result.data?.correctCount ?? 0;
  const totalQuestions = result.totalQuestions ?? result.data?.totalQuestions ?? 0;
  const passed = result.passed ?? result.data?.passed ?? score >= 50;
  const answers = result.answers ?? result.data?.answers ?? [];

  const filteredAnswers = answers.filter(a => {
    if (activeFilter === 'correct') return a.isCorrect === true;
    if (activeFilter === 'incorrect') return a.isCorrect === false;
    return true;
  });

  const radius = 68;
  const circumference = 2 * Math.PI * radius;
  const dashOffset = scoreVisible ? circumference * (1 - score / 100) : circumference;

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg-primary)' }}>
      <Navbar />

      <div className="container section" style={{ maxWidth: 850 }}>

        {/* Score card */}
        <div
          className="card"
          style={{
            padding: '48px 40px', textAlign: 'center', marginBottom: 32,
            border: `1px solid ${passed ? 'rgba(52,211,153,0.3)' : 'rgba(248,113,113,0.3)'}`,
            background: `${passed ? 'rgba(52,211,153,0.05)' : 'rgba(248,113,113,0.05)'}`,
            animation: 'scaleIn 0.5s ease',
          }}
        >
          {/* Score ring */}
          <div style={{ position: 'relative', width: 160, height: 160, margin: '0 auto 24px' }}>
            <svg width="160" height="160" viewBox="0 0 160 160">
              {/* Background track */}
              <circle cx="80" cy="80" r={radius} fill="none" stroke="var(--border)" strokeWidth="10" />
              {/* Score arc */}
              <circle
                cx="80" cy="80" r={radius}
                fill="none"
                stroke={passed ? 'var(--success)' : 'var(--error)'}
                strokeWidth="10"
                strokeLinecap="round"
                strokeDasharray={circumference}
                strokeDashoffset={dashOffset}
                transform="rotate(-90 80 80)"
                style={{ transition: 'stroke-dashoffset 1.2s cubic-bezier(0.4,0,0.2,1)' }}
              />
            </svg>
            {/* Score text */}
            <div style={{
              position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column',
              alignItems: 'center', justifyContent: 'center',
            }}>
              <div style={{ fontSize: '2.5rem', fontWeight: 800, color: passed ? 'var(--success)' : 'var(--error)', lineHeight: 1 }}>
                {score.toFixed(0)}
              </div>
              <div style={{ fontSize: '1rem', color: 'var(--text-muted)', fontWeight: 500 }}>điểm</div>
            </div>
          </div>

          {/* Passed/Failed badge */}
          <div style={{ marginBottom: 16 }}>
            <span style={{
              display: 'inline-flex', alignItems: 'center', gap: 8, padding: '10px 24px',
              borderRadius: 'var(--radius-full)', fontSize: '1.1rem', fontWeight: 700,
              background: passed ? 'var(--success-bg)' : 'var(--error-bg)',
              color: passed ? 'var(--success)' : 'var(--error)',
              border: `1px solid ${passed ? 'rgba(52,211,153,0.4)' : 'rgba(248,113,113,0.4)'}`,
            }}>
              {passed ? '🏆 ĐẠT' : '❌ CHƯA ĐẠT'}
            </span>
          </div>

          <h2 style={{ fontSize: '1.3rem', fontWeight: 700, marginBottom: 8 }}>
            {passed ? 'Chúc mừng! Bạn đã vượt qua bài thi.' : 'Chưa đạt. Hãy cố gắng hơn lần sau!'}
          </h2>

          {/* Stats row */}
          <div style={{ display: 'flex', justifyContent: 'center', gap: 32, marginTop: 24, flexWrap: 'wrap' }}>
            <StatBox label="Câu đúng" value={correctCount} total={totalQuestions} color="var(--success)" />
            <StatBox label="Câu sai" value={totalQuestions - correctCount} total={totalQuestions} color="var(--error)" />
            <StatBox label="Điểm số" value={`${score.toFixed(1)}/100`} color={passed ? 'var(--success)' : 'var(--error)'} />
          </div>
        </div>

        {/* Detailed answers */}
        {answers.length > 0 && (
          <div style={{ animation: 'fadeIn 0.6s ease 0.3s both' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16, flexWrap: 'wrap', gap: 12 }}>
              <h3 style={{ fontSize: '1.1rem', fontWeight: 700 }}>Chi tiết đáp án</h3>
              <div className="tabs" style={{ width: 'auto' }}>
                {[['all', 'Tất cả'], ['correct', '✓ Đúng'], ['incorrect', '✗ Sai']].map(([val, label]) => (
                  <button key={val} className={`tab-btn ${activeFilter === val ? 'active' : ''}`} onClick={() => setActiveFilter(val)}>
                    {label} {val === 'correct' ? `(${correctCount})` : val === 'incorrect' ? `(${totalQuestions - correctCount})` : `(${totalQuestions})`}
                  </button>
                ))}
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {filteredAnswers.map((ans, i) => (
                <AnswerReviewCard key={ans.questionId || i} ans={ans} index={i} totalFilter={filteredAnswers.length} />
              ))}
            </div>
          </div>
        )}

        {/* Actions */}
        <div style={{ display: 'flex', justifyContent: 'center', gap: 12, marginTop: 40 }}>
          <button className="btn btn-secondary" onClick={() => navigate('/student')}>
            ← Về Dashboard
          </button>
        </div>
      </div>
    </div>
  );
}

function AnswerReviewCard({ ans, index }) {
  const correct = ans.isCorrect === true;
  const skipped = !ans.selectedOption;

  return (
    <div
      className="card"
      style={{
        padding: '20px 24px',
        borderColor: skipped ? 'var(--border)' : correct ? 'rgba(52,211,153,0.25)' : 'rgba(248,113,113,0.25)',
        background: skipped ? 'var(--surface)' : correct ? 'rgba(52,211,153,0.04)' : 'rgba(248,113,113,0.04)',
        animation: `fadeIn 0.3s ease ${index * 0.05}s both`,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 16 }}>
        {/* Status icon */}
        <div style={{
          width: 32, height: 32, borderRadius: '50%', flexShrink: 0,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          background: skipped ? 'var(--surface)' : correct ? 'var(--success-bg)' : 'var(--error-bg)',
          fontSize: '0.9rem',
        }}>
          {skipped ? '—' : correct ? '✓' : '✗'}
        </div>

        <div style={{ flex: 1 }}>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: 8 }}>
            Câu {ans.orderIndex || (index + 1)}
          </p>
          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
            {skipped ? (
              <span style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Bỏ qua câu này</span>
            ) : (
              <>
                <AnswerChip label="Bạn chọn" option={ans.selectedOption} isCorrect={correct} />
                {ans.correctAnswer && !correct && (
                  <AnswerChip label="Đáp án đúng" option={ans.correctAnswer} isCorrect={true} forceGreen />
                )}
                {correct && (
                  <AnswerChip label="Đáp án đúng" option={ans.correctAnswer} isCorrect={true} forceGreen />
                )}
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function AnswerChip({ label, option, isCorrect, forceGreen }) {
  const color = forceGreen ? 'var(--success)' : isCorrect ? 'var(--success)' : 'var(--error)';
  const bg = forceGreen ? 'var(--success-bg)' : isCorrect ? 'var(--success-bg)' : 'var(--error-bg)';
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{label}</span>
      <span style={{ padding: '4px 12px', borderRadius: 'var(--radius-full)', background: bg, color, fontWeight: 700, fontSize: '0.9rem' }}>
        {option || '?'}
      </span>
    </div>
  );
}

function StatBox({ label, value, total, color }) {
  return (
    <div style={{ textAlign: 'center' }}>
      <div style={{ fontSize: '1.8rem', fontWeight: 800, color, lineHeight: 1 }}>
        {value}{total ? <span style={{ fontSize: '1rem', color: 'var(--text-muted)', fontWeight: 400 }}>/{total}</span> : ''}
      </div>
      <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 4 }}>{label}</div>
    </div>
  );
}
