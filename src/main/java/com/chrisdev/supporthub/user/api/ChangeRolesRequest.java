package com.chrisdev.supporthub.user.api;

import com.chrisdev.supporthub.user.domain.RoleName;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record ChangeRolesRequest(@NotEmpty Set<RoleName> roles) {
}