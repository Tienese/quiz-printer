package com.canvas.printer.controller;

import com.canvas.printer.service.QuizAutomationService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Controller to trigger the Test Student automation.
 */
@Controller
public class QuizAutomationController {

    private final QuizAutomationService automationService;

    public QuizAutomationController(QuizAutomationService automationService) {
        this.automationService = automationService;
    }

    /**
     * Serves the automation UI page.
     * URL: /automation
     */
    @GetMapping("/automation")
    public String showAutomationPage() {
        return "quiz-automation";
    }

    /**
     * API Endpoint to force the "Test Student" to take a quiz.
     * Usage: POST /api/automate/course/123/quiz/456
     */
    @PostMapping("/api/automate/course/{courseId}/quiz/{quizId}")
    @ResponseBody
    public ResponseEntity<String> runTestSubmission(@PathVariable String courseId, @PathVariable String quizId) {
        try {
            String result = automationService.runQuizTest(courseId, quizId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error running automation: " + e.getMessage());
        }
    }
}