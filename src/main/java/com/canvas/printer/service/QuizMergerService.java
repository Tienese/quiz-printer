package com.canvas.printer.service;

import com.canvas.printer.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuizMergerService {

    private static final Logger logger = LoggerFactory.getLogger(QuizMergerService.class);
    private final CanvasApiService apiService;
    private final ObjectMapper mapper;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public QuizMergerService(CanvasApiService apiService, ObjectMapper mapper) {
        this.apiService = apiService;
        this.mapper = mapper;
    }

    public PrintableQuiz getPrintableQuiz(String courseId, String quizId, String assignId, String studentId) {
        try {
            // 1. Fetch Data
            String questionsJson = apiService.getQuizQuestionsJson(courseId, quizId);
            // Keep the assignment submission JSON for answer data (submission_history)
            String submissionJson = apiService.getSubmissionJson(courseId, assignId, studentId);
            // NEW: Fetch Quiz Submission specifically for time metadata
            String quizSubmissionJson = apiService.getQuizSubmissionJson(courseId, quizId, studentId);

            List<CanvasUser> users = apiService.getCourseUsers(courseId);
            CanvasQuiz quizDetails = apiService.getQuiz(courseId, quizId);

            // Find the student user object
            CanvasUser studentUser = users.stream()
                    .filter(u -> String.valueOf(u.id()).equals(studentId))
                    .findFirst()
                    .orElse(null);

            String studentName = (studentUser != null) ? studentUser.name() : "Student ID: " + studentId;

            // 2. Parse Trees
            JsonNode questionsRoot = mapper.readTree(questionsJson);
            JsonNode submissionNode = mapper.readTree(submissionJson);
            JsonNode quizSubmissionRoot = mapper.readTree(quizSubmissionJson);

            // 3. Extract Metadata from Quiz Submission (New API Call)
            String startedAtStr = "N/A";
            String finishedAtStr = "N/A";
            String timeSpent = "N/A";

            // The quiz submission API returns { "quiz_submissions": [ ... ] }
            if (quizSubmissionRoot.has("quiz_submissions")) {
                JsonNode quizSubs = quizSubmissionRoot.get("quiz_submissions");
                if (quizSubs.isArray() && quizSubs.size() > 0) {
                    JsonNode quizSub = quizSubs.get(0); // Get the first (and should be only) one for this user

                    if (quizSub.has("started_at") && !quizSub.get("started_at").isNull()) {
                        Instant start = Instant.parse(quizSub.get("started_at").asText());
                        startedAtStr = formatter.format(start);
                    }

                    if (quizSub.has("finished_at") && !quizSub.get("finished_at").isNull()) {
                        Instant finish = Instant.parse(quizSub.get("finished_at").asText());
                        finishedAtStr = formatter.format(finish);
                    }
                    if (quizSub.has("time_spent") && !quizSub.get("time_spent").isNull()) {
                        int timeSpentInSecond = quizSub.get("time_spent").asInt();
                        Duration duration = Duration.ofSeconds(timeSpentInSecond);

                        long hours = duration.toHours();
                        long minutes = duration.toMinutesPart();
                        long seconds = duration.toSecondsPart();

                        timeSpent = String.format("%d:%02d:%02d", hours, minutes, seconds);
                    }
                }
            }

            // 4. Extract Answer Data & Attempt from Assignment Submission
            JsonNode submissionData = null;
            int attempt = 0;

            if (submissionNode.has("submission_history")) {
                JsonNode history = submissionNode.get("submission_history");
                if (history != null && history.isArray() && history.size() > 0) {
                    // Get latest attempt for answers
                    JsonNode latestAttempt = history.get(history.size() - 1);

                    if (latestAttempt.has("submission_data")) {
                        submissionData = latestAttempt.get("submission_data");
                    }

                    attempt = latestAttempt.path("attempt").asInt(0);
                }
            }

            // Fallback for submission_data if not in history
            if (submissionData == null && submissionNode.has("submission_data")) {
                submissionData = submissionNode.get("submission_data");
            }

            // Fallback for submission data content
            if (submissionData == null) {
                logger.warn("No submission data found for student " + studentId + ". Printing blank quiz.");
                submissionData = mapper.createArrayNode();
            }

            // 5. Metadata
            String score = submissionNode.path("score").asText("0");
            String quizTitle = quizDetails.title();
            int timeLimit = quizDetails.time_limit();
            List<String> questionTypes = quizDetails.question_types();
            long pointsPossible = quizDetails.points_possible();

            // 6. Merge Loop
            List<PrintableQuestion> mergedQuestions = new ArrayList<>();
            if (questionsRoot.isArray()) {
                int index = 1; // Start counting for question numbering
                for (JsonNode qNode : questionsRoot) {
                    mergedQuestions.add(processQuestion(qNode, submissionData, index++));
                }
            }

            return new PrintableQuiz(quizId, studentId, quizTitle, studentName, score,
                    startedAtStr, finishedAtStr, timeSpent, attempt,
                    pointsPossible, timeLimit, questionTypes, mergedQuestions);

        } catch (Exception e) {
            logger.error("Failed to merge quiz", e);
            return null;
        }
    }

    private PrintableQuestion processQuestion(JsonNode qNode, JsonNode submissionData, int questionNumber) {
        long qId = qNode.path("id").asLong();
        String questionText = qNode.path("question_text").asText("Question");
        String qType = qNode.path("question_type").asText("unknown");

        // Extract Feedback
        String feedback = qNode.path("neutral_comments").asText(null);
        if (feedback == null || feedback.trim().isEmpty()) {
            feedback = qNode.path("correct_comments").asText(null);
        }
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
            printableOptions = processStandardOptions(qNode, studentAnswerNode, qType);
        }

        // C. Determine if Unanswered
        boolean isUnanswered = printableOptions.stream().noneMatch(PrintableOption::isSelected);

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