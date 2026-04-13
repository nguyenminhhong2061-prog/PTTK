// Statistics Service API — gọi qua Vite proxy /api-stats → http://localhost:5003
const BASE = '/api-stats';

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

/**
 * Thống kê tổng quan một bài thi.
 * Response: { examId, examTitle, totalParticipants, totalSubmitted,
 *             averageScore, highestScore, lowestScore, passRate, passCount, failCount,
 *             scoreDistribution: [{range, count, percentage}], averageDurationMinutes, generatedAt }
 */
export const getExamStatistics = (examId) =>
  fetch(`${BASE}/statistics/exams/${examId}`).then(handle);

/**
 * Phân tích chi tiết từng câu hỏi trong bài thi.
 * Response: { examId, examTitle, totalSubmitted,
 *             questions: [{questionId, orderIndex, content, correctAnswer,
 *                         correctRate, incorrectRate, skipRate, difficulty,
 *                         optionDistribution: {A, B, C, D, skipped}}] }
 */
export const getExamQuestionStatistics = (examId) =>
  fetch(`${BASE}/statistics/exams/${examId}/questions`).then(handle);

/**
 * Bảng điểm học sinh theo bài thi.
 * Response: { examId, examTitle, totalSubmitted, page, limit,
 *             students: [{rank, studentId, score, correctCount, totalQuestions,
 *                        passed, durationMinutes, submittedAt}] }
 */
export const getStudentScores = (examId, page = 1, limit = 100) => {
  const params = new URLSearchParams({ page, limit });
  return fetch(`${BASE}/statistics/exams/${examId}/students?${params}`).then(handle);
};
