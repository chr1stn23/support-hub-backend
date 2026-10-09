package com.chrisdev.supporthub.auth.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class RefreshTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanup.class);

    private final RefreshTokenStore store;

    public RefreshTokenCleanup(RefreshTokenStore store) {
        this.store = store;
    }

    /**
     * Cada noche borra las familias que caducaron hace más de un día.
     */
    @Scheduled(cron = "0 30 3 * * *")
    public void purge() {
        int deleted = store.deleteExpired(Instant.now().minus(Duration.ofDays(1)));
        log.info("Purged {} expired refresh tokens", deleted);
    }
}
