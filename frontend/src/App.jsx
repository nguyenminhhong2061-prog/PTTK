import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import HomePage from './pages/HomePage.jsx';
import StudentDashboard from './pages/student/StudentDashboard.jsx';
import ExamPage from './pages/student/ExamPage.jsx';
import ResultPage from './pages/student/ResultPage.jsx';
import TeacherDashboard from './pages/teacher/TeacherDashboard.jsx';
import ExamStatsPage from './pages/teacher/ExamStatsPage.jsx';

/** Route bảo vệ: yêu cầu user đã đăng nhập đúng role */
function ProtectedRoute({ children, role }) {
  const user = JSON.parse(sessionStorage.getItem('quizUser') || 'null');
  if (!user) return <Navigate to="/" replace />;
  if (role && user.role !== role) return <Navigate to="/" replace />;
  return children;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage />} />

        {/* ── Student routes ── */}
        <Route path="/student" element={
          <ProtectedRoute role="student"><StudentDashboard /></ProtectedRoute>
        } />
        <Route path="/student/exam/:examId" element={
          <ProtectedRoute role="student"><ExamPage /></ProtectedRoute>
        } />
        <Route path="/student/result/:submissionId" element={
          <ProtectedRoute role="student"><ResultPage /></ProtectedRoute>
        } />

        {/* ── Teacher routes ── */}
        <Route path="/teacher" element={
          <ProtectedRoute role="teacher"><TeacherDashboard /></ProtectedRoute>
        } />
        <Route path="/teacher/exams/:examId/stats" element={
          <ProtectedRoute role="teacher"><ExamStatsPage /></ProtectedRoute>
        } />

        {/* Fallback */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
