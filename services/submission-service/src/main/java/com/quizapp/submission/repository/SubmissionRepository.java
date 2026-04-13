package com.quizapp.submission.repository;

import com.quizapp.submission.entity.Submission;
import com.quizapp.submission.enums.SubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, String> {

    /** Kiểm tra học sinh đã có submission nào cho bài thi này chưa */
    Optional<Submission> findByStudentIdAndExamId(String studentId, Long examId);

    /** Lọc theo examId và status (Statistics Service dùng) */
    List<Submission> findByExamIdAndStatus(Long examId, SubmissionStatus status);

    /** Xem lịch sử làm bài của học sinh */
    List<Submission> findByStudentId(String studentId);

    /** Lấy tất cả submission theo examId */
    List<Submission> findByExamId(Long examId);
}
