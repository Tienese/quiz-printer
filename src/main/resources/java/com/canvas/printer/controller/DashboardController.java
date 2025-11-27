package com.canvas.printer.controller;

import com.canvas.printer.service.CanvasApiService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class DashboardController {

    private final CanvasApiService apiService;
    // UPDATE THIS TO YOUR REAL COURSE ID
    private final String DEFAULT_COURSE_ID = "13295775";

    public DashboardController(CanvasApiService apiService) {
        this.apiService = apiService;
    }

    @GetMapping("/")
    public String listQuizzes(Model model) {
        var quizzes = apiService.getQuizzes(DEFAULT_COURSE_ID);
        model.addAttribute("quizzes", quizzes);
        model.addAttribute("courseId", DEFAULT_COURSE_ID);
        return "dashboard-quizzes";
    }

    @GetMapping("/course/{courseId}/quiz/{quizId}/assignment/{assignId}/submissions")
    public String listSubmissions(@PathVariable String courseId,
            @PathVariable String quizId,
            @PathVariable String assignId,
            Model model) {
        var submissions = apiService.getSubmissions(courseId, assignId);
        model.addAttribute("submissions", submissions);
        model.addAttribute("courseId", courseId);
        model.addAttribute("quizId", quizId);
        model.addAttribute("assignId", assignId);
        return "dashboard-submissions";
    }
}