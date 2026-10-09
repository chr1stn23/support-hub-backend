package com.chrisdev.supporthub.auth.infrastructure;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RefreshTokenStore {

    private final JdbcClient jdbc;

    public RefreshTokenStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    public void insert(UUID userId, UUID familyId, String tokenHash, Instant expiresAt, Instant familyExpiresAt) {
        jdbc.sql("""
                        INSERT INTO refresh_tokens (user_id, family_id, token_hash, expires_at, family_expires_at)
                        VALUES (:userId, :familyId, :hash, :expiresAt, :familyExpiresAt)
                        """)
                .param("userId", userId)
                .param("familyId", familyId)
                .param("hash", tokenHash)
                .param("expiresAt", utc(expiresAt))
                .param("familyExpiresAt", utc(familyExpiresAt))
                .update();
    }

    public Optional<Row> findByHash(String tokenHash) {
        return jdbc.sql("""
                        SELECT id, user_id, family_id, expires_at, family_expires_at, used_at, revoked_at
                        FROM refresh_tokens WHERE token_hash = :hash
                        """)
                .param("hash", tokenHash)
                .query((rs, n) -> new Row(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("family_id", UUID.class),
                        instant(rs.getObject("expires_at", OffsetDateTime.class)),
                        instant(rs.getObject("family_expires_at", OffsetDateTime.class)),
                        instant(rs.getObject("used_at", OffsetDateTime.class)),
                        instant(rs.getObject("revoked_at", OffsetDateTime.class))))
                .optional();
    }

    /**
     * Atómico: si dos peticiones intentan usar el mismo token, solo una recibe true.
     */
    public boolean markUsed(UUID id) {
        return jdbc.sql("UPDATE refresh_tokens SET used_at = now() WHERE id = :id AND used_at IS null AND revoked_at " +
                        "IS null")
                .param("id", id)
                .update() == 1;
    }

    public int revokeFamily(UUID familyId) {
        return jdbc.sql("UPDATE refresh_tokens SET revoked_at = now() WHERE family_id = :family AND revoked_at IS null")
                .param("family", familyId)
                .update();
    }

    /**
     * En su propia transacción: debe confirmarse aunque la petición que lo detecta termine en error.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeFamilyNow(UUID familyId) {
        return revokeFamily(familyId);
    }

    public int revokeAllForUser(UUID userId) {
        return jdbc.sql("UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = :user AND revoked_at IS null")
                .param("user", userId)
                .update();
    }

    public int deleteExpired(Instant familiesExpiredBefore) {
        return jdbc.sql("DELETE from refresh_tokens WHERE family_expires_at < :before")
                .param("before", utc(familiesExpiredBefore))
                .update();
    }

    public record Row(UUID id, UUID userId, UUID familyId, Instant expiresAt, Instant familyExpiresAt,
                      Instant usedAt, Instant revokedAt) {
    }
}
