package com.canvas.printer.model;

import java.util.List;

public record CanvasQuiz(
        long id,
        String title,
        int question_count,
        long assignment_id, // NEW: The magic link
        long points_possible,
        int time_spent,
        int time_limit,
        List<String> question_types) {
}