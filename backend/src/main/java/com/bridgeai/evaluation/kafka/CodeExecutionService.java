package com.bridgeai.evaluation.kafka;

import com.bridgeai.evaluation.websocket.TelemetryWebSocketHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class CodeExecutionService {

    private final TelemetryWebSocketHandler webSocketHandler;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public CodeExecutionService(TelemetryWebSocketHandler webSocketHandler, KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.webSocketHandler = webSocketHandler;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "code-submissions", groupId = "evaluation-group")
    public void processSubmission(String payload, @Header(KafkaHeaders.RECEIVED_KEY) String sessionId) {
        try {
            // Read incoming
            Map<String, Object> inputData = objectMapper.readValue(payload, Map.class);
            String userId = (String) inputData.getOrDefault("user_id", "default_user");

            // Mock execution steps
            sendStatus(sessionId, "Compiling...");
            Thread.sleep(500);
            sendStatus(sessionId, "Running Test Cases...");
            Thread.sleep(1000);
            sendStatus(sessionId, "Calculating Latency...");
            Thread.sleep(500);

            // Mock metrics
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("user_id", userId);
            metrics.put("session_id", sessionId);
            metrics.put("Execution Time", "42ms");
            metrics.put("Memory", "128MB");
            metrics.put("CPU", "14%");
            metrics.put("Accuracy", "96.4%");

            String metricsJson = objectMapper.writeValueAsString(metrics);
            kafkaTemplate.send("code_evaluation_telemetry", sessionId, metricsJson);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendStatus(String sessionId, String status) {
        webSocketHandler.sendMessage(sessionId, status);
    }
}
