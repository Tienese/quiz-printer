package com.canvas.printer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Service to automate Canvas Quiz submissions for the Test Student.
 */
@Service
public class QuizAutomationService {

    private static final Logger logger = LoggerFactory.getLogger(QuizAutomationService.class);

    private final String canvasUrl;
    private final String apiToken;
    private final HttpClient client;
    private final ObjectMapper mapper;

    public QuizAutomationService(@Value("${canvas.api.url}") String canvasUrl,
            @Value("${canvas.api.token}") String apiToken,
            ObjectMapper mapper) {
        this.canvasUrl = canvasUrl.replaceAll("/$", "");
        this.apiToken = apiToken;
        this.client = HttpClient.newHttpClient();
        this.mapper = mapper;
    }

    /**
     * Main execution method to run a test submission.
     */
    public String runQuizTest(String courseId, String quizId) {
        try {
            logger.info("Starting automated test for Course {} Quiz {}", courseId, quizId);

            // 1. Get Test Student ID
            String testStudentId = getTestStudentId(courseId);
            logger.info("Test Student ID: {}", testStudentId);

            // 2. Start Submission
            JsonNode submission = startQuizSubmission(courseId, quizId, testStudentId);
            long submissionId = submission.path("id").asLong();
            String validationToken = submission.path("validation_token").asText();
            logger.info("Created Submission ID: {}", submissionId);

            // 3. Answer Questions
            answerQuestions(submissionId, validationToken, testStudentId);

            // 4. Complete Quiz
            completeQuiz(courseId, quizId, submissionId, validationToken, testStudentId);

            return "Quiz " + quizId + " completed successfully for Test Student (ID: " + testStudentId + ")";

        } catch (Exception e) {
            logger.error("Automation failed", e);
            // Provide a user-friendly error message if possible
            String msg = e.getMessage();
            if (msg != null && msg.contains("404")) {
                throw new RuntimeException(
                        "Course or Quiz not found (404). Please check: \n1. Your 'canvas.api.url' in application.properties matches your school's domain (e.g. https://yourschool.instructure.com).\n2. The Course ID is correct.");
            }
            throw new RuntimeException("Quiz automation failed: " + e.getMessage(), e);
        }
    }

    private String getTestStudentId(String courseId) throws Exception {
        // POST to this endpoint creates the test student if they don't exist
        String url = canvasUrl + "/api/v1/courses/" + courseId + "/student_view_student";
        JsonNode response = sendRequest(url, "POST", null);
        return String.valueOf(response.path("id").asLong());
    }

    private JsonNode startQuizSubmission(String courseId, String quizId, String asUserId) throws Exception {
        String url = canvasUrl + "/api/v1/courses/" + courseId + "/quizzes/" + quizId + "/submissions?as_user_id="
                + asUserId;
        JsonNode response = sendRequest(url, "POST", null);
        return response.get("submission");
    }

    private void answerQuestions(long submissionId, String validationToken, String asUserId) throws Exception {
        String url = canvasUrl + "/api/v1/quiz_submissions/" + submissionId + "/questions?as_user_id=" + asUserId;
        JsonNode questions = sendRequest(url, "GET", null);

        if (questions.isArray() && questions.size() > 0) {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("validation_token", validationToken);
            ArrayNode quizQuestions = payload.putArray("quiz_questions");

            for (JsonNode q : questions) {
                long qId = q.path("id").asLong();
                ObjectNode answerNode = mapper.createObjectNode();
                answerNode.put("id", qId);

                JsonNode answers = q.path("answers");
                if (answers.isArray() && answers.size() > 0) {
                    JsonNode firstOption = answers.get(0);
                    answerNode.put("answer", firstOption.path("id").asLong());
                } else {
                    answerNode.put("answer", "Automated Test Answer");
                }
                quizQuestions.add(answerNode);
            }

            String postUrl = canvasUrl + "/api/v1/quiz_submissions/" + submissionId + "/questions?as_user_id="
                    + asUserId;
            sendRequest(postUrl, "POST", payload.toString());
            logger.info("Submitted answers for {} questions.", questions.size());
        }
    }

    private void completeQuiz(String courseId, String quizId, long submissionId, String validationToken,
            String asUserId) throws Exception {
        String url = canvasUrl + "/api/v1/courses/" + courseId + "/quizzes/" + quizId + "/submissions/" + submissionId
                + "/complete?as_user_id=" + asUserId;

        ObjectNode payload = mapper.createObjectNode();
        payload.put("attempt", 1);
        payload.put("validation_token", validationToken);

        sendRequest(url, "POST", payload.toString());
        logger.info("Submission {} completed.", submissionId);
    }

    // --- Helper to handle requests and clean up 404 HTML errors ---
    private JsonNode sendRequest(String url, String method, String jsonBody) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json");

        if ("POST".equalsIgnoreCase(method)) {
            if (jsonBody != null) {
                builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            } else {
                builder.POST(HttpRequest.BodyPublishers.noBody());
            }
        } else {
            builder.GET();
        }

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            String body = response.body();
            // Detect if Canvas returned a generic HTML error page (common with 404/500s on
            // wrong domains)
            if (body != null && (body.trim().startsWith("<!DOCTYPE html>") || body.trim().startsWith("<html"))) {
                body = "[HTML Error Page Returned] - Check your URL/Course ID";
            }
            throw new RuntimeException(
                    "Canvas API Error (" + method + " " + url + "): " + response.statusCode() + " " + body);
        }

        return mapper.readTree(response.body());
    }
}