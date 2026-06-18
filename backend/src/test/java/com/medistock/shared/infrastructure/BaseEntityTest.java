package com.medistock.shared.infrastructure;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD RED phase: Verify BaseEntity structure and lifecycle behavior.
 * BaseEntity is a JPA @MappedSuperclass with id, tenantId, createdAt, updatedAt, version.
 */
class BaseEntityTest {

    @Test
    void shouldBeAbstract() {
        assertTrue(java.lang.reflect.Modifier.isAbstract(BaseEntity.class.getModifiers()),
            "BaseEntity must be abstract");
    }

    @Test
    void shouldHaveIdField() throws Exception {
        Field field = BaseEntity.class.getDeclaredField("id");
        assertEquals(UUID.class, field.getType());
    }

    @Test
    void shouldHaveTenantIdField() throws Exception {
        Field field = BaseEntity.class.getDeclaredField("tenantId");
        assertEquals(String.class, field.getType());
    }

    @Test
    void shouldHaveCreatedAtField() throws Exception {
        Field field = BaseEntity.class.getDeclaredField("createdAt");
        assertEquals(LocalDateTime.class, field.getType());
    }

    @Test
    void shouldHaveUpdatedAtField() throws Exception {
        Field field = BaseEntity.class.getDeclaredField("updatedAt");
        assertEquals(LocalDateTime.class, field.getType());
    }

    @Test
    void shouldHaveVersionField() throws Exception {
        Field field = BaseEntity.class.getDeclaredField("version");
        assertEquals(long.class, field.getType());
    }

    @Test
    void shouldHaveOnCreateLifecycleMethod() throws Exception {
        Method method = BaseEntity.class.getDeclaredMethod("onCreate");
        assertNotNull(method);
    }

    @Test
    void shouldHaveOnUpdateLifecycleMethod() throws Exception {
        Method method = BaseEntity.class.getDeclaredMethod("onUpdate");
        assertNotNull(method);
    }

    @Test
    void shouldSetCreatedAtAndUpdatedAtOnCreate() {
        // Create a concrete subclass to test behavior
        class TestEntity extends BaseEntity {
            public TestEntity() {
                super();
            }
        }

        TestEntity entity = new TestEntity();
        entity.onCreate();

        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
        assertEquals(entity.getCreatedAt(), entity.getUpdatedAt(),
            "createdAt and updatedAt should be equal after onCreate");

        // Simulate a short delay and then update
        entity.onUpdate();
        assertTrue(entity.getUpdatedAt().isAfter(entity.getCreatedAt())
                || entity.getUpdatedAt().isEqual(entity.getCreatedAt()),
            "updatedAt should be >= createdAt after onUpdate");
    }

    @Test
    void shouldAutoGenerateIdIfNull() {
        class TestEntity extends BaseEntity {
            public TestEntity() {
                super();
            }
        }

        TestEntity entity = new TestEntity();
        assertNull(entity.getId()); // Before onCreate
        entity.onCreate();
        assertNotNull(entity.getId()); // After onCreate
        assertInstanceOf(UUID.class, entity.getId());
    }

    @Test
    void shouldPreserveExistingId() {
        class TestEntity extends BaseEntity {
            public TestEntity(UUID id) {
                super();
                setId(id);
            }
        }

        UUID existingId = UUID.randomUUID();
        TestEntity entity = new TestEntity(existingId);
        entity.onCreate();
        assertEquals(existingId, entity.getId(),
            "Existing ID should be preserved after onCreate");
    }

    @Test
    void shouldHaveGettersForAllFields() throws Exception {
        assertNotNull(BaseEntity.class.getMethod("getId"));
        assertNotNull(BaseEntity.class.getMethod("getTenantId"));
        assertNotNull(BaseEntity.class.getMethod("getCreatedAt"));
        assertNotNull(BaseEntity.class.getMethod("getUpdatedAt"));
        assertNotNull(BaseEntity.class.getMethod("getVersion"));
    }

    @Test
    void versionShouldStartAtZero() {
        class TestEntity extends BaseEntity {
            public TestEntity() {
                super();
            }
        }

        TestEntity entity = new TestEntity();
        assertEquals(0L, entity.getVersion(), "Version should default to 0");
    }
}
