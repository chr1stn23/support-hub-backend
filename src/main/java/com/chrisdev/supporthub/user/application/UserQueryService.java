package com.chrisdev.supporthub.user.application;

import com.chrisdev.supporthub.common.api.PageResponse;
import com.chrisdev.supporthub.common.exception.BusinessException;
import com.chrisdev.supporthub.common.exception.ErrorCode;
import com.chrisdev.supporthub.common.exception.ResourceNotFoundException;
import com.chrisdev.supporthub.user.api.UserAdminResponse;
import com.chrisdev.supporthub.user.api.UserLookupResponse;
import com.chrisdev.supporthub.user.domain.RoleName;
import com.chrisdev.supporthub.user.domain.User;
import com.chrisdev.supporthub.user.infrastructure.UserRepository;
import com.chrisdev.supporthub.user.infrastructure.UserSpecifications;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserQueryService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("fullName", "email", "createdAt");
    private static final int MAX_SEARCH_LENGTH = 100;

    private final UserRepository userRepository;

    public UserQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public PageResponse<UserAdminResponse> search(UserSearchCriteria criteria, Pageable pageable) {
        validateSort(pageable);

        Specification<User> spec = UserSpecifications.all();
        if (criteria.active() != null) {
            spec = spec.and(UserSpecifications.hasActive(criteria.active()));
        }
        if (criteria.role() != null) {
            spec = spec.and(UserSpecifications.hasRole(criteria.role()));
        }
        spec = withSearch(spec, criteria.search());

        // Los roles de toda la página se cargan con UNA consulta extra (@BatchSize)
        return PageResponse.from(userRepository.findAll(spec, pageable), UserAdminResponse::from);
    }

    public PageResponse<UserLookupResponse> lookup(String search, RoleName role, Pageable pageable) {
        validateSort(pageable);

        Specification<User> spec = UserSpecifications.hasActive(true);   // el selector solo ofrece activos
        if (role != null) {
            spec = spec.and(UserSpecifications.hasRole(role));
        }
        spec = withSearch(spec, search);

        return PageResponse.from(userRepository.findAll(spec, pageable), UserLookupResponse::from);
    }

    public UserAdminResponse getById(UUID id) {
        return userRepository.findWithRolesById(id)
                .map(UserAdminResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private Specification<User> withSearch(Specification<User> spec, String search) {
        if (search == null || search.isBlank()) {
            return spec;
        }
        String text = search.trim();
        if (text.length() > MAX_SEARCH_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "The search text cannot exceed %d characters.".formatted(MAX_SEARCH_LENGTH));
        }
        return spec.and(UserSpecifications.matches(text));
    }

    private void validateSort(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Cannot sort by '%s'. Allowed fields: %s."
                                .formatted(order.getProperty(), SORTABLE_FIELDS));
            }
        }
    }
}
