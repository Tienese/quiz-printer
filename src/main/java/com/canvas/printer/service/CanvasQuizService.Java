import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to automate Canvas Quiz submissions for a Test Student.
 * * REQUIREMENTS:
 * 1. The API Token used must have Account Admin permissions to use 'as_user_id'.
 * 2. This example targets "Classic Quizzes". "New Quizzes" uses a different, more complex LTI-based API.
 */
@Service
public class CanvasQuizService {

    @Value("${canvas.api.url}")
    private String canvasBaseUrl;

    @Value("${canvas.api.token}")
    private String apiToken;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Main workflow to run the test submission.
     */
    public void runQuizTest(String courseId, String quizId) {
        // Step 1: Find the "Test Student" User ID for this course
        String testStudentId = getTestStudentId(courseId);
        System.out.println("Found Test Student ID: " + testStudentId);

        // Step 2: Start the Quiz Submission (Masquerading as Test Student)
        Map<String, Object> submission = startQuizSubmission(courseId, quizId, testStudentId);
        Long submissionId = ((Number) submission.get("id")).longValue();
        System.out.println("Started Submission ID: " + submissionId);

        // Step 3: Answer Questions (Optional - requires mapping Question IDs to Answer IDs)
        // For this example, we will submit a blank or partial attempt to prove connectivity.
        // submitAnswers(submissionId, submission, testStudentId);

        // Step 4: Complete/Turn-in the Quiz
        completeQuiz(courseId, quizId, submissionId, testStudentId);
        System.out.println("Quiz Completed Successfully for Test Student.");
    }

    /**
     * Step 1: Get the Test Student's internal ID.
     * The Test Student is a real user in the DB, usually named "Test Student".
     */
    private String getTestStudentId(String courseId) {
        String url = canvasBaseUrl + "/courses/" + courseId + "/users";
        
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("enrollment_type[]", "StudentEnrollment")
                .queryParam("search_term", "Test Student"); // Filter by name

        ResponseEntity<List> response = restTemplate.exchange(
                builder.toUriString(), 
                HttpMethod.GET, 
                createHeaders(), 
                List.class
        );

        List<Map<String, Object>> users = response.getBody();
        if (users == null || users.isEmpty()) {
            throw new RuntimeException("Test Student not found. Ensure you have activated Student View at least once in the UI.");
        }
        
        // Return the ID of the first match
        return String.valueOf(users.get(0).get("id"));
    }

    /**
     * Step 2: POST /courses/:course_id/quizzes/:quiz_id/submissions
     * Starts the clock and creates the submission entry.
     */
    private Map<String, Object> startQuizSubmission(String courseId, String quizId, String asUserId) {
        String url = canvasBaseUrl + "/courses/" + courseId + "/quizzes/" + quizId + "/submissions";
        
        // Add as_user_id to force action as the Test Student
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("as_user_id", asUserId);

        ResponseEntity<Map> response = restTemplate.exchange(
                builder.toUriString(), 
                HttpMethod.POST, 
                createHeaders(), 
                Map.class
        );
        
        return (Map<String, Object>) response.getBody().get("submission");
    }

    /**
     * Step 3: Answer Questions (Conceptual)
     * To answer, you need the 'validation_token' from the submission response
     * and the specific Question IDs and Answer IDs (which you get from GET /quizzes/:id/questions).
     */
    private void submitAnswers(Long submissionId, Map<String, Object> submissionData, String asUserId) {
        String url = canvasBaseUrl + "/quiz_submissions/" + submissionId + "/questions";
        
        // Logic to construct question answers payload:
        // {
        //   "validation_token": "...",
        //   "quiz_questions": [{ "id": 123, "answer": 456 }]
        // }
        // This is complex as it requires parsing the quiz structure first.
    }

    /**
     * Step 4: POST /courses/:course_id/quizzes/:quiz_id/submissions/:id/complete
     * Officially "Turns in" the quiz.
     */
    private void completeQuiz(String courseId, String quizId, Long submissionId, String asUserId) {
        String url = canvasBaseUrl + "/courses/" + courseId + "/quizzes/" + quizId + "/submissions/" + submissionId + "/complete";

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("as_user_id", asUserId)
                .queryParam("attempt", 1) 
                .queryParam("validation_token", "XXX"); // Often required if checking answers, sometimes optional depending on quiz settings

        restTemplate.exchange(
                builder.toUriString(), 
                HttpMethod.POST, 
                createHeaders(), 
                Map.class
        );
    }

    private HttpEntity<String> createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiToken);
        headers.set("Content-Type", "application/json");
        return new HttpEntity<>(headers);
    }
}