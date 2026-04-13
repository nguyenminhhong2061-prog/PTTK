import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import Navbar from '../../components/Navbar.jsx';
import Timer from '../../components/Timer.jsx';
import { startExam, saveAnswers, submitExam, getSubmissionByExam } from '../../api/submissionApi.js';

export default function ExamPage() {
  const { examId } = useParams();
  const navigate = useNavigate();
  const user = JSON.parse(sessionStorage.getItem('quizUser') || '{}');

  const [submission, setSubmission] = useState(null);    // từ POST /submissions
  const [questions, setQuestions] = useState([]);
  const [answers, setAnswers] = useState({});            // {questionId: 'A'|'B'|'C'|'D'}
  const [currentQ, setCurrentQ] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [redirecting, setRedirecting] = useState(false); // đang redirect sau 409
  const [submitting, setSubmitting] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [savingIndicator, setSavingIndicator] = useState('');

  const isSaving = useRef(false);

  // Bắt đầu / resume bài thi
  useEffect(() => {
    initExam();
  }, [examId]);

  const initExam = async () => {
    setLoading(true);
    setError('');
    setRedirecting(false);
    try {
      // examId là UUID string — KHÔNG convert sang Number
      const res = await startExam(examId, user.id);
      // Backend có thể wrap trong {data:...} hoặc trả về trực tiếp
      const sub = res.data || res;
      setSubmission(sub);
      setQuestions(sub.questions || []);

      // Khôi phục đáp án đã chọn trước đó (khi resume)
      const restored = {};
      (sub.questions || []).forEach(q => {
        if (q.selectedOption) restored[String(q.questionId)] = q.selectedOption;
      });
      setAnswers(restored);
    } catch (err) {
      // ── 409 Conflict: học sinh đã nộp bài thi này rồi ──
      // → Tìm submissionId trong DB và redirect sang trang kết quả
      if (err.status === 409 || err.message?.includes('đã nộp')) {
        setRedirecting(true);
        try {
          const subRes = await getSubmissionByExam(user.id, examId);
          // Handle cả {data:[]} và {submissions:[]} và array trực tiếp
          const rawList = subRes.data || subRes.submissions || (Array.isArray(subRes) ? subRes : []);
          const submitted = rawList.find(s => (s.status || '').toUpperCase() === 'SUBMITTED') || rawList[0];
          if (submitted?.id) {
            navigate(`/student/result/${submitted.id}`, { replace: true });
            return;
          }
        } catch (_) {
          // Không tìm được submission → báo lỗi và cho về dashboard
        }
        setRedirecting(false);
        setError('Bạn đã nộp bài thi này. Vui lòng xem kết quả từ Dashboard.');
      } else {
        setError(err.message);
      }
    } finally {
      setLoading(false);
    }
  };

  // Lưu đáp án lên server (debounce thủ công)
  const persistAnswer = useCallback(async (questionId, option) => {
    if (isSaving.current || !submission) return;
    isSaving.current = true;
    setSavingIndicator('Đang lưu...');
    try {
      await saveAnswers(submission.submissionId, [
        { questionId: String(questionId), selectedOption: option },
      ]);
      setSavingIndicator('✓ Đã lưu');
      setTimeout(() => setSavingIndicator(''), 1500);
    } catch {
      setSavingIndicator('⚠ Lỗi lưu');
    } finally {
      isSaving.current = false;
    }
  }, [submission]);

  const handleSelectAnswer = (questionId, option) => {
    setAnswers(prev => ({ ...prev, [String(questionId)]: option }));
    persistAnswer(questionId, option);
  };

  // Nộp bài
  const handleSubmit = async () => {
    setShowConfirm(false);
    setSubmitting(true);
    try {
      // Lưu tất cả đáp án trước khi nộp
      const allAnswers = questions
        .map(q => ({
          questionId: String(q.questionId),
          selectedOption: answers[String(q.questionId)] || null,
        }))
        .filter(a => a.selectedOption);

      if (allAnswers.length > 0) {
        await saveAnswers(submission.submissionId, allAnswers);
      }

      const result = await submitExam(submission.submissionId);
      navigate(`/student/result/${submission.submissionId}`, { state: { result } });
    } catch (err) {
      setSubmitting(false);
      // Nếu đã nộp từ tab/device khác → redirect kết quả
      if (err.status === 409) {
        navigate(`/student/result/${submission.submissionId}`, { replace: true });
      } else {
        alert('❌ Lỗi khi nộp bài: ' + err.message);
      }
    }
  };

  const handleAutoSubmit = useCallback(() => {
    if (!submitting && submission) {
      handleSubmit();
    }
  }, [submitting, submission]);

  // ── Loading / Redirect states ──────────────────────────────────────────
  if (loading || redirecting) {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--bg-primary)' }}>
        <Navbar />
        <div className="loading-center" style={{ minHeight: 'calc(100vh - 64px)' }}>
          <div className="spinner" style={{ width: 48, height: 48 }} />
          <p>{redirecting ? 'Đang chuyển đến trang kết quả...' : 'Đang chuẩn bị đề thi...'}</p>
        </div>
      </div>
    );
  }

  // ── Error state ────────────────────────────────────────────────────────
  if (error) {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--bg-primary)' }}>
        <Navbar />
        <div className="container section" style={{ maxWidth: 600, textAlign: 'center', paddingTop: 80 }}>
          <div style={{ fontSize: '3rem', marginBottom: 16 }}>
            {error.includes('đã nộp') ? '✅' : '⚠️'}
          </div>
          <h2 style={{ color: error.includes('đã nộp') ? 'var(--success)' : 'var(--error)', marginBottom: 12 }}>
            {error.includes('đã nộp') ? 'Bài thi đã hoàn thành' : 'Không thể tải bài thi'}
          </h2>
          <p style={{ color: 'var(--text-secondary)', marginBottom: 28 }}>{error}</p>
          <button className="btn btn-secondary" onClick={() => navigate('/student')}>
            ← Về Dashboard
          </button>
        </div>
      </div>
    );
  }

  // ── Empty questions fallback ───────────────────────────────────────────
  if (questions.length === 0) {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--bg-primary)' }}>
        <Navbar />
        <div className="container section" style={{ maxWidth: 600, textAlign: 'center', paddingTop: 80 }}>
          <div style={{ fontSize: '3rem', marginBottom: 16 }}>📭</div>
          <h2 style={{ color: 'var(--text-secondary)', marginBottom: 12 }}>Bài thi không có câu hỏi</h2>
          <button className="btn btn-secondary" onClick={() => navigate('/student')}>← Về Dashboard</button>
        </div>
      </div>
    );
  }

  const q = questions[currentQ];
  const answeredCount = Object.keys(answers).length;
  const progress = questions.length > 0 ? (answeredCount / questions.length) * 100 : 0;
  const OPTIONS = ['A', 'B', 'C', 'D'];
  const optionValues = [q?.optionA, q?.optionB, q?.optionC, q?.optionD];

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg-primary)' }}>
      <Navbar />

      {/* Exam sticky header */}
      <div style={{
        borderBottom: '1px solid var(--border)',
        background: 'rgba(13,13,40,0.9)',
        backdropFilter: 'blur(12px)',
        position: 'sticky', top: 64, zIndex: 50,
      }}>
        <div className="container" style={{
          padding: '12px 24px', display: 'flex',
          alignItems: 'center', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap',
        }}>
          <div>
            <h3 style={{
              fontSize: '1rem', fontWeight: 700, color: 'var(--text-primary)',
              whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: 320,
            }}>
              {submission?.examTitle || `Bài thi #${examId}`}
            </h3>
            <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              Đã trả lời:{' '}
              <span style={{ color: 'var(--accent)', fontWeight: 600 }}>{answeredCount}</span>
              /{questions.length} câu
              {savingIndicator && (
                <span style={{
                  marginLeft: 8, fontSize: '0.75rem',
                  color: savingIndicator.includes('✓') ? 'var(--success)' : 'var(--warning)',
                }}>
                  {savingIndicator}
                </span>
              )}
            </p>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            {submission?.deadlineAt && (
              <Timer deadlineAt={submission.deadlineAt} onExpire={handleAutoSubmit} />
            )}
            <button
              className="btn btn-primary btn-sm"
              onClick={() => setShowConfirm(true)}
              disabled={submitting}
            >
              {submitting
                ? <><div className="spinner" style={{ width: 14, height: 14, borderWidth: 2 }} /> Đang nộp...</>
                : '✓ Nộp bài'}
            </button>
          </div>
        </div>

        {/* Progress bar */}
        <div style={{ height: 3, background: 'var(--border)' }}>
          <div style={{
            height: '100%', width: `${progress}%`,
            background: 'var(--gradient-primary)', transition: 'width 0.4s ease',
          }} />
        </div>
      </div>

      {/* Main content */}
      <div className="container section" style={{ maxWidth: 960 }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 240px', gap: 24, alignItems: 'start' }}>

          {/* Question panel */}
          <div style={{ animation: 'fadeIn 0.3s ease' }}>
            {/* Question number badge */}
            <div style={{ marginBottom: 16, display: 'flex', alignItems: 'center', gap: 10 }}>
              <div style={{
                padding: '5px 14px', borderRadius: 'var(--radius-full)',
                background: 'var(--surface-active)', border: '1px solid var(--border-accent)',
                color: 'var(--accent-light)', fontSize: '0.85rem', fontWeight: 600,
              }}>
                Câu {currentQ + 1} / {questions.length}
              </div>
              {answers[String(q?.questionId)] && (
                <span style={{ fontSize: '0.78rem', color: 'var(--success)' }}>✓ Đã trả lời</span>
              )}
            </div>

            {/* Question content */}
            <div className="card" style={{ padding: '24px 28px', marginBottom: 16, borderColor: 'var(--border-accent)' }}>
              <p style={{ fontSize: '1.05rem', lineHeight: 1.75, color: 'var(--text-primary)', fontWeight: 500 }}>
                {q?.content}
              </p>
            </div>

            {/* Options */}
            <div>
              {OPTIONS.map((letter, idx) => {
                const selected = answers[String(q?.questionId)] === letter;
                const val = optionValues[idx];
                if (!val) return null;
                return (
                  <button
                    key={letter}
                    className={`option-btn ${selected ? 'selected' : ''}`}
                    onClick={() => handleSelectAnswer(q?.questionId, letter)}
                    disabled={submitting}
                  >
                    <span className="option-label">{letter}</span>
                    <span style={{ flex: 1, textAlign: 'left' }}>{val}</span>
                    {selected && <span style={{ color: 'var(--accent)', flexShrink: 0 }}>✓</span>}
                  </button>
                );
              })}
            </div>

            {/* Navigation buttons */}
            <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 24, gap: 12 }}>
              <button
                className="btn btn-secondary"
                onClick={() => setCurrentQ(p => Math.max(0, p - 1))}
                disabled={currentQ === 0}
              >
                ← Câu trước
              </button>
              <button
                className="btn btn-secondary"
                onClick={() => setCurrentQ(p => Math.min(questions.length - 1, p + 1))}
                disabled={currentQ === questions.length - 1}
              >
                Câu sau →
              </button>
            </div>
          </div>

          {/* Question map sidebar */}
          <div style={{ position: 'sticky', top: 140 }}>
            <div className="card" style={{ padding: 20 }}>
              <h4 style={{ fontSize: '0.85rem', fontWeight: 600, marginBottom: 14, color: 'var(--text-secondary)' }}>
                Bảng câu hỏi
              </h4>
              <div className="q-dots">
                {questions.map((qItem, i) => {
                  const answered = !!answers[String(qItem.questionId)];
                  const active = i === currentQ;
                  return (
                    <button
                      key={i}
                      className={`q-dot ${active ? 'active' : answered ? 'answered' : ''}`}
                      onClick={() => setCurrentQ(i)}
                      title={`Câu ${i + 1}${answered ? ' (đã trả lời)' : ''}`}
                    >
                      {i + 1}
                    </button>
                  );
                })}
              </div>

              <div className="divider" />
              <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', display: 'flex', flexDirection: 'column', gap: 6 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <div className="q-dot answered" style={{ width: 20, height: 20, fontSize: '0.6rem', cursor: 'default', flexShrink: 0 }} />
                  <span>Đã trả lời ({answeredCount})</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <div className="q-dot" style={{ width: 20, height: 20, fontSize: '0.6rem', cursor: 'default', flexShrink: 0 }} />
                  <span>Chưa trả lời ({questions.length - answeredCount})</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <div className="q-dot active" style={{ width: 20, height: 20, fontSize: '0.6rem', cursor: 'default', flexShrink: 0 }} />
                  <span>Câu đang xem</span>
                </div>
              </div>
            </div>

            {/* Quick submit */}
            <button
              className="btn btn-primary"
              style={{ width: '100%', marginTop: 16 }}
              onClick={() => setShowConfirm(true)}
              disabled={submitting}
            >
              {submitting ? 'Đang nộp...' : '✓ Nộp bài'}
            </button>
          </div>
        </div>
      </div>

      {/* Confirm submit modal */}
      {showConfirm && (
        <div className="modal-overlay" onClick={() => setShowConfirm(false)}>
          <div className="modal" style={{ textAlign: 'center' }} onClick={e => e.stopPropagation()}>
            <div style={{ fontSize: '3rem', marginBottom: 12 }}>📤</div>
            <h2 style={{ marginBottom: 10 }}>Xác nhận nộp bài?</h2>
            <p style={{ color: 'var(--text-secondary)', marginBottom: 8 }}>
              Bạn đã trả lời{' '}
              <strong style={{ color: 'var(--accent)' }}>{answeredCount}/{questions.length}</strong> câu hỏi.
            </p>
            {answeredCount < questions.length && (
              <p style={{ color: 'var(--warning)', fontSize: '0.9rem', marginBottom: 8 }}>
                ⚠ Còn {questions.length - answeredCount} câu chưa trả lời sẽ bị tính sai.
              </p>
            )}
            <p style={{ color: 'var(--text-muted)', fontSize: '0.88rem', marginBottom: 24 }}>
              Sau khi nộp, bạn không thể thay đổi đáp án.
            </p>
            <div style={{ display: 'flex', gap: 12 }}>
              <button className="btn btn-secondary" style={{ flex: 1 }} onClick={() => setShowConfirm(false)}>
                Xem lại
              </button>
              <button className="btn btn-primary" style={{ flex: 2 }} onClick={handleSubmit}>
                ✓ Nộp bài ngay
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
