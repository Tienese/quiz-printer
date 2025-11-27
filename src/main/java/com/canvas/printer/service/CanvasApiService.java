package com.canvas.printer.service;

import com.canvas.printer.model.CanvasQuiz;
import com.canvas.printer.model.CanvasSubmission;
import com.canvas.printer.model.CanvasUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Service
public class CanvasApiService {

    private final String canvasUrl;
    private final String apiToken;
    private final HttpClient client;
    private final ObjectMapper mapper;

    public CanvasApiService(@Value("${canvas.api.url}") String canvasUrl,
            @Value("${canvas.api.token}") String apiToken,
            ObjectMapper mapper) {
        this.canvasUrl = canvasUrl.replaceAll("/$", "");
        this.apiToken = apiToken;
        this.client = HttpClient.newHttpClient();
        this.mapper = mapper;
    }

    // 1. Get List of Quizzes (This returns the assignment_id we need)
    public List<CanvasQuiz> getQuizzes(String courseId) {
        String url = canvasUrl + "/api/v1/courses/" + courseId + "/quizzes?per_page=100";
        return fetchList(url, new TypeReference<>() {
        });
    }

    // 2. Get Single Quiz (NEW: To get the Title)
    public CanvasQuiz getQuiz(String courseId, String quizId) {
        String url = canvasUrl + "/api/v1/courses/" + courseId + "/quizzes/" + quizId;
        try {
            String json = fetchRaw(url);
            return mapper.readValue(json, CanvasQuiz.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch quiz details", e);
        }
    }

    // 2. Get List of Submissions (Using ASSIGNMENT Endpoint)
    public List<CanvasSubmission> getSubmissions(String courseId, String assignmentId) {
        // The Assignment endpoint returns a clean list of submissions
        String url = canvasUrl + "/api/v1/courses/" + courseId + "/assignments/" + assignmentId
                + "/submissions?per_page=100";
        return fetchList(url, new TypeReference<>() {
        });
    }

    // 3. Get Quiz Questions (Definitions)
    public String getQuizQuestionsJson(String courseId, String quizId) {
        String url = canvasUrl + "/api/v1/courses/" + courseId + "/quizzes/" + quizId + "/questions?per_page=100";
        return fetchRaw(url);
    }

    // 4. Get Single Student Submission (Using ASSIGNMENT Endpoint for rich data)
    public String getSubmissionJson(String courseId, String assignmentId, String studentId) {
        String url = canvasUrl + "/api/v1/courses/" + courseId + "/assignments/" + assignmentId + "/submissions/"
                + studentId + "?include[]=submission_history";
        return fetchRaw(url);
    }

    // 5. Get All Users in Course (To map ID -> Name)
    public List<CanvasUser> getCourseUsers(String courseId) {
        // enrollment_type[]=student ensures we only get students
        String url = canvasUrl + "/api/v1/courses/" + courseId + "/users?enrollment_type[]=student&per_page=100";
        return fetchList(url, new TypeReference<>() {
        });
    }

    // 6. Get Quiz Submission (NEW: Specifically for Time Data)
    // This endpoint returns the specific Quiz Submission object which contains
    // started_at and finished_at
    public String getQuizSubmissionJson(String courseId, String quizId, String studentId) {
        // The endpoint to list quiz submissions, filtered by user_id
        String url = canvasUrl + "/api/v1/courses/" + courseId + "/quizzes/" + quizId +
                "/submissions?user_ids[]=" + studentId;
        return fetchRaw(url);
    }

    // --- Helpers ---

    private <T> List<T> fetchList(String url, TypeReference<List<T>> typeRef) {
        try {
            String json = fetchRaw(url);
            return mapper.readValue(json, typeRef);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse list: " + e.getMessage(), e);
        }
    }

    private String fetchRaw(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + apiToken)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Canvas API Error " + response.statusCode() + ": " + response.body());
            }
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("HTTP Request Failed", e);
        }
    }
}