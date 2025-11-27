package com.canvas.printer.service;

import com.canvas.printer.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuizMergerService {

    private static final Logger logger = LoggerFactory.getLogger(QuizMergerService.class);
    private final CanvasApiService apiService;
    private final ObjectMapper mapper;

    public QuizMergerService(CanvasApiService apiService, ObjectMapper mapper) {
        this.apiService = apiService;
        this.mapper = mapper;
    }

    public PrintableQuiz getPrintableQuiz(String courseId, String quizId, String assignId, String studentId) {
        try {
            // 1. Fetch Data
            String questionsJson = apiService.getQuizQuestionsJson(courseId, quizId);
            String submissionJson = apiService.getSubmissionJson(courseId, assignId, studentId);
            List<CanvasUser> users = apiService.getCourseUsers(courseId);

            CanvasQuiz quizDetails = apiService.getQuiz(courseId, quizId);
            // Find the name
            String studentName = users.stream()
                    .filter(u -> String.valueOf(u.id()).equals(studentId))
                    .findFirst()
                    .map(CanvasUser::name)
                    .orElse("Student ID: " + studentId); // Fallback
            // 2. Parse Trees
            JsonNode questionsRoot = mapper.readTree(questionsJson);
            JsonNode submissionNode = mapper.readTree(submissionJson);

            // 3. Extract Answer Data (Robust Fallback Logic)
            JsonNode submissionData = null;
            if (submissionNode.has("submission_history")) {
                JsonNode history = submissionNode.get("submission_history");
                if (history != null && history.isArray() && history.size() > 0) {
                    JsonNode latestAttempt = history.get(history.size() - 1);
                    if (latestAttempt.has("submission_data")) {
                        submissionData = latestAttempt.get("submission_data");
                    }
                }
            }

            // Fallback 1: Check root
            if (submissionData == null && submissionNode.has("submission_data")) {
                submissionData = submissionNode.get("submission_data");
            }

            // Fallback 2: Empty
            if (submissionData == null) {
                logger.warn("No submission data found for student " + studentId + ". Printing blank quiz.");
                submissionData = mapper.createArrayNode();
            }

            // 4. Metadata
            String score = submissionNode.path("score").asText("0");
            String quizTitle = quizDetails.title();
            int timeLimit = quizDetails.time_limit();
            List<String> questionTypes = quizDetails.question_types();
            long pointsPossible = quizDetails.points_possible();
            
            /*
             * long points_possible,
             * List<String> question_types) {
             */

            // 5. Merge Loop
            List<PrintableQuestion> mergedQuestions = new ArrayList<>();
            if (questionsRoot.isArray()) {
                int index = 1; // Start counting for question numbering
                for (JsonNode qNode : questionsRoot) {
                    mergedQuestions.add(processQuestion(qNode, submissionData, index++));
                }
            }

            return new PrintableQuiz(quizId, studentId, quizTitle, studentName, score, pointsPossible, timeLimit,
                    questionTypes,
                    mergedQuestions);

        } catch (Exception e) {
            logger.error("Failed to merge quiz", e);
            return null;
        }
    }

    private PrintableQuestion processQuestion(JsonNode qNode, JsonNode submissionData, int questionNumber) {
        long qId = qNode.path("id").asLong();
        String questionText = qNode.path("question_text").asText("Question");
        String qType = qNode.path("question_type").asText("unknown");

        // Extract Feedback (General)
        // Prioritize "neutral_comments" (General Feedback)
        String feedback = qNode.path("neutral_comments").asText(null);
        if (feedback == null || feedback.trim().isEmpty()) {
            // Fallback to "correct_comments" if general is missing (common in some Canvas
            // quizzes)
            feedback = qNode.path("correct_comments").asText(null);
        }
        // Sanitize empty strings to null
        if (feedback != null && feedback.trim().isEmpty()) {
            feedback = null;
        }

        // A. Find Student Answer Node
        JsonNode studentAnswerNode = null;
        if (submissionData != null && submissionData.isArray()) {
            for (JsonNode answer : submissionData) {
                if (answer.path("question_id").asLong() == qId) {
                    studentAnswerNode = answer;
                    break;
                }
            }
        }

        // B. Process Options based on Type
        List<PrintableOption> printableOptions = new ArrayList<>();

        if ("matching_question".equals(qType)) {
            printableOptions = processMatchingOptions(qNode, studentAnswerNode);
        } else if ("multiple_dropdowns_question".equals(qType)) {
            printableOptions = processDropdownOptions(qNode, studentAnswerNode);
        } else {
            // Standard MC / Multiple Answer / True False
            printableOptions = processStandardOptions(qNode, studentAnswerNode, qType);
        }

        // C. Determine if Unanswered
        // A question is unanswered if NO options are selected by the student
        boolean isUnanswered = printableOptions.stream().noneMatch(PrintableOption::isSelected);

        // Special check for Matching/Dropdowns where "No Answer" text might be generated
        // (This logic might need tuning depending on how strict you want "unanswered" to be for complex types)
        
        return new PrintableQuestion(questionNumber, questionText, printableOptions, feedback, isUnanswered);
    }

    // --- 1. Matching Questions Logic ---
    private List<PrintableOption> processMatchingOptions(JsonNode qNode, JsonNode studentAnswerNode) {
        List<PrintableOption> options = new ArrayList<>();

        Map<Long, String> matchMap = new HashMap<>();
        JsonNode matches = qNode.get("matches");
        if (matches != null) {
            for (JsonNode m : matches) {
                matchMap.put(m.path("match_id").asLong(), m.path("text").asText());
            }
        }

        JsonNode answers = qNode.get("answers");
        if (answers != null) {
            for (JsonNode ans : answers) {
                long answerId = ans.path("id").asLong();
                String leftText = ans.path("text").asText();
                long correctMatchId = ans.path("match_id").asLong();
                String correctRightText = matchMap.getOrDefault(correctMatchId, "[Unknown]");

                boolean isSelected = false;
                boolean isCorrect = false;
                String studentChoiceText = " (No Answer)";

                if (studentAnswerNode != null) {
                    String key = "answer_" + answerId;
                    if (studentAnswerNode.has(key)) {
                        long studentMatchId = studentAnswerNode.get(key).asLong();
                        if (studentMatchId > 0) {
                            isSelected = true;
                            studentChoiceText = matchMap.getOrDefault(studentMatchId, "[Unknown ID]");
                            if (studentMatchId == correctMatchId) {
                                isCorrect = true;
                            }
                        }
                    }
                }

                String displayText = leftText + " = " + correctRightText;
                boolean isSelectedAndWrong = isSelected && !isCorrect;

                if (isSelectedAndWrong) {
                    displayText = leftText + " = " + studentChoiceText + " (Expected: " + correctRightText + ")";
                }

                options.add(new PrintableOption(displayText, true, isSelected && isCorrect, isSelectedAndWrong, null));
            }
        }
        return options;
    }

    // --- 2. Dropdown Questions Logic ---
    private List<PrintableOption> processDropdownOptions(JsonNode qNode, JsonNode studentAnswerNode) {
        List<PrintableOption> options = new ArrayList<>();
        JsonNode answers = qNode.get("answers");

        if (answers != null) {
            for (JsonNode ans : answers) {
                long id = ans.path("id").asLong();
                String text = ans.path("text").asText();
                String blankId = ans.path("blank_id").asText("");
                int weight = ans.path("weight").asInt(0);

                String displayText = "[" + blankId + "] " + text;

                boolean isCorrectKey = weight > 0;
                boolean isSelected = false;

                if (studentAnswerNode != null) {
                    if (studentAnswerNode.toString().contains(String.valueOf(id))) {
                        isSelected = true;
                    }
                }

                boolean isSelectedAndWrong = isSelected && !isCorrectKey;
                options.add(new PrintableOption(displayText, isCorrectKey, isSelected, isSelectedAndWrong, null));
            }
        }
        return options;
    }

    // --- 3. Standard MC/MA/TF Logic ---
    private List<PrintableOption> processStandardOptions(JsonNode qNode, JsonNode studentAnswerNode, String qType) {
        List<PrintableOption> options = new ArrayList<>();
        JsonNode answers = qNode.get("answers");

        if (answers != null) {
            for (JsonNode ans : answers) {
                long optId = ans.path("id").asLong();
                String text = ans.path("text").asText();
                // SAFETY: Default weight to 0 if missing
                int weight = ans.path("weight").asInt(0);

                // --- THE FIX: Sanitize Feedback ---
                String comments = ans.path("comments").asText(null);
                if (comments != null && comments.trim().isEmpty()) {
                    comments = null;
                }

                boolean isCorrectKey = (weight > 0);
                boolean isSelected = false;

                if (studentAnswerNode != null) {
                    if (qType.equals("multiple_answers_question")) {
                        // FIX: Check value is "1" (Selected) vs "0" (Unselected)
                        String key = "answer_" + optId;
                        if (studentAnswerNode.has(key)) {
                            String val = studentAnswerNode.get(key).asText();
                            if ("1".equals(val)) {
                                isSelected = true;
                            }
                        }
                    } else {
                        // Standard MC: answer_id matches option ID
                        if (studentAnswerNode.has("answer_id")
                                && studentAnswerNode.path("answer_id").asLong() == optId) {
                            isSelected = true;
                        }
                    }
                }

                boolean isSelectedAndWrong = isSelected && !isCorrectKey;
                options.add(new PrintableOption(text, isCorrectKey, isSelected, isSelectedAndWrong, comments));
            }
        }
        return options;
    }
}