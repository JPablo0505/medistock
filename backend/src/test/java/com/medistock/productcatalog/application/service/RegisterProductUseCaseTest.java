package com.medistock.productcatalog.application.service;

import com.medistock.productcatalog.domain.port.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Month;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterProductUseCase")
class RegisterProductUseCaseTest {

    private static final LocalDate EXP = LocalDate.of(2026, Month.DECEMBER, 31);
    private static final LocalDate MFR = LocalDate.of(2025, Month.JANUARY, 1);

    @Mock
    private ProductRepository productRepository;

    private RegisterProductUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterProductUseCase(productRepository);
    }

    @Test
    @DisplayName("Should register product with valid params")
    void shouldRegisterProduct() {
        // Arrange
        when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        var result = useCase.execute("tenant-1", "Paracetamol 500mg", "Tablet", EXP, MFR);

        // Assert
        assertThat(result.name()).as("name").isEqualTo("Paracetamol 500mg");
        assertThat(result.unit()).as("unit").isEqualTo("Tablet");
        assertThat(result.isActive()).as("active flag").isTrue();
        assertThat(result.id()).as("id").isNotNull();
        verify(productRepository).save(any());
    }

    @Test
    @DisplayName("Should return ProductDTO with all fields")
    void shouldReturnProductDTO() {
        // Arrange
        when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        var result = useCase.execute("tenant-1", "Amoxicilina", "Cápsula", EXP, MFR);

        // Assert
        assertThat(result).as("result type").isInstanceOf(ProductDTO.class);
        assertThat(result.tenantId()).as("tenant id").isEqualTo("tenant-1");
        assertThat(result.name()).as("name").isEqualTo("Amoxicilina");
        assertThat(result.expirationDate()).as("expiration date").isEqualTo(EXP);
        assertThat(result.manufacturingDate()).as("manufacturing date").isEqualTo(MFR);
    }
}
