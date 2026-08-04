package com.loghawk.controller;

import com.loghawk.service.QueryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@Controller
public class DashboardController {

    private final QueryService queryService;

    public DashboardController(QueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        Map<String, Object> stats = queryService.getSystemStats();

        model.addAttribute("title", "LogHawk Dashboard");
        model.addAttribute("stats", stats);
        model.addAttribute("totalEntries", stats.get("totalEntries"));
        model.addAttribute("indexTerms", stats.get("indexTerms"));

        Map<String, Object> coordinator =
                (Map<String, Object>) stats.get("coordinator");

        model.addAttribute("totalShards",
                coordinator.get("totalShards"));

        model.addAttribute("activeThreads",
                coordinator.getOrDefault("activeThreads", 0));

        model.addAttribute("completedTasks",
                coordinator.getOrDefault("completedTasks", 0));

        return "dashboard";
    }

    @GetMapping("/search")
    public String searchPage(Model model) {
        model.addAttribute("title", "Search Logs");
        return "search";
    }

    @GetMapping("/benchmarks")
    public String benchmarksPage(Model model) {
        model.addAttribute("title", "Performance Benchmarks");
        return "benchmarks";
    }
}