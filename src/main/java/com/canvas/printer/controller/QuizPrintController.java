package com.canvas.printer.controller;

import com.canvas.printer.model.PrintableQuiz;
import com.canvas.printer.service.QuizMergerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class QuizPrintController {

    private static final Logger logger = LoggerFactory.getLogger(QuizPrintController.class);
    private final QuizMergerService quizService;

    public QuizPrintController(QuizMergerService quizService) {
        this.quizService = quizService;
    }

    @GetMapping("/print/{courseId}/{quizId}/{assignId}/{submissionId}")
    public String printQuiz(
            @PathVariable String courseId,
            @PathVariable String quizId,
            @PathVariable String assignId,
            @PathVariable String submissionId,
            Model model) {
        logger.info("Request: Quiz {}, Assign {}, Student {}", quizId, assignId, submissionId);

        try {
            PrintableQuiz quizData = quizService.getPrintableQuiz(courseId, quizId, assignId, submissionId);

            if (quizData == null) {
                logger.error("Service returned null.");
                return "error";
            }

            // --- Filter for Review Sheet ---
            var reviewQuestions = quizData.questions().stream()
                    .filter(q -> q.options().stream()
                            .anyMatch(o -> o.isSelectedAndWrong() || (o.isCorrect() && !o.isSelected())))
                    .toList();

            logger.info("Review Sheet: Found {} questions to review.", reviewQuestions.size());

            model.addAttribute("reviewQuestions", reviewQuestions);
            model.addAttribute("quizTitle", quizData.quizTitle());
            model.addAttribute("quizId", quizData.quizId());
            model.addAttribute("studentName", quizData.studentName());
            model.addAttribute("studentId", quizData.studentId());

            // NEW: Metadata fields
            model.addAttribute("startedAt", quizData.startedAt());
            model.addAttribute("finishedAt", quizData.finishedAt());
            model.addAttribute("timeSpent", quizData.timeSpent());
            model.addAttribute("timeLimit", quizData.timeLimit());
            
            model.addAttribute("attempt", quizData.attempt());

            model.addAttribute("score", quizData.score());
            model.addAttribute("questions", quizData.questions());
            model.addAttribute("pointsPossible", quizData.pointsPossible());
            model.addAttribute("timeLimit", quizData.timeLimit());
            model.addAttribute("questionTypes", quizData.questionTypes());

            return "quiz-print-view";

        } catch (Exception e) {
            logger.error("Error generating print view", e);
            return "error";
        }
    }
}