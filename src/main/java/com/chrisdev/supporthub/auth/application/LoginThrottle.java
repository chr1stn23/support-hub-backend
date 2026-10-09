package com.chrisdev.supporthub.auth.application;

import com.chrisdev.supporthub.auth.infrastructure.SessionProperties;
import com.chrisdev.supporthub.common.exception.TooManyAttemptsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Limita los intentos fallidos por correo y por IP en una ventana de tiempo. Vive en memoria:
 * con varias instancias del backend cada una cuenta por separado.
 */
@Component
public class LoginThrottle {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final SessionProperties.Throttle config;
    private final Clock clock;
    private final ConcurrentMap<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    @Autowired
    public LoginThrottle(SessionProperties props) {
        this(props, Clock.systemUTC());
    }

    public LoginThrottle(SessionProperties props, Clock clock) {
        this.config = props.throttle();
        this.clock = clock;
    }

    private static String emailKey(String email) {
        return "e:" + email.trim().toLowerCase(Locale.ROOT);
    }

    private static String ipKey(String ip) {
        return "i:" + ip;
    }

    /**
     * Lanza TooManyAttemptsException si el correo o la IP ya agotaron sus intentos.
     */
    public void check(String email, String ip) {
        Instant now = clock.instant();
        Optional<Duration> wait = retryAfter(emailKey(email), config.maxFailuresPerEmail(), now)
                .or(() -> retryAfter(ipKey(ip), config.maxFailuresPerIp(), now));
        wait.ifPresent(duration -> {
            throw new TooManyAttemptsException(Math.max(1, duration.toSeconds()));
        });
    }

    public void recordFailure(String email, String ip) {
        Instant now = clock.instant();
        add(emailKey(email), now);
        add(ipKey(ip), now);
    }

    public void reset(String email) {
        failures.remove(emailKey(email));
    }

    private Optional<Duration> retryAfter(String key, int max, Instant now) {
        Deque<Instant> attempts = failures.get(key);
        if (attempts == null) {
            return Optional.empty();
        }
        synchronized (attempts) {
            prune(attempts, now);
            if (attempts.size() < max) {
                return Optional.empty();
            }
            return Optional.of(Duration.between(now, attempts.peekFirst().plus(window())));
        }
    }

    private void add(String key, Instant now) {
        if (failures.size() >= MAX_TRACKED_KEYS) {
            evictExpired(now);
            if (failures.size() >= MAX_TRACKED_KEYS && !failures.containsKey(key)) {
                return;   // memoria acotada: ante un ataque masivo no se siguen claves nuevas
            }
        }
        Deque<Instant> attempts = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts, now);
            attempts.addLast(now);
        }
    }

    private void prune(Deque<Instant> attempts, Instant now) {
        Instant limit = now.minus(window());
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(limit)) {
            attempts.removeFirst();
        }
    }

    private void evictExpired(Instant now) {
        failures.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                prune(entry.getValue(), now);
                return entry.getValue().isEmpty();
            }
        });
    }

    private Duration window() {
        return Duration.ofMinutes(config.windowMinutes());
    }
}
