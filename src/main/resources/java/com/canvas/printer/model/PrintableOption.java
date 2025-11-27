package com.canvas.printer.model;

public record PrintableOption(
    String text,
    boolean isCorrect,
    boolean isSelected,
    boolean isSelectedAndWrong,
    String feedback // NEW: Specific feedback for this individual choice
) {}