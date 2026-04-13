// Exam Service API — gọi qua Vite proxy /api-exam → http://localhost:5001
const BASE = '/api-exam';

const handle = async (res) => {
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(data.message || data.error || `HTTP ${res.status}`);
    err.status = res.status;
    err.data = data;
    throw err;
  }
  return data;
};

// ─── Exams ───────────────────────────────────────────────────────────────────

/**
 * Lấy danh sách bài thi.
 * @param {string|null} status - 'PUBLISHED' | 'DRAFT' | 'CLOSED' | null (tất cả)
 */
export const getExams = (status, page = 1, limit = 100, createdBy = null) => {
  const params = new URLSearchParams({ page, limit });
  if (status) params.set('status', status);
  if (createdBy) params.set('createdBy', createdBy);
  return fetch(`${BASE}/exams?${params}`).then(handle);
};

/** Lấy chi tiết một bài thi theo ID */
export const getExamById = (examId) =>
  fetch(`${BASE}/exams/${examId}`).then(handle);

/** Tạo bài thi mới (giáo viên) */
export const createExam = (data) =>
  fetch(`${BASE}/exams`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  }).then(handle);

/** Cập nhật bài thi (giáo viên) */
export const updateExam = (examId, data) =>
  fetch(`${BASE}/exams/${examId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  }).then(handle);

/** Thay đổi trạng thái bài thi: published | closed */
export const updateExamStatus = (examId, status) =>
  fetch(`${BASE}/exams/${examId}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status }),
  }).then(handle);

/**
 * Lấy câu hỏi của bài thi — KHÔNG có đáp án (cho học sinh).
 * Response: { examId, totalQuestions, questions: [{questionId, orderIndex, content, optionA, optionB, optionC, optionD}] }
 */
export const getExamQuestionsForStudent = (examId) =>
  fetch(`${BASE}/exams/${examId}/questions?includeAnswers=false`).then(handle);

/** Lấy câu hỏi của bài thi — CÓ đáp án (cho giáo viên / internal) */
export const getExamQuestionsWithAnswers = (examId) =>
  fetch(`${BASE}/exams/${examId}/questions?includeAnswers=true`).then(handle);

// Alias
export const getExamQuestions = getExamQuestionsForStudent;

// ─── Questions ────────────────────────────────────────────────────────────────

/**
 * Lấy danh sách câu hỏi (ngân hàng câu hỏi).
 * @param {string|null} createdBy - lọc theo giáo viên
 */
export const getQuestions = (createdBy = null, page = 1, limit = 100) => {
  const params = new URLSearchParams({ page, limit });
  if (createdBy) params.set('createdBy', createdBy);
  return fetch(`${BASE}/questions?${params}`).then(handle);
};

/** Tạo câu hỏi mới */
export const createQuestion = (data) =>
  fetch(`${BASE}/questions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  }).then(handle);

/** Cập nhật câu hỏi */
export const updateQuestion = (questionId, data) =>
  fetch(`${BASE}/questions/${questionId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  }).then(handle);

/** Xóa câu hỏi */
export const deleteQuestion = (questionId) =>
  fetch(`${BASE}/questions/${questionId}`, {
    method: 'DELETE',
  }).then(async (res) => {
    if (!res.ok && res.status !== 204) {
      const data = await res.json().catch(() => ({}));
      const err = new Error(data.message || `HTTP ${res.status}`);
      err.status = res.status;
      throw err;
    }
    return { success: true };
  });
