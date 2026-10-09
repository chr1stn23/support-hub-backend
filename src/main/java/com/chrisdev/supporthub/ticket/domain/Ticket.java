package com.chrisdev.supporthub.ticket.domain;

import com.chrisdev.supporthub.asset.domain.Asset;
import com.chrisdev.supporthub.common.exception.BusinessException;
import com.chrisdev.supporthub.common.exception.ErrorCode;
import com.chrisdev.supporthub.user.domain.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Generated   // TCK-000001: lo asigna la BD con su secuencia
    @Column(name = "ticket_code", nullable = false, updatable = false)
    private String ticketCode;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketPriority priority;

    @Column(name = "priority_rank", insertable = false, updatable = false)
    private int priorityRank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id")
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private User agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    private Long version;

    protected Ticket() {
    }

    public static Ticket open(String title, String description, Category category,
            TicketPriority priority, User requester, Asset asset) {
        Ticket ticket = new Ticket();
        ticket.title = title.trim();
        ticket.description = description.trim();
        ticket.category = category;
        ticket.priority = priority;
        ticket.requester = requester;
        ticket.asset = asset;
        ticket.status = TicketStatus.OPEN;
        return ticket;
    }

    public UUID getId() {
        return id;
    }

    public String getTicketCode() {
        return ticketCode;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public int getPriorityRank() {
        return priorityRank;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public Category getCategory() {
        return category;
    }

    public User getRequester() {
        return requester;
    }

    public User getAgent() {
        return agent;
    }

    public Asset getAsset() {
        return asset;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Long getVersion() {
        return version;
    }

    // Comportamiento de dominio
    public boolean isLocked() {
        return status == TicketStatus.CLOSED || status == TicketStatus.CANCELLED;
    }

    public boolean isAssignable() {
        return status == TicketStatus.OPEN
                || status == TicketStatus.IN_PROGRESS
                || status == TicketStatus.WAITING;
    }

    /**
     * @return true si el estado cambió (OPEN pasa a IN_PROGRESS al asignarse)
     */
    public boolean assignTo(User newAgent) {
        this.agent = newAgent;
        if (status == TicketStatus.OPEN) {
            status = TicketStatus.IN_PROGRESS;
            return true;
        }
        return false;
    }

    public void changeStatus(TicketStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "A ticket cannot move from %s to %s.".formatted(status, target));
        }

        if (target == TicketStatus.IN_PROGRESS && agent == null) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Assign the ticket to an agent before moving it to IN_PROGRESS.");
        }

        Instant now = Instant.now();
        switch (target) {
            case RESOLVED -> resolvedAt = now;
            case CLOSED -> closedAt = now;
            case IN_PROGRESS -> {
                if (status == TicketStatus.RESOLVED) {
                    resolvedAt = null; // reapertura
                }
            }
            default -> {
            }
        }

        this.status = target;
    }

    public void changeTitle(String title) {
        this.title = title.trim();
    }

    public void changeDescription(String description) {
        this.description = description.trim();
    }

    public void changePriority(TicketPriority priority) {
        this.priority = priority;
    }

    public void changeCategory(Category category) {
        this.category = category;
    }

    public void linkAsset(Asset asset) {
        this.asset = asset;
    }

    public boolean canMoveTo(TicketStatus target) {
        return status.canTransitionTo(target) && !(target == TicketStatus.IN_PROGRESS && agent == null);
    }
}