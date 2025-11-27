package com.canvas.printer.model;

import java.util.List;

public record PrintableQuiz(
        String quizId,
        String studentId,
        String quizTitle,
        String studentName,
        String score,
        long pointsPossible,
        int timeLimit,
        List<String> questionTypes,
        List<PrintableQuestion> questions) {
}