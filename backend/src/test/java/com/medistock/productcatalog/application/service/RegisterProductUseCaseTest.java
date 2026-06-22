package com.medistock.productcatalog.application.service;

import com.medistock.productcatalog.domain.port.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Month;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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
    void shouldRegisterProduct() {
        when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = useCase.execute("tenant-1", "Paracetamol 500mg", "Tablet", EXP, MFR);

        assertThat(result.name()).isEqualTo("Paracetamol 500mg");
        assertThat(result.unit()).isEqualTo("Tablet");
        assertThat(result.isActive()).isTrue();
        assertThat(result.id()).isNotNull();
        verify(productRepository).save(any());
    }

    @Test
    void shouldReturnProductDTO() {
        when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = useCase.execute("tenant-1", "Amoxicilina", "Cápsula", EXP, MFR);

        assertThat(result).isInstanceOf(ProductDTO.class);
        assertThat(result.tenantId()).isEqualTo("tenant-1");
        assertThat(result.name()).isEqualTo("Amoxicilina");
        assertThat(result.expirationDate()).isEqualTo(EXP);
        assertThat(result.manufacturingDate()).isEqualTo(MFR);
    }
}
