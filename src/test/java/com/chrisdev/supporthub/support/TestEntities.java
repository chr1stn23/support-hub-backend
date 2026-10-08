package com.chrisdev.supporthub.support;

import com.chrisdev.supporthub.asset.domain.AssetType;
import com.chrisdev.supporthub.ticket.domain.Category;
import com.chrisdev.supporthub.user.domain.User;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

/**
 * Fabrica entidades para pruebas unitarias, sin base de datos.
 */
public final class TestEntities {

    private TestEntities() {
    }

    public static User user(String name) {
        User user = new User(name.toLowerCase().replace(' ', '.') + "@test.local", "hash", name);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    public static Category category() {
        Category category = BeanUtils.instantiateClass(Category.class);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(category, "name", "Hardware");
        ReflectionTestUtils.setField(category, "active", true);
        return category;
    }

    public static AssetType assetType() {
        AssetType type = BeanUtils.instantiateClass(AssetType.class);
        ReflectionTestUtils.setField(type, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(type, "name", "Laptop");
        ReflectionTestUtils.setField(type, "active", true);
        return type;
    }
}
