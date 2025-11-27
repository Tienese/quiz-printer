package com.canvas.printer.model;

import java.util.List;

public record PrintableQuestion(
        int questionNumber,
        String questionHtml,
        List<PrintableOption> options,
        String feedbackText,
        boolean isUnanswered // NEW: Track if the question was skipped
) {
}