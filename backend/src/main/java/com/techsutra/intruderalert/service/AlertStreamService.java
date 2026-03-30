package com.techsutra.intruderalert.service;

import com.techsutra.intruderalert.model.AlertRecord;
import com.techsutra.intruderalert.model.AlertStreamEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AlertStreamService {
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        sendEvent(emitter, "connected", null);
        return emitter;
    }

    public void publish(String type, AlertRecord alert) {
        AlertStreamEvent event = new AlertStreamEvent(type, alert);
        for (SseEmitter emitter : emitters) {
            sendEvent(emitter, "alert", event);
        }
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException exception) {
            emitter.complete();
            emitters.remove(emitter);
        }
    }
}
