package com.chrisdev.supporthub.user.application;

import com.chrisdev.supporthub.asset.infrastructure.AssetAssignmentRepository;
import com.chrisdev.supporthub.audit.application.AuditMetadata;
import com.chrisdev.supporthub.audit.application.AuditService;
import com.chrisdev.supporthub.audit.domain.AuditAction;
import com.chrisdev.supporthub.audit.domain.AuditEntityType;
import com.chrisdev.supporthub.common.exception.BusinessException;
import com.chrisdev.supporthub.common.exception.ErrorCode;
import com.chrisdev.supporthub.common.exception.ResourceNotFoundException;
import com.chrisdev.supporthub.common.security.AuthenticatedUser;
import com.chrisdev.supporthub.common.util.Texts;
import com.chrisdev.supporthub.ticket.domain.TicketStatus;
import com.chrisdev.supporthub.ticket.infrastructure.TicketRepository;
import com.chrisdev.supporthub.user.api.*;
import com.chrisdev.supporthub.user.domain.Role;
import com.chrisdev.supporthub.user.domain.RoleName;
import com.chrisdev.supporthub.user.domain.User;
import com.chrisdev.supporthub.user.infrastructure.RoleRepository;
import com.chrisdev.supporthub.user.infrastructure.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class UserCommandService {

    private static final String EMAIL_INDEX = "uq_users_email_lower";
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final AssetAssignmentRepository assignmentRepository;
    private final TicketRepository ticketRepository;

    public UserCommandService(UserRepository userRepository, RoleRepository roleRepository,
            PasswordEncoder passwordEncoder, AuditService audit,
            AssetAssignmentRepository assignmentRepository,
            TicketRepository ticketRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.assignmentRepository = assignmentRepository;
        this.ticketRepository = ticketRepository;
    }

    private static List<String> names(Set<Role> roles) {
        return roles.stream().map(r -> r.getName().name()).sorted().toList();
    }

    private static void requireEncodable(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "The password cannot exceed %d bytes.".formatted(BCRYPT_MAX_BYTES));
        }
    }

    private static BusinessException emailTaken() {
        return new BusinessException(ErrorCode.EMAIL_ALREADY_USED, "Another user already uses that email.");
    }

    // Crear
    @Transactional
    public UserAdminResponse create(CreateUserRequest request, AuthenticatedUser actor) {
        requireAdminActor(actor);

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw emailTaken();
        }
        requireEncodable(request.password());

        User user = new User(email, passwordEncoder.encode(request.password()),
                Texts.requireText("fullName", request.fullName()));
        Set<Role> roles = resolveRoles(request.roles());
        roles.forEach(user::addRole);
        saveCatchingDuplicateEmail(user);

        audit.record(actor.id(), AuditAction.USER_CREATED, AuditEntityType.USER, user.getId(),
                AuditMetadata.of("email", user.getEmail(), "roles", names(roles)));
        return UserAdminResponse.from(user);
    }

    // Editar
    @Transactional
    public UserAdminResponse update(UUID userId, UpdateUserRequest request, AuthenticatedUser actor) {
        requireAdminActor(actor);
        User user = requireUser(userId);

        if (!request.hasChanges()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "No changes were provided.");
        }
        if (!Objects.equals(user.getVersion(), request.version())) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                    "The user was modified by someone else. Reload it and try again.");
        }

        List<String> changed = new ArrayList<>();
        if (request.fullName() != null) {
            String fullName = Texts.requireText("fullName", request.fullName());
            if (!fullName.equals(user.getFullName())) {
                user.changeFullName(fullName);
                changed.add("fullName");
            }
        }
        if (request.email() != null) {
            String email = request.email().trim().toLowerCase(Locale.ROOT);
            if (!email.equals(user.getEmail())) {
                if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, user.getId())) {
                    throw emailTaken();
                }
                user.changeEmail(email);
                changed.add("email");
            }
        }

        if (changed.isEmpty()) {
            return UserAdminResponse.from(user);
        }
        saveCatchingDuplicateEmail(user);
        audit.record(actor.id(), AuditAction.USER_UPDATED, AuditEntityType.USER, user.getId(),
                AuditMetadata.of("fields", changed));
        return UserAdminResponse.from(user);
    }

    // Roles
    @Transactional
    public UserAdminResponse changeRoles(UUID userId, ChangeRolesRequest request, AuthenticatedUser actor) {
        lockAdminRole();                     // 1) serializar...
        requireAdminActor(actor);            // 2)... y SOLO ENTONCES leer el estado ya confirmado
        User user = requireUser(userId);

        Set<Role> newRoles = resolveRoles(request.roles());
        List<String> before = user.roleNames();
        List<String> after = names(newRoles);
        if (before.equals(after)) {
            return UserAdminResponse.from(user);   // nada que cambiar
        }

        boolean losesAdmin = user.hasRole(RoleName.ADMIN) && !request.roles().contains(RoleName.ADMIN);
        if (losesAdmin && user.getId().equals(actor.id())) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF,
                    "You cannot remove your own ADMIN role. Ask another admin to do it.");
        }

        user.replaceRoles(newRoles);
        userRepository.saveAndFlush(user);
        if (losesAdmin) {
            ensureAnAdminRemains();
        }

        audit.record(actor.id(), AuditAction.USER_ROLES_CHANGED, AuditEntityType.USER, user.getId(),
                AuditMetadata.of("email", user.getEmail(), "from", before, "to", after));
        return UserAdminResponse.from(user);
    }

    // Activar / Baja
    @Transactional
    public UserAdminResponse deactivate(UUID userId, AuthenticatedUser actor) {
        lockAdminRole();
        requireAdminActor(actor);
        User user = requireUser(userId);

        if (user.getId().equals(actor.id())) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF, "You cannot deactivate your own account.");
        }
        if (!user.isActive()) {
            return UserAdminResponse.from(user);   // idempotente
        }
        ensureNoPendingWork(user);

        user.deactivate();
        userRepository.saveAndFlush(user);
        ensureAnAdminRemains();

        audit.record(actor.id(), AuditAction.USER_DEACTIVATED, AuditEntityType.USER, user.getId(),
                AuditMetadata.of("email", user.getEmail()));
        return UserAdminResponse.from(user);
    }

    @Transactional
    public UserAdminResponse activate(UUID userId, AuthenticatedUser actor) {
        requireAdminActor(actor);
        User user = requireUser(userId);

        if (user.isActive()) {
            return UserAdminResponse.from(user);
        }
        user.activate();
        userRepository.saveAndFlush(user);

        audit.record(actor.id(), AuditAction.USER_ACTIVATED, AuditEntityType.USER, user.getId(),
                AuditMetadata.of("email", user.getEmail()));
        return UserAdminResponse.from(user);
    }

    // Contraseñas
    @Transactional
    public void changeOwnPassword(ChangePasswordRequest request, AuthenticatedUser actor) {
        User user = userRepository.findById(actor.id())
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED,
                        "The authenticated user no longer exists or is inactive."));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD, "The current password is incorrect.");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "The new password must be different from the current one.");
        }
        requireEncodable(request.newPassword());

        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.saveAndFlush(user);

        audit.record(actor.id(), AuditAction.PASSWORD_CHANGED, AuditEntityType.USER, user.getId(), null);
    }

    @Transactional
    public void resetPassword(UUID userId, ResetPasswordRequest request, AuthenticatedUser actor) {
        requireAdminActor(actor);
        if (userId.equals(actor.id())) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF,
                    "Use the change-password endpoint for your own account.");
        }
        User user = requireUser(userId);
        requireEncodable(request.newPassword());

        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.saveAndFlush(user);

        audit.record(actor.id(), AuditAction.PASSWORD_RESET, AuditEntityType.USER, user.getId(),
                AuditMetadata.of("email", user.getEmail()));
    }

    // Helpers

    /**
     * Bloquea la fila del rol ADMIN hasta el fin de la transacción (SELECT ... FOR UPDATE).
     */
    private void lockAdminRole() {
        roleRepository.findByNameForUpdate(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("The ADMIN role is missing from the database"));
    }

    /**
     * El JWT puede estar desactualizado (usuario desactivado o degradado tras emitirse):
     * para administrar, el actor tiene que seguir siendo un ADMIN activo SEGÚN LA BD.
     */
    private void requireAdminActor(AuthenticatedUser actor) {
        User admin = userRepository.findWithRolesById(actor.id())
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED,
                        "The authenticated user no longer exists or is inactive."));
        if (!admin.hasRole(RoleName.ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Your administrator privileges were revoked. Sign in again.");
        }
    }

    /**
     * Red de seguridad: con el bloqueo y requireAdminActor normalmente no puede dispararse.
     */
    private void ensureAnAdminRemains() {
        if (userRepository.countActiveByRole(RoleName.ADMIN) == 0) {
            throw new BusinessException(ErrorCode.LAST_ADMIN_REQUIRED, "At least one active admin must remain.");
        }
    }

    private void ensureNoPendingWork(User user) {
        long assets = assignmentRepository.countByUserIdAndReturnedAtIsNull(user.getId());
        long tickets = ticketRepository.countByAgentIdAndStatusIn(user.getId(),
                List.of(TicketStatus.IN_PROGRESS, TicketStatus.WAITING));
        if (assets > 0 || tickets > 0) {
            throw new BusinessException(ErrorCode.USER_HAS_ACTIVE_ASSIGNMENTS,
                    ("The user still holds %d asset(s) and has %d open ticket(s) assigned. Return or reassign them " +
                            "first.")
                            .formatted(assets, tickets));
        }
    }

    private User requireUser(UUID id) {
        return userRepository.findWithRolesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private Set<Role> resolveRoles(Set<RoleName> names) {
        List<Role> found = roleRepository.findByNameIn(names);
        if (found.size() != names.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "One or more roles do not exist.");
        }
        return new HashSet<>(found);
    }

    private void saveCatchingDuplicateEmail(User user) {
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            String message = ex.getMostSpecificCause().getMessage();
            if (message != null && message.contains(EMAIL_INDEX)) {
                throw emailTaken();   // dos altas simultáneas con el mismo email
            }
            throw ex;
        }
    }
}
