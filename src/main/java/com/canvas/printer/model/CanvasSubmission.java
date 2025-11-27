package com.canvas.printer.model;

public record CanvasSubmission(
        long id,
        long user_id,
        double score,
        String workflow_state // e.g., "graded", "submitted"
) {
}