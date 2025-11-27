package com.canvas.printer.model;

import java.util.List;

public record PrintableQuestion(
                int questionNumber, // NEW: Store the original index (1, 2, 3...)
                String questionHtml,
                List<PrintableOption> options,
                String feedbackText) {
}