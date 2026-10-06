package com.chrisdev.supporthub.user.infrastructure;

import com.chrisdev.supporthub.common.util.Texts;
import com.chrisdev.supporthub.user.domain.RoleName;
import com.chrisdev.supporthub.user.domain.User;
import org.springframework.data.jpa.domain.Specification;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> all() {
        return (root, query, cb) -> cb.conjunction();
    }

    public static Specification<User> hasActive(boolean active) {
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    // Un usuario tiene como máximo UNA fila por rol, así que este join filtrado no duplica resultados
    public static Specification<User> hasRole(RoleName role) {
        return (root, query, cb) -> cb.equal(root.join("roles").get("name"), role);
    }

    public static Specification<User> matches(String text) {
        return (root, query, cb) -> {
            String pattern = "%" + Texts.escapeLike(text.toLowerCase()) + "%";
            return cb.or(
                    cb.like(cb.lower(root.<String>get("fullName")), pattern, '\\'),
                    cb.like(cb.lower(root.<String>get("email")), pattern, '\\'));
        };
    }
}
