package com.chrisdev.supporthub.user.application;

import com.chrisdev.supporthub.user.domain.RoleName;

public record UserSearchCriteria(Boolean active, RoleName role, String search) {
}