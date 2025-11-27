package com.canvas.printer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CanvasUser(
    long id,
    String name,
    String sortable_name // e.g., "Nguyen, Van"
) {}