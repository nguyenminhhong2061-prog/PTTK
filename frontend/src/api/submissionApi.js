// Submission Service API — gọi qua Vite proxy /api-sub → http://localhost:5002
const BASE = '/api-sub';

/**
 * Handle response — attach HTTP status vào Error để caller detect được mã lỗi.
 * Ví dụ: err.status === 409 → học sinh đã nộp bài
 */
const handle = async (res) => {
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(data.message || data.error || `HTTP ${res.status}`);
    err.status = res.status; // Attach status code để ExamPage xử lý 409
    err.data = data;
    throw err;
  }
  return data;
};

/** Bắt đầu làm bài / Resume nếu đã có IN_PROGRESS */
export const startExam = (examId, studentId) =>
  fetch(`${BASE}/submissions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ examId, studentId }),
  }).then(handle);

/** Lưu đáp án tạm thời — answers = [{questionId, selectedOption}] */
export const saveAnswers = (submissionId, answers) =>
  fetch(`${BASE}/submissions/${submissionId}/answers`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ answers }),
  }).then(handle);

/**
 * Nộp bài và nhận kết quả chấm điểm.
 * KHÔNG gửi body — Spring Boot reject Content-Type: application/json + empty string body.
 * Controller đã có @RequestBody(required = false) nên hoàn toàn ổn khi không có body.
 */
export const submitExam = (submissionId) =>
  fetch(`${BASE}/submissions/${submissionId}/submit`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
  }).then(handle);

/** Lấy chi tiết một bài nộp */
export const getSubmission = (submissionId) =>
  fetch(`${BASE}/submissions/${submissionId}`).then(handle);

/** Lấy tất cả bài nộp của học sinh */
export const getStudentSubmissions = (studentId) =>
  fetch(`${BASE}/submissions?studentId=${encodeURIComponent(studentId)}`).then(handle);

/**
 * Lấy submission của học sinh theo bài thi cụ thể.
 * Dùng khi ExamPage nhận 409 "đã nộp bài" → tìm submissionId để redirect về ResultPage.
 */
export const getSubmissionByExam = (studentId, examId) =>
  fetch(
    `${BASE}/submissions?studentId=${encodeURIComponent(studentId)}&examId=${examId}`
  ).then(handle);
