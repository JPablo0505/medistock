package com.medistock.shared.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BaseEntity abstract class")
class BaseEntityTest {

    @Test
    @DisplayName("Should be abstract")
    void shouldBeAbstract() {
        // Act & Assert
        assertTrue(java.lang.reflect.Modifier.isAbstract(BaseEntity.class.getModifiers()),
                "BaseEntity must be abstract");
    }

    @Test
    @DisplayName("Should have id field of type UUID")
    void shouldHaveIdField() throws Exception {
        // Arrange & Act
        Field field = BaseEntity.class.getDeclaredField("id");

        // Assert
        assertEquals(UUID.class, field.getType(), "id field type");
    }

    @Test
    @DisplayName("Should have tenantId field of type String")
    void shouldHaveTenantIdField() throws Exception {
        // Arrange & Act
        Field field = BaseEntity.class.getDeclaredField("tenantId");

        // Assert
        assertEquals(String.class, field.getType(), "tenantId field type");
    }

    @Test
    @DisplayName("Should have createdAt field of type LocalDateTime")
    void shouldHaveCreatedAtField() throws Exception {
        // Arrange & Act
        Field field = BaseEntity.class.getDeclaredField("createdAt");

        // Assert
        assertEquals(LocalDateTime.class, field.getType(), "createdAt field type");
    }

    @Test
    @DisplayName("Should have updatedAt field of type LocalDateTime")
    void shouldHaveUpdatedAtField() throws Exception {
        // Arrange & Act
        Field field = BaseEntity.class.getDeclaredField("updatedAt");

        // Assert
        assertEquals(LocalDateTime.class, field.getType(), "updatedAt field type");
    }

    @Test
    @DisplayName("Should have version field of type long")
    void shouldHaveVersionField() throws Exception {
        // Arrange & Act
        Field field = BaseEntity.class.getDeclaredField("version");

        // Assert
        assertEquals(long.class, field.getType(), "version field type");
    }

    @Test
    @DisplayName("Should have onCreate lifecycle method")
    void shouldHaveOnCreateLifecycleMethod() throws Exception {
        // Arrange & Act
        Method method = BaseEntity.class.getDeclaredMethod("onCreate");

        // Assert
        assertNotNull(method, "onCreate method should exist");
    }

    @Test
    @DisplayName("Should have onUpdate lifecycle method")
    void shouldHaveOnUpdateLifecycleMethod() throws Exception {
        // Arrange & Act
        Method method = BaseEntity.class.getDeclaredMethod("onUpdate");

        // Assert
        assertNotNull(method, "onUpdate method should exist");
    }

    @Test
    @DisplayName("Should set createdAt and updatedAt on create")
    void shouldSetCreatedAtAndUpdatedAtOnCreate() {
        // Arrange
        class TestEntity extends BaseEntity {
            public TestEntity() { super(); }
        }
        TestEntity entity = new TestEntity();

        // Act
        entity.onCreate();

        // Assert
        assertNotNull(entity.getCreatedAt(), "createdAt");
        assertNotNull(entity.getUpdatedAt(), "updatedAt");
        assertEquals(entity.getCreatedAt(), entity.getUpdatedAt(),
                "createdAt and updatedAt should be equal after onCreate");

        // Act & Assert (onUpdate)
        entity.onUpdate();
        assertTrue(entity.getUpdatedAt().isAfter(entity.getCreatedAt())
                        || entity.getUpdatedAt().isEqual(entity.getCreatedAt()),
                "updatedAt should be >= createdAt after onUpdate");
    }

    @Test
    @DisplayName("Should auto-generate id if null")
    void shouldAutoGenerateIdIfNull() {
        // Arrange
        class TestEntity extends BaseEntity {
            public TestEntity() { super(); }
        }
        TestEntity entity = new TestEntity();

        // Assert before
        assertNull(entity.getId(), "id should be null before onCreate");

        // Act
        entity.onCreate();

        // Assert after
        assertNotNull(entity.getId(), "id should be set after onCreate");
        assertInstanceOf(UUID.class, entity.getId(), "id type");
    }

    @Test
    @DisplayName("Should preserve existing id")
    void shouldPreserveExistingId() {
        // Arrange
        class TestEntity extends BaseEntity {
            public TestEntity(UUID id) { super(); setId(id); }
        }
        UUID existingId = UUID.randomUUID();
        TestEntity entity = new TestEntity(existingId);

        // Act
        entity.onCreate();

        // Assert
        assertEquals(existingId, entity.getId(), "Existing ID should be preserved after onCreate");
    }

    @Test
    @DisplayName("Should have getters for all fields")
    void shouldHaveGettersForAllFields() throws Exception {
        // Act & Assert
        assertNotNull(BaseEntity.class.getMethod("getId"), "getId()");
        assertNotNull(BaseEntity.class.getMethod("getTenantId"), "getTenantId()");
        assertNotNull(BaseEntity.class.getMethod("getCreatedAt"), "getCreatedAt()");
        assertNotNull(BaseEntity.class.getMethod("getUpdatedAt"), "getUpdatedAt()");
        assertNotNull(BaseEntity.class.getMethod("getVersion"), "getVersion()");
    }

    @Test
    @DisplayName("Version should start at zero")
    void versionShouldStartAtZero() {
        // Arrange
        class TestEntity extends BaseEntity {
            public TestEntity() { super(); }
        }
        TestEntity entity = new TestEntity();

        // Act & Assert
        assertEquals(0L, entity.getVersion(), "Version should default to 0");
    }
}
