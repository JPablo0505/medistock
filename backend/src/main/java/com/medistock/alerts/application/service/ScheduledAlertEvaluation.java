package com.medistock.alerts.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledAlertEvaluation {

    private static final Logger log = LoggerFactory.getLogger(ScheduledAlertEvaluation.class);

    private final EvaluateAlertRulesUseCase evaluateAlertRulesUseCase;

    public ScheduledAlertEvaluation(EvaluateAlertRulesUseCase evaluateAlertRulesUseCase) {
        this.evaluateAlertRulesUseCase = evaluateAlertRulesUseCase;
    }

    @Scheduled(cron = "0 0 6 * * ?") // Run daily at 6 AM
    public void evaluateDaily() {
        log.info("Starting daily alert evaluation for default tenant");
        try {
            var triggered = evaluateAlertRulesUseCase.evaluateAll("default");
            log.info("Daily alert evaluation complete. {} new alerts triggered.", triggered.size());
        } catch (Exception e) {
            log.error("Daily alert evaluation failed", e);
        }
    }
}
