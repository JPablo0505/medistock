package com.medistock.alerts.application.service;

import com.medistock.alerts.domain.exception.AlertInstanceNotFoundException;
import com.medistock.alerts.domain.model.AlertInstance;
import com.medistock.alerts.domain.port.AlertInstanceRepository;

import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class ResolveAlertUseCase {

    private final AlertInstanceRepository alertInstanceRepository;

    public ResolveAlertUseCase(AlertInstanceRepository alertInstanceRepository) {
        this.alertInstanceRepository = alertInstanceRepository;
    }

    public AlertInstance execute(UUID instanceId) {
        AlertInstance instance = alertInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new AlertInstanceNotFoundException(instanceId));
        instance.resolve();
        return alertInstanceRepository.save(instance);
    }
}
