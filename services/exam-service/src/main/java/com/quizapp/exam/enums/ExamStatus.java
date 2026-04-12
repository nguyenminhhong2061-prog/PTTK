package com.quizapp.exam.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Exam lifecycle status. Persisted as STRING to match MySQL ENUM('DRAFT','PUBLISHED','CLOSED').
 */
public enum ExamStatus {
    DRAFT, PUBLISHED, CLOSED;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static ExamStatus fromJson(String value) {
        if (value == null) {
            return null;
        }
        return ExamStatus.valueOf(value.trim().toUpperCase());
    }
}
