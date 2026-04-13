import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import Navbar from '../../components/Navbar.jsx';
import {
  getExams, createExam, updateExam, updateExamStatus,
  getQuestions, createQuestion, updateQuestion, deleteQuestion,
} from '../../api/examApi.js';

// ─── Status helpers ───────────────────────────────────────────────────────────
// Backend returns uppercase enum names; API spec for PATCH uses lowercase
const normalStatus = (s) => (s || '').toUpperCase();
const STATUS_LABEL = { DRAFT: 'Nháp', PUBLISHED: 'Đang mở', CLOSED: 'Đã đóng' };
const STATUS_BADGE = {
  DRAFT:     { bg: 'rgba(100,116,139,0.15)', color: '#94a3b8', dot: '#64748b' },
  PUBLISHED: { bg: 'rgba(52,211,153,0.12)',  color: '#34d399', dot: '#34d399' },
  CLOSED:    { bg: 'rgba(248,113,113,0.12)', color: '#f87171', dot: '#f87171' },
};

// ─── Main Component ───────────────────────────────────────────────────────────
export default function TeacherDashboard() {
  const navigate = useNavigate();
  const user = JSON.parse(sessionStorage.getItem('quizUser') || '{}');

  const [tab, setTab]           = useState('exams');   // 'exams' | 'questions'
  const [exams, setExams]       = useState([]);
  const [questions, setQuestions] = useState([]);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState('');
  const [modal, setModal]       = useState(null);       // null | 'createExam' | 'editExam' | 'createQuestion' | 'editQuestion' | 'confirm'
  const [selected, setSelected] = useState(null);       // item being edited/deleted
  const [confirmAction, setConfirmAction] = useState(null);
  const [saving, setSaving]     = useState(false);
  const [toast, setToast]       = useState(null);       // { msg, type: 'ok'|'err' }
  const [searchQ, setSearchQ]   = useState('');         // search questions

  // ─ Load data ─────────────────────────────────────────────────────────────
  const loadData = useCallback(async () => {
    setLoading(true); setError('');
    try {
      const [examsRes, questionsRes] = await Promise.all([
        getExams(null, 1, 100, user.id),     // Tất cả bài thi của giáo viên này
        getQuestions(null, 1, 200),           // Toàn bộ ngân hàng câu hỏi (không lọc)
      ]);
      // Handle cả {data:[]} và {exams:[], questions:[]} và array trực tiếp
      setExams(examsRes.data || examsRes.exams || (Array.isArray(examsRes) ? examsRes : []));
      setQuestions(questionsRes.data || questionsRes.questions || (Array.isArray(questionsRes) ? questionsRes : []));
    } catch (err) {
      setError(err.message || 'Không thể tải dữ liệu');
    } finally {
      setLoading(false);
    }
  }, [user.id]);

  useEffect(() => { loadData(); }, [loadData]);

  // ─ Toast helper ──────────────────────────────────────────────────────────
  const showToast = (msg, type = 'ok') => {
    setToast({ msg, type });
    setTimeout(() => setToast(null), 3000);
  };

  // ─ Exam actions ──────────────────────────────────────────────────────────
  const handleCreateExam = async (data) => {
    setSaving(true);
    try {
      await createExam({ ...data, createdBy: user.id });
      setModal(null);
      showToast('Tạo bài thi thành công!');
      loadData();
    } catch (err) {
      showToast(err.message, 'err');
    } finally { setSaving(false); }
  };

  const handleUpdateExam = async (data) => {
    setSaving(true);
    try {
      await updateExam(selected.id, { ...data, createdBy: user.id });
      setModal(null); setSelected(null);
      showToast('Cập nhật bài thi thành công!');
      loadData();
    } catch (err) {
      showToast(err.message, 'err');
    } finally { setSaving(false); }
  };

  const handleChangeStatus = async (exam, newStatus) => {
    try {
      await updateExamStatus(exam.id, newStatus);
      showToast(newStatus === 'published' ? 'Đã công bố bài thi! 🚀' : 'Đã đóng bài thi! 🔒');
      loadData();
    } catch (err) {
      showToast(err.message, 'err');
    }
  };

  // ─ Question actions ───────────────────────────────────────────────────────
  const handleCreateQuestion = async (data) => {
    setSaving(true);
    try {
      await createQuestion({ ...data, createdBy: user.id });
      setModal(null);
      showToast('Thêm câu hỏi thành công!');
      loadData();
    } catch (err) {
      showToast(err.message, 'err');
    } finally { setSaving(false); }
  };

  const handleUpdateQuestion = async (data) => {
    setSaving(true);
    try {
      await updateQuestion(selected.id, { ...data, createdBy: user.id });
      setModal(null); setSelected(null);
      showToast('Cập nhật câu hỏi thành công!');
      loadData();
    } catch (err) {
      showToast(err.message, 'err');
    } finally { setSaving(false); }
  };

  const handleDeleteQuestion = async () => {
    setSaving(true);
    try {
      await deleteQuestion(selected.id);
      setModal(null); setSelected(null);
      showToast('Đã xóa câu hỏi!');
      loadData();
    } catch (err) {
      showToast(err.message, 'err');
    } finally { setSaving(false); }
  };

  // ─ Stats ─────────────────────────────────────────────────────────────────
  const publishedCount = exams.filter(e => normalStatus(e.status) === 'PUBLISHED').length;
  const draftCount     = exams.filter(e => normalStatus(e.status) === 'DRAFT').length;
  const filteredQ      = questions.filter(q =>
    !searchQ || q.content?.toLowerCase().includes(searchQ.toLowerCase())
  );

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg-primary)' }}>
      <Navbar />

      {/* Toast */}
      {toast && (
        <div style={{
          position: 'fixed', top: 80, right: 24, zIndex: 999,
          padding: '14px 22px', borderRadius: 12,
          background: toast.type === 'ok' ? 'rgba(52,211,153,0.15)' : 'rgba(248,113,113,0.15)',
          border: `1px solid ${toast.type === 'ok' ? 'rgba(52,211,153,0.4)' : 'rgba(248,113,113,0.4)'}`,
          color: toast.type === 'ok' ? 'var(--success)' : 'var(--error)',
          fontWeight: 600, fontSize: '0.9rem',
          animation: 'slideUp 0.3s ease', backdropFilter: 'blur(12px)',
        }}>
          {toast.type === 'ok' ? '✓' : '⚠'} {toast.msg}
        </div>
      )}

      <div className="container section" style={{ maxWidth: 1100 }}>

        {/* Header */}
        <div style={{ marginBottom: 28, animation: 'fadeIn 0.5s ease' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <div style={{
                width: 56, height: 56, borderRadius: '50%',
                background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: '1.4rem', fontWeight: 700, color: 'white',
              }}>
                {user.id?.slice(0, 2)?.toUpperCase()}
              </div>
              <div>
                <h2 style={{ fontSize: '1.4rem', fontWeight: 700 }}>
                  Xin chào, <span style={{ color: 'var(--accent-light)' }}>{user.id}</span>
                </h2>
                <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', marginTop: 2 }}>
                  Giáo viên · Quản lý đề thi & ngân hàng câu hỏi
                </p>
              </div>
            </div>

            {/* Quick stats */}
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
              <StatChip label="Bài thi" value={exams.length} color="var(--accent)" />
              <StatChip label="Đang mở" value={publishedCount} color="var(--success)" />
              <StatChip label="Nháp"    value={draftCount}     color="var(--text-muted)" />
              <StatChip label="Câu hỏi" value={questions.length} color="var(--violet)" />
            </div>
          </div>
        </div>

        {/* Tabs */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 24, flexWrap: 'wrap', gap: 12 }}>
          <div className="tabs" style={{ width: 'auto' }}>
            <button className={`tab-btn ${tab === 'exams' ? 'active' : ''}`} onClick={() => setTab('exams')}>
              📝 Bài Thi ({exams.length})
            </button>
            <button className={`tab-btn ${tab === 'questions' ? 'active' : ''}`} onClick={() => setTab('questions')}>
              ❓ Câu Hỏi ({questions.length})
            </button>
          </div>

          {tab === 'exams' ? (
            <button className="btn btn-primary btn-sm" onClick={() => { setSelected(null); setModal('createExam'); }}>
              + Tạo bài thi mới
            </button>
          ) : (
            <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
              <input
                className="input"
                style={{ width: 220, padding: '8px 14px', fontSize: '0.875rem' }}
                placeholder="Tìm câu hỏi..."
                value={searchQ}
                onChange={e => setSearchQ(e.target.value)}
              />
              <button className="btn btn-primary btn-sm" onClick={() => { setSelected(null); setModal('createQuestion'); }}>
                + Thêm câu hỏi
              </button>
            </div>
          )}
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
            <p>⚠ {error}</p>
            <button className="btn btn-sm btn-secondary" style={{ marginTop: 12 }} onClick={loadData}>Thử lại</button>
          </div>
        )}

        {/* Tab: Bài Thi */}
        {!loading && !error && tab === 'exams' && (
          <div style={{ animation: 'fadeIn 0.4s ease' }}>
            {exams.length === 0 ? (
              <EmptyState icon="📝" title="Chưa có bài thi nào" desc="Bắt đầu bằng cách tạo bài thi đầu tiên của bạn." />
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                {exams.map(exam => (
                  <ExamCard
                    key={exam.id}
                    exam={exam}
                    questions={questions}
                    onEdit={() => { setSelected(exam); setModal('editExam'); }}
                    onPublish={() => handleChangeStatus(exam, 'published')}
                    onClose={()   => handleChangeStatus(exam, 'closed')}
                    onStats={()   => navigate(`/teacher/exams/${exam.id}/stats`)}
                  />
                ))}
              </div>
            )}
          </div>
        )}

        {/* Tab: Câu Hỏi */}
        {!loading && !error && tab === 'questions' && (
          <div style={{ animation: 'fadeIn 0.4s ease' }}>
            {filteredQ.length === 0 ? (
              <EmptyState icon="❓" title={searchQ ? 'Không tìm thấy câu hỏi' : 'Chưa có câu hỏi nào'} desc={searchQ ? 'Thử từ khóa khác.' : 'Bắt đầu bằng cách thêm câu hỏi đầu tiên.'} />
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {filteredQ.map((q, idx) => (
                  <QuestionCard
                    key={q.id}
                    q={q}
                    index={idx}
                    onEdit={() => { setSelected(q); setModal('editQuestion'); }}
                    onDelete={() => {
                      setSelected(q);
                      setConfirmAction({
                        title: 'Xóa câu hỏi?',
                        body: 'Hành động này không thể hoàn tác.',
                        onConfirm: handleDeleteQuestion,
                      });
                      setModal('confirm');
                    }}
                  />
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* ── Modals ──────────────────────────────────────────────────── */}

      {modal === 'createExam' && (
        <ExamModal
          title="Tạo bài thi mới"
          questions={questions}
          saving={saving}
          onSave={handleCreateExam}
          onClose={() => setModal(null)}
        />
      )}

      {modal === 'editExam' && selected && (
        <ExamModal
          title="Chỉnh sửa bài thi"
          initial={selected}
          questions={questions}
          saving={saving}
          onSave={handleUpdateExam}
          onClose={() => { setModal(null); setSelected(null); }}
        />
      )}

      {modal === 'createQuestion' && (
        <QuestionModal
          title="Thêm câu hỏi mới"
          saving={saving}
          onSave={handleCreateQuestion}
          onClose={() => setModal(null)}
        />
      )}

      {modal === 'editQuestion' && selected && (
        <QuestionModal
          title="Chỉnh sửa câu hỏi"
          initial={selected}
          saving={saving}
          onSave={handleUpdateQuestion}
          onClose={() => { setModal(null); setSelected(null); }}
        />
      )}

      {modal === 'confirm' && confirmAction && (
        <ConfirmModal
          title={confirmAction.title}
          body={confirmAction.body}
          saving={saving}
          onConfirm={confirmAction.onConfirm}
          onClose={() => { setModal(null); setSelected(null); }}
        />
      )}
    </div>
  );
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function StatChip({ label, value, color }) {
  return (
    <div style={{ padding: '7px 16px', background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-full)', display: 'flex', alignItems: 'center', gap: 8 }}>
      <span style={{ width: 7, height: 7, borderRadius: '50%', background: color, flexShrink: 0 }} />
      <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>{label}:</span>
      <span style={{ fontSize: '0.9rem', fontWeight: 700, color }}>{value}</span>
    </div>
  );
}

function StatusBadge({ status }) {
  const s = STATUS_BADGE[normalStatus(status)] || STATUS_BADGE.DRAFT;
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '4px 12px', borderRadius: 'var(--radius-full)', background: s.bg, color: s.color, fontSize: '0.78rem', fontWeight: 600 }}>
      <span style={{ width: 6, height: 6, borderRadius: '50%', background: s.dot }} />
      {STATUS_LABEL[normalStatus(status)] || status}
    </span>
  );
}

function ExamCard({ exam, onEdit, onPublish, onClose, onStats }) {
  const [expanded, setExpanded] = useState(false);
  const status = normalStatus(exam.status);
  return (
    <div className="card" style={{ padding: '20px 24px', animation: 'fadeIn 0.3s ease' }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
        <div style={{ flex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6, flexWrap: 'wrap' }}>
            <span style={{ fontWeight: 700, fontSize: '1.05rem', color: 'var(--text-primary)' }}>{exam.title}</span>
            <StatusBadge status={status} />
          </div>
          {exam.description && (
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: 8 }}>{exam.description}</p>
          )}
          <div style={{ display: 'flex', gap: 16, color: 'var(--text-muted)', fontSize: '0.82rem', flexWrap: 'wrap' }}>
            <span>⏱ {exam.durationMinutes} phút</span>
            <span>📝 {exam.totalQuestions ?? 0} câu hỏi</span>
            <span>👤 {exam.createdBy}</span>
            {exam.createdAt && (
              <span>🗓 {new Date(exam.createdAt).toLocaleDateString('vi-VN')}</span>
            )}
          </div>
        </div>

        {/* Actions */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0, flexWrap: 'wrap' }}>
          {status === 'PUBLISHED' && (
            <button className="btn btn-sm btn-secondary" style={{ color: 'var(--accent)' }} onClick={onStats}>
              📊 Thống kê
            </button>
          )}
          {status === 'CLOSED' && (
            <button className="btn btn-sm btn-secondary" style={{ color: 'var(--accent)' }} onClick={onStats}>
              📊 Thống kê
            </button>
          )}
          {status === 'DRAFT' && (
            <>
              <button className="btn btn-sm btn-secondary" onClick={onEdit}>✏ Sửa</button>
              <button
                className="btn btn-sm btn-primary"
                onClick={onPublish}
                disabled={!exam.totalQuestions || exam.totalQuestions === 0}
                title={!exam.totalQuestions ? 'Cần có ít nhất 1 câu hỏi' : ''}
              >
                🚀 Công bố
              </button>
            </>
          )}
          {status === 'PUBLISHED' && (
            <button className="btn btn-sm btn-danger" onClick={onClose}>🔒 Đóng</button>
          )}
          <button
            className="btn btn-sm btn-ghost"
            style={{ color: 'var(--text-muted)', fontSize: '0.75rem' }}
            onClick={() => setExpanded(p => !p)}
          >
            {expanded ? '▲' : '▼'}
          </button>
        </div>
      </div>

      {expanded && (
        <div style={{ marginTop: 16, paddingTop: 16, borderTop: '1px solid var(--border)', animation: 'fadeIn 0.2s ease' }}>
          <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 600, marginBottom: 8, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            ID bài thi: <code style={{ color: 'var(--accent-light)', fontFamily: 'monospace' }}>{exam.id}</code>
          </p>
          {status === 'DRAFT' && (!exam.totalQuestions || exam.totalQuestions === 0) && (
            <p style={{ color: 'var(--warning)', fontSize: '0.85rem' }}>
              ⚠ Bài thi chưa có câu hỏi. Tạo câu hỏi và khi sửa bài thi hãy chọn câu hỏi để công bố.
            </p>
          )}
        </div>
      )}
    </div>
  );
}

function QuestionCard({ q, index, onEdit, onDelete }) {
  const [expanded, setExpanded] = useState(false);
  const OPTIONS = ['A', 'B', 'C', 'D'];
  const optVals = [q.optionA, q.optionB, q.optionC, q.optionD];

  return (
    <div className="card" style={{ padding: '16px 20px', animation: `fadeIn 0.3s ease ${index * 0.03}s both` }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
        <div style={{ flex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4, flexWrap: 'wrap' }}>
            <span style={{ padding: '2px 10px', borderRadius: 'var(--radius-full)', background: 'var(--surface-active)', color: 'var(--accent-light)', fontSize: '0.75rem', fontWeight: 600 }}>#{index + 1}</span>
            <span style={{ fontWeight: 500, fontSize: '0.95rem', color: 'var(--text-primary)', flex: 1 }}>
              {q.content?.length > 100 && !expanded ? q.content.slice(0, 100) + '…' : q.content}
            </span>
          </div>
          {!expanded && (
            <p style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>
              Đáp án: <strong style={{ color: 'var(--success)' }}>{q.correctAnswer}</strong>
              {' · '}
              <button style={{ background: 'none', border: 'none', color: 'var(--accent)', fontSize: '0.8rem', cursor: 'pointer', padding: 0 }} onClick={() => setExpanded(true)}>
                Xem thêm ▾
              </button>
            </p>
          )}
        </div>
        <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
          <button className="btn btn-sm btn-secondary" onClick={onEdit}>✏</button>
          <button className="btn btn-sm btn-danger" onClick={onDelete}>🗑</button>
        </div>
      </div>

      {expanded && (
        <div style={{ marginTop: 12, paddingTop: 12, borderTop: '1px solid var(--border)', animation: 'fadeIn 0.2s ease' }}>
          {OPTIONS.map((letter, i) => {
            const isCorrect = letter === q.correctAnswer;
            return (
              <div key={letter} style={{
                display: 'flex', alignItems: 'center', gap: 10, padding: '8px 14px', borderRadius: 8, marginBottom: 6,
                background: isCorrect ? 'rgba(52,211,153,0.08)' : 'var(--surface)',
                border: `1px solid ${isCorrect ? 'rgba(52,211,153,0.3)' : 'var(--border)'}`,
              }}>
                <span style={{ width: 28, height: 28, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: '0.8rem', flexShrink: 0, background: isCorrect ? 'var(--success)' : 'var(--border)', color: isCorrect ? 'white' : 'var(--text-muted)' }}>{letter}</span>
                <span style={{ fontSize: '0.9rem', color: isCorrect ? 'var(--success)' : 'var(--text-secondary)' }}>{optVals[i]}</span>
                {isCorrect && <span style={{ marginLeft: 'auto', fontSize: '0.75rem', color: 'var(--success)', fontWeight: 600 }}>✓ Đúng</span>}
              </div>
            );
          })}
          <button style={{ background: 'none', border: 'none', color: 'var(--text-muted)', fontSize: '0.8rem', cursor: 'pointer', marginTop: 4 }} onClick={() => setExpanded(false)}>
            Thu gọn ▴
          </button>
        </div>
      )}
    </div>
  );
}

// ─── ExamModal ────────────────────────────────────────────────────────────────
function ExamModal({ title, initial, questions, saving, onSave, onClose }) {
  const [form, setForm] = useState({
    title:           initial?.title           || '',
    description:     initial?.description     || '',
    durationMinutes: initial?.durationMinutes || 45,
    questionIds:     [],
  });

  // Pre-populate câu hỏi đã chọn khi chỉnh sửa bài thi
  const initIds = new Set(
    (initial?.questionIds || []).map(id => String(id))
  );
  const [selectedQIds, setSelectedQIds] = useState(initIds);
  const [searchQ, setSearchQ] = useState('');
  const [errors, setErrors] = useState({});

  // Map question ID → question for display
  const qMap = {};
  questions.forEach(q => { qMap[String(q.id)] = q; });

  const toggleQ = (id) => {
    setSelectedQIds(prev => {
      const next = new Set(prev);
      if (next.has(String(id))) next.delete(String(id));
      else next.add(String(id));
      return next;
    });
  };

  const validate = () => {
    const e = {};
    if (!form.title.trim() || form.title.length < 5) e.title = 'Tiêu đề phải có ít nhất 5 ký tự';
    if (!form.durationMinutes || form.durationMinutes < 1) e.durationMinutes = 'Thời gian phải > 0';
    if (selectedQIds.size === 0) e.questions = 'Phải chọn ít nhất 1 câu hỏi';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!validate()) return;
    onSave({ ...form, questionIds: [...selectedQIds] }); // UUID strings — không convert số
  };

  const filteredQ = questions.filter(q =>
    !searchQ || q.content?.toLowerCase().includes(searchQ.toLowerCase())
  );

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" style={{ minWidth: 560, maxWidth: 680, maxHeight: '90vh', overflowY: 'auto' }} onClick={e => e.stopPropagation()}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 24 }}>
          <h2 style={{ fontSize: '1.3rem', fontWeight: 700 }}>{title}</h2>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>✕</button>
        </div>

        <form onSubmit={handleSubmit}>
          {/* Title */}
          <div style={{ marginBottom: 16 }}>
            <label style={labelStyle}>Tiêu đề bài thi *</label>
            <input className="input" placeholder="VD: Kiểm tra Giữa kỳ — Lập trình Java" value={form.title} onChange={e => setForm(p => ({ ...p, title: e.target.value }))} />
            {errors.title && <p style={errStyle}>{errors.title}</p>}
          </div>

          {/* Description */}
          <div style={{ marginBottom: 16 }}>
            <label style={labelStyle}>Mô tả (tùy chọn)</label>
            <textarea className="input" style={{ minHeight: 72, resize: 'vertical' }} placeholder="Mô tả ngắn về bài thi..." value={form.description} onChange={e => setForm(p => ({ ...p, description: e.target.value }))} />
          </div>

          {/* Duration */}
          <div style={{ marginBottom: 20 }}>
            <label style={labelStyle}>Thời gian làm bài (phút) *</label>
            <input className="input" type="number" min="1" max="300" value={form.durationMinutes}
              onChange={e => setForm(p => ({ ...p, durationMinutes: parseInt(e.target.value) || 0 }))} />
            {errors.durationMinutes && <p style={errStyle}>{errors.durationMinutes}</p>}
          </div>

          {/* Question selection */}
          <div style={{ marginBottom: 20 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
              <label style={labelStyle}>Chọn câu hỏi * <span style={{ color: 'var(--accent)' }}>({selectedQIds.size} đã chọn)</span></label>
              <input className="input" style={{ width: 180, padding: '6px 12px', fontSize: '0.8rem' }} placeholder="Tìm câu hỏi..." value={searchQ} onChange={e => setSearchQ(e.target.value)} />
            </div>
            {errors.questions && <p style={errStyle}>{errors.questions}</p>}
            <div style={{ border: '1px solid var(--border)', borderRadius: 10, maxHeight: 240, overflowY: 'auto' }}>
              {filteredQ.length === 0 ? (
                <p style={{ textAlign: 'center', padding: 24, color: 'var(--text-muted)', fontSize: '0.875rem' }}>
                  {questions.length === 0 ? 'Chưa có câu hỏi nào. Hãy tạo câu hỏi trước.' : 'Không tìm thấy câu hỏi.'}
                </p>
              ) : (
                filteredQ.map((q, i) => {
                  const checked = selectedQIds.has(String(q.id));
                  return (
                    <div key={q.id}
                      onClick={() => toggleQ(q.id)}
                      style={{
                        display: 'flex', alignItems: 'flex-start', gap: 12, padding: '10px 16px', cursor: 'pointer',
                        background: checked ? 'rgba(129,140,248,0.08)' : 'transparent',
                        borderBottom: i < filteredQ.length - 1 ? '1px solid var(--border)' : 'none',
                        transition: 'background 0.15s',
                      }}
                    >
                      <div style={{ width: 18, height: 18, borderRadius: 4, border: `2px solid ${checked ? 'var(--accent)' : 'var(--border)'}`, background: checked ? 'var(--accent)' : 'transparent', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, marginTop: 2, transition: 'all 0.15s' }}>
                        {checked && <span style={{ color: 'white', fontSize: '0.65rem', fontWeight: 700 }}>✓</span>}
                      </div>
                      <span style={{ fontSize: '0.875rem', color: 'var(--text-primary)', lineHeight: 1.5 }}>
                        {q.content?.length > 80 ? q.content.slice(0, 80) + '…' : q.content}
                      </span>
                    </div>
                  );
                })
              )}
            </div>
          </div>

          <div style={{ display: 'flex', gap: 10 }}>
            <button type="button" className="btn btn-secondary" style={{ flex: 1 }} onClick={onClose}>Hủy</button>
            <button type="submit" className="btn btn-primary" style={{ flex: 2 }} disabled={saving}>
              {saving ? <><div className="spinner" style={{ width: 14, height: 14, borderWidth: 2 }} /> Đang lưu...</> : '💾 Lưu bài thi'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ─── QuestionModal ────────────────────────────────────────────────────────────
function QuestionModal({ title, initial, saving, onSave, onClose }) {
  const [form, setForm] = useState({
    content:       initial?.content       || '',
    optionA:       initial?.optionA       || '',
    optionB:       initial?.optionB       || '',
    optionC:       initial?.optionC       || '',
    optionD:       initial?.optionD       || '',
    correctAnswer: initial?.correctAnswer || 'A',
  });
  const [errors, setErrors] = useState({});

  const validate = () => {
    const e = {};
    if (!form.content.trim() || form.content.length < 10) e.content = 'Câu hỏi phải có ít nhất 10 ký tự';
    if (!form.optionA.trim()) e.optionA = 'Bắt buộc';
    if (!form.optionB.trim()) e.optionB = 'Bắt buộc';
    if (!form.optionC.trim()) e.optionC = 'Bắt buộc';
    if (!form.optionD.trim()) e.optionD = 'Bắt buộc';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!validate()) return;
    onSave(form);
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" style={{ minWidth: 520, maxWidth: 620, maxHeight: '92vh', overflowY: 'auto' }} onClick={e => e.stopPropagation()}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 24 }}>
          <h2 style={{ fontSize: '1.3rem', fontWeight: 700 }}>{title}</h2>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>✕</button>
        </div>

        <form onSubmit={handleSubmit}>
          {/* Content */}
          <div style={{ marginBottom: 16 }}>
            <label style={labelStyle}>Nội dung câu hỏi *</label>
            <textarea className="input" style={{ minHeight: 88, resize: 'vertical' }} placeholder="Nhập nội dung câu hỏi..." value={form.content} onChange={e => setForm(p => ({ ...p, content: e.target.value }))} />
            {errors.content && <p style={errStyle}>{errors.content}</p>}
          </div>

          {/* Options */}
          <div style={{ marginBottom: 20 }}>
            <label style={labelStyle}>Các lựa chọn *</label>
            {['A', 'B', 'C', 'D'].map(letter => {
              const key = `option${letter}`;
              const isCorrect = form.correctAnswer === letter;
              return (
                <div key={letter} style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
                  <button
                    type="button"
                    onClick={() => setForm(p => ({ ...p, correctAnswer: letter }))}
                    style={{
                      width: 36, height: 36, borderRadius: '50%', border: `2px solid ${isCorrect ? 'var(--success)' : 'var(--border)'}`,
                      background: isCorrect ? 'var(--success)' : 'transparent', color: isCorrect ? 'white' : 'var(--text-muted)',
                      fontWeight: 700, fontSize: '0.85rem', cursor: 'pointer', flexShrink: 0, transition: 'all 0.2s',
                    }}
                    title={`Chọn ${letter} là đáp án đúng`}
                  >
                    {letter}
                  </button>
                  <div style={{ flex: 1 }}>
                    <input
                      className="input"
                      style={{ borderColor: errors[key] ? 'var(--error)' : undefined }}
                      placeholder={`Lựa chọn ${letter}...`}
                      value={form[key]}
                      onChange={e => setForm(p => ({ ...p, [key]: e.target.value }))}
                    />
                    {errors[key] && <p style={{ ...errStyle, marginTop: 2 }}>{errors[key]}</p>}
                  </div>
                  {isCorrect && <span style={{ color: 'var(--success)', fontSize: '0.8rem', fontWeight: 600, width: 50 }}>✓ Đúng</span>}
                </div>
              );
            })}
            <p style={{ color: 'var(--text-muted)', fontSize: '0.8rem', marginTop: 4 }}>
              Nhấn vào chữ cái <strong>A/B/C/D</strong> để chọn đáp án đúng (hiện tại: <strong style={{ color: 'var(--success)' }}>{form.correctAnswer}</strong>)
            </p>
          </div>

          <div style={{ display: 'flex', gap: 10 }}>
            <button type="button" className="btn btn-secondary" style={{ flex: 1 }} onClick={onClose}>Hủy</button>
            <button type="submit" className="btn btn-primary" style={{ flex: 2 }} disabled={saving}>
              {saving ? <><div className="spinner" style={{ width: 14, height: 14, borderWidth: 2 }} /> Đang lưu...</> : '💾 Lưu câu hỏi'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ─── ConfirmModal ──────────────────────────────────────────────────────────────
function ConfirmModal({ title, body, saving, onConfirm, onClose }) {
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" style={{ textAlign: 'center', maxWidth: 400 }} onClick={e => e.stopPropagation()}>
        <div style={{ fontSize: '2.5rem', marginBottom: 16 }}>⚠️</div>
        <h2 style={{ marginBottom: 8 }}>{title}</h2>
        <p style={{ color: 'var(--text-secondary)', marginBottom: 28 }}>{body}</p>
        <div style={{ display: 'flex', gap: 10 }}>
          <button className="btn btn-secondary" style={{ flex: 1 }} onClick={onClose}>Hủy</button>
          <button className="btn btn-danger" style={{ flex: 1 }} disabled={saving} onClick={onConfirm}>
            {saving ? 'Đang xóa...' : 'Xác nhận xóa'}
          </button>
        </div>
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

// ─── Style helpers ────────────────────────────────────────────────────────────
const labelStyle = {
  display: 'block', marginBottom: 6, fontSize: '0.875rem',
  color: 'var(--text-secondary)', fontWeight: 500,
};
const errStyle = {
  color: 'var(--error)', fontSize: '0.8rem', marginTop: 4,
};
