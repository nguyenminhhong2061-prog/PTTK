package com.quizapp.submission.repository;

import com.quizapp.submission.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    /** Lấy các event chưa publish, theo thứ tự tạo trước xử lý trước (FIFO) — dùng cho OutboxRelayJob */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status);
}
