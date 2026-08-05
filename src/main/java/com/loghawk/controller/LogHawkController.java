package com.loghawk.controller;

import com.loghawk.coordinator.QueryCoordinator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class LogHawkController {
    private final QueryCoordinator queryCoordinator;
    private final LogIndexer logIndexer;

}
