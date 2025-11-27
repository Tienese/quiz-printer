package com.canvas.printer.model;

import java.util.List;

public record PrintableQuiz(
                String quizId,
                String studentId,
                String quizTitle,
                String studentName,
                String score,
                String startedAt, // NEW: Started At
                String finishedAt, // NEW: Finished At
                String timeSpent,
                int attempt,
                long pointsPossible,
                int timeLimit,
                List<String> questionTypes,
                List<PrintableQuestion> questions) {
}