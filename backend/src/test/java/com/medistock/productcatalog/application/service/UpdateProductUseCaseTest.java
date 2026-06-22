package com.medistock.productcatalog.application.service;

import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.model.ProductDomainException;
import com.medistock.productcatalog.domain.model.ProductNotFoundException;
import com.medistock.productcatalog.domain.port.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateProductUseCaseTest {

    private static final LocalDate EXP = LocalDate.of(2026, Month.DECEMBER, 31);
    private static final LocalDate MFR = LocalDate.of(2025, Month.JANUARY, 1);

    @Mock
    private ProductRepository productRepository;

    private UpdateProductUseCase useCase;
    private Product existingProduct;

    @BeforeEach
    void setUp() {
        useCase = new UpdateProductUseCase(productRepository);
        existingProduct = Product.register("tenant-1", "Original Name", "Tablet", EXP, MFR);
    }

    @Test
    void shouldUpdateProduct() {
        when(productRepository.findById(existingProduct.getId())).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UUID catId = UUID.randomUUID();
        var result = useCase.execute(
                existingProduct.getId(),
                "Updated Name", "Generic Name", catId,
                "500mg", "Tablet", "New Manufacturer",
                true, false, "Cool storage",
                "Cápsula", BigDecimal.valueOf(100), BigDecimal.valueOf(50),
                "http://image.url",
                EXP, MFR
        );

        assertThat(result.name()).isEqualTo("Updated Name");
        assertThat(result.genericName()).isEqualTo("Generic Name");
        assertThat(result.manufacturer()).isEqualTo("New Manufacturer");
        assertThat(result.requiresPrescription()).isTrue();
        assertThat(result.salePrice()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(result.purchasePrice()).isEqualByComparingTo(BigDecimal.valueOf(50));
    }

    @Test
    void shouldThrowWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(
                id, "Name", null, null, null, null, null,
                false, false, null, "Unit",
                BigDecimal.TEN, BigDecimal.ZERO, null, EXP, MFR))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenNameIsBlank() {
        when(productRepository.findById(existingProduct.getId())).thenReturn(Optional.of(existingProduct));

        assertThatThrownBy(() -> useCase.execute(
                existingProduct.getId(),
                "  ", null, null, null, null, null,
                false, false, null, "Tablet",
                BigDecimal.TEN, BigDecimal.ZERO, null, EXP, MFR))
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("name must not be blank");
    }

    @Test
    void shouldThrowWhenUnitIsBlank() {
        when(productRepository.findById(existingProduct.getId())).thenReturn(Optional.of(existingProduct));

        assertThatThrownBy(() -> useCase.execute(
                existingProduct.getId(),
                "Name", null, null, null, null, null,
                false, false, null, "  ",
                BigDecimal.TEN, BigDecimal.ZERO, null, EXP, MFR))
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("Unit must not be blank");
    }

    @Test
    void shouldThrowWhenSalePriceIsNegative() {
        when(productRepository.findById(existingProduct.getId())).thenReturn(Optional.of(existingProduct));

        assertThatThrownBy(() -> useCase.execute(
                existingProduct.getId(),
                "Name", null, null, null, null, null,
                false, false, null, "Tablet",
                BigDecimal.valueOf(-1), BigDecimal.ZERO, null, EXP, MFR))
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("Sale price must not be negative");
    }
}
