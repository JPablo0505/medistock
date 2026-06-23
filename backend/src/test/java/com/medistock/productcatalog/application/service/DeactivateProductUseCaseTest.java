package com.medistock.productcatalog.application.service;

import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.model.ProductNotFoundException;
import com.medistock.productcatalog.domain.port.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Month;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeactivateProductUseCase")
class DeactivateProductUseCaseTest {

    private static final LocalDate EXP = LocalDate.of(2026, Month.DECEMBER, 31);
    private static final LocalDate MFR = LocalDate.of(2025, Month.JANUARY, 1);

    @Mock
    private ProductRepository productRepository;

    private DeactivateProductUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeactivateProductUseCase(productRepository);
    }

    @Test
    @DisplayName("Should deactivate active product")
    void shouldDeactivateProduct() {
        // Arrange
        var product = Product.register("tenant-1", "Ibuprofeno", "Tablet", EXP, MFR);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        var result = useCase.execute(product.getId());

        // Assert
        assertThat(result.isActive()).as("active flag after deactivate").isFalse();
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("Should throw when product not found")
    void shouldThrowWhenNotFound() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(id))
                .as("Non-existent product should throw ProductNotFoundException")
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should not throw when product already inactive")
    void shouldNotThrowWhenAlreadyInactive() {
        // Arrange
        var product = Product.register("tenant-1", "Ibuprofeno", "Tablet", EXP, MFR);
        product.deactivate();
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        var result = useCase.execute(product.getId());

        // Assert
        assertThat(result.isActive()).as("active flag").isFalse();
        verify(productRepository).save(product);
    }
}
