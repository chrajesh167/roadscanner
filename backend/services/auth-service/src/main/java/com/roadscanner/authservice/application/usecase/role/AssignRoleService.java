package com.roadscanner.authservice.application.usecase.role;

import com.roadscanner.authservice.domain.exception.UserNotFoundException;
import com.roadscanner.authservice.domain.model.RoleAssignment;
import com.roadscanner.authservice.domain.port.in.AssignRole;
import com.roadscanner.authservice.domain.port.out.CredentialRepository;
import com.roadscanner.authservice.domain.port.out.RoleAssignmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link AssignRole} — the internal, never-client-facing role elevation from
 * docs/services/auth-service/responsibilities.md ("Boundary With Operator Service"). Appends
 * a new immutable {@link RoleAssignment} fact; the change takes effect on the user's next
 * token issuance (login or refresh), since already-issued access tokens are stateless and
 * carry the role claim they were signed with.
 */
@Transactional
public class AssignRoleService implements AssignRole {

    private static final Logger log = LoggerFactory.getLogger(AssignRoleService.class);

    private final CredentialRepository credentialRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;

    public AssignRoleService(CredentialRepository credentialRepository,
                             RoleAssignmentRepository roleAssignmentRepository) {
        this.credentialRepository = credentialRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    @Override
    public AssignRoleResult assign(AssignRoleCommand command) {
        credentialRepository.findByUserId(command.userId())
                .orElseThrow(() -> new UserNotFoundException(command.userId()));

        RoleAssignment assignment = roleAssignmentRepository.save(
                RoleAssignment.assign(command.userId(), command.role(), command.assignedBy(), command.now()));
        log.info("Role {} assigned to user {} by {}", assignment.role(), assignment.userId(), assignment.assignedBy());
        return new AssignRoleResult(assignment);
    }
}
