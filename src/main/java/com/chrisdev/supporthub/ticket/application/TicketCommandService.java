package com.chrisdev.supporthub.ticket.application;

import com.chrisdev.supporthub.asset.domain.Asset;
import com.chrisdev.supporthub.asset.infrastructure.AssetRepository;
import com.chrisdev.supporthub.common.exception.BusinessException;
import com.chrisdev.supporthub.common.exception.ErrorCode;
import com.chrisdev.supporthub.common.security.AuthenticatedUser;
import com.chrisdev.supporthub.ticket.api.CreateTicketRequest;
import com.chrisdev.supporthub.ticket.api.TicketResponse;
import com.chrisdev.supporthub.ticket.domain.*;
import com.chrisdev.supporthub.ticket.infrastructure.CategoryRepository;
import com.chrisdev.supporthub.ticket.infrastructure.TicketHistoryRepository;
import com.chrisdev.supporthub.ticket.infrastructure.TicketRepository;
import com.chrisdev.supporthub.user.domain.User;
import com.chrisdev.supporthub.user.infrastructure.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketCommandService {

    private final TicketRepository ticketRepository;
    private final TicketHistoryRepository historyRepository;
    private final CategoryRepository categoryRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;

    public TicketCommandService(TicketRepository ticketRepository,
            TicketHistoryRepository historyRepository,
            CategoryRepository categoryRepository,
            AssetRepository assetRepository,
            UserRepository userRepository) {
        this.ticketRepository = ticketRepository;
        this.historyRepository = historyRepository;
        this.categoryRepository = categoryRepository;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TicketResponse create(CreateTicketRequest request, AuthenticatedUser actor) {
        User requester = userRepository.findById(actor.id())
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED,
                        "The authenticated user no longer exists or is inactive."));

        Category category = categoryRepository.findById(request.categoryId())
                .filter(Category::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "The selected category does not exist or is inactive."));

        Asset asset = null;
        if (request.assetId() != null) {
            asset = assetRepository.findById(request.assetId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "The selected asset does not exist."));
        }

        Ticket ticket = Ticket.open(request.title(), request.description(),
                category, request.priority(), requester, asset);

        // saveAndFlush fuerza el INSERT ahora, para que Hibernate lea el ticket_code generado por la BD
        ticketRepository.saveAndFlush(ticket);

        historyRepository.save(TicketHistory.of(ticket, requester, TicketEventType.CREATED,
                null, TicketStatus.OPEN.name(), null));

        return TicketResponse.from(ticket);
    }
}
