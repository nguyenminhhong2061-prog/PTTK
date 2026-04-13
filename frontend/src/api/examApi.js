// Exam Service API — gọi qua Vite proxy /api-exam → http://localhost:5001
const BASE = '/api-exam';

const handle = async (res) => {
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: res.statusText }));
    throw new Error(err.message || `HTTP ${res.status}`);
  }
  return res.json();
};

/**
 * Lấy danh sách bài thi.
 * @param {string|null} status - 'PUBLISHED' | 'DRAFT' | 'CLOSED' | null (tất cả)
 */
export const getExams = (status, page = 1, limit = 100) => {
  const params = new URLSearchParams({ page, limit });
  if (status) params.set('status', status);
  return fetch(`${BASE}/exams?${params}`).then(handle);
};

/** Lấy chi tiết một bài thi theo ID */
export const getExamById = (examId) =>
  fetch(`${BASE}/exams/${examId}`).then(handle);

/**
 * Lấy câu hỏi của bài thi — KHÔNG có đáp án (cho học sinh).
 * Response: { examId, totalQuestions, questions: [{questionId, orderIndex, content, optionA, optionB, optionC, optionD}] }
 */
export const getExamQuestionsForStudent = (examId) =>
  fetch(`${BASE}/exams/${examId}/questions?includeAnswers=false`).then(handle);

// Alias
export const getExamQuestions = getExamQuestionsForStudent;
