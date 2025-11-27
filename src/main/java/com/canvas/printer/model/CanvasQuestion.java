package com.canvas.printer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class CanvasQuestion {

    private Long id;

    @JsonProperty("question_name")
    private String questionName;

    @JsonProperty("question_text")
    private String questionText;

    @JsonProperty("question_type")
    private String questionType;

    @JsonProperty("points_possible")
    private Double pointsPossible;

    private List<Answer> answers;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getQuestionName() {
        return questionName;
    }

    public void setQuestionName(String questionName) {
        this.questionName = questionName;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(String questionType) {
        this.questionType = questionType;
    }

    public Double getPointsPossible() {
        return pointsPossible;
    }

    public void setPointsPossible(Double pointsPossible) {
        this.pointsPossible = pointsPossible;
    }

    public List<Answer> getAnswers() {
        return answers;
    }

    public void setAnswers(List<Answer> answers) {
        this.answers = answers;
    }

    // Inner class to map the answers array in the JSON
    public static class Answer {
        private String text;
        
        // Canvas uses 'weight' to determine correctness (100 = correct, 0 = incorrect)
        private Double weight; 
        
        private Long id;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public Double getWeight() {
            return weight;
        }

        public void setWeight(Double weight) {
            this.weight = weight;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }
}