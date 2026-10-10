package com.issuetracker.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live updates with Server-Sent Events (SSE).
 *
 * SSE = the browser opens one long-lived HTTP connection and the server pushes text
 * messages down it. Simpler than WebSockets because data only flows server -> browser,
 * which is all a board needs ("someone moved MOB-4, refresh").
 *
 * Scaling note (good interview point): the emitter map lives in THIS server's memory.
 * With 2+ instances you'd fan events out through Redis Pub/Sub or Postgres LISTEN/NOTIFY.
 */
@Component
public class EventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(EventBroadcaster.class);
    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final Map<Long, Set<SseEmitter>> emittersByProject = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long projectId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emittersByProject.computeIfAbsent(projectId, id -> ConcurrentHashMap.newKeySet()).add(emitter);

        Runnable cleanup = () -> remove(projectId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (Exception e) {
            cleanup.run();
        }
        return emitter;
    }

    /**
     * AFTER_COMMIT: only broadcast once the change is really saved in the database.
     * Otherwise a browser could refetch and not see the change yet (or see a rolled-back one).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProjectEvent(ProjectEvent event) {
        Set<SseEmitter> emitters = emittersByProject.get(event.projectId());
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("change").data(event, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                remove(event.projectId(), emitter);
            }
        }
    }

    /** Proxies (and Render) close idle connections, so send a tiny comment every 25s. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        emittersByProject.forEach((projectId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    remove(projectId, emitter);
                }
            }
        });
    }

    public int connectionCount() {
        return emittersByProject.values().stream().mapToInt(Set::size).sum();
    }

    private void remove(Long projectId, SseEmitter emitter) {
        Set<SseEmitter> emitters = emittersByProject.get(projectId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersByProject.remove(projectId, emitters);
            }
        }
        log.debug("SSE client left project {}", projectId);
    }
}
