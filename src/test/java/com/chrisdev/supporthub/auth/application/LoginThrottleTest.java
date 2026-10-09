package com.chrisdev.supporthub.auth.application;

import com.chrisdev.supporthub.auth.infrastructure.SessionProperties;
import com.chrisdev.supporthub.common.exception.TooManyAttemptsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.*;

public class LoginThrottleTest {

    private MutableClock clock;
    private LoginThrottle throttle;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        var props = new SessionProperties(false, "Strict", 7, 30, false, new SessionProperties.Throttle(3, 5, 15));
        throttle = new LoginThrottle(props, clock);
    }

    private void fail(String email, String ip, int times) {
        for (int i = 0; i < times; i++) {
            throttle.recordFailure(email, ip);
        }
    }

    @Test
    void anEmailIsBlockedAfterTooManyFailures() {
        fail("ana@x.com", "10.0.0.1", 2);
        assertThatCode(() -> throttle.check("ana@x.com", "10.0.0.1")).doesNotThrowAnyException();

        fail("ana@x.com", "10.0.0.1", 1);
        assertThatThrownBy(() -> throttle.check("ana@x.com", "10.0.0.1"))
                .isInstanceOfSatisfying(TooManyAttemptsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isBetween(1L, 900L));
    }

    @Test
    void theEmailIsCaseInsensitiveAndOtherEmailsAreUnaffected() {
        fail("Ana@X.com", "10.0.0.1", 3);
        assertThatThrownBy(() -> throttle.check("ana@x.com", "10.0.0.9")).isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> throttle.check("luis@x.com", "10.0.0.9")).doesNotThrowAnyException();
    }

    @Test
    void anIpIsBlockedAcrossDifferentEmails() {
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure("user" + i + "@x.com", "10.0.0.7");
        }
        assertThatThrownBy(() -> throttle.check("nuevo@x.com", "10.0.0.7")).isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void theBlockEndsWhenTheWindowPasses() {
        fail("ana@x.com", "10.0.0.1", 3);
        clock.advance(Duration.ofMinutes(16));
        assertThatCode(() -> throttle.check("ana@x.com", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void aSuccessfulLoginResetsTheEmailCounter() {
        fail("ana@x.com", "10.0.0.1", 2);
        throttle.reset("ana@x.com");
        fail("ana@x.com", "10.0.0.1", 2);
        assertThatCode(() -> throttle.check("ana@x.com", "10.0.0.1")).doesNotThrowAnyException();
    }

    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-08T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
