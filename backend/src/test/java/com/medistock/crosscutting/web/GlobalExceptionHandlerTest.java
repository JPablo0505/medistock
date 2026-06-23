package com.medistock.crosscutting.web;

import com.medistock.inventory.domain.model.InventoryDomainException;
import com.medistock.productcatalog.domain.model.ProductNotFoundException;
import com.medistock.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Mock private WebRequest request;

    private void mockCorrelationId(String value) {
        when(request.getAttribute(CorrelationIdFilter.CORRELATION_ID_HEADER,
                RequestAttributes.SCOPE_REQUEST)).thenReturn(value);
    }

    @Test
    @DisplayName("Should handle MethodArgumentNotValidException")
    void shouldHandleValidation() {
        // Arrange
        mockCorrelationId("corr-123");
        var fieldError = new FieldError("obj", "name", "must not be null");
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        var ex = new MethodArgumentNotValidException(null, bindingResult);

        // Act
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleValidation(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(400));
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().errors()).hasSize(1);
        assertThat(response.getBody().errors().get(0).field()).isEqualTo("name");
        assertThat(response.getBody().correlationId()).isEqualTo("corr-123");
    }

    @Test
    @DisplayName("Should handle OptimisticLockingFailureException")
    void shouldHandleOptimisticLock() {
        mockCorrelationId("corr-456");
        var ex = new OptimisticLockingFailureException("lock conflict");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleOptimisticLock(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(409));
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("OPTIMISTIC_LOCK");
        assertThat(response.getBody().errors()).isEmpty();
    }

    @Test
    @DisplayName("Should handle ProductNotFoundException")
    void shouldHandleProductNotFound() {
        mockCorrelationId("corr-789");
        var productId = UUID.randomUUID();
        var ex = new ProductNotFoundException(productId);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleProductNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(404));
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("PRODUCT_NOT_FOUND");
        assertThat(response.getBody().message()).contains(productId.toString());
    }

    @Test
    @DisplayName("Should handle DomainException")
    void shouldHandleDomainException() {
        mockCorrelationId("corr-abc");
        var ex = new InventoryDomainException("Insufficient stock");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleDomainException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(409));
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INV-001");
    }

    @Test
    @DisplayName("Should handle NoResourceFoundException")
    void shouldHandleNoResourceFound() {
        mockCorrelationId("corr-def");
        var ex = new NoResourceFoundException(null, "Resource not found");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(404));
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("Should handle AccessDeniedException")
    void shouldHandleAccessDenied() {
        mockCorrelationId("corr-ghi");
        var ex = new AccessDeniedException("Access denied");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleAccessDenied(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(403));
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("Should handle generic Exception")
    void shouldHandleGeneralException() {
        mockCorrelationId("corr-jkl");
        var ex = new RuntimeException("Unexpected");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleGeneral(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(500));
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    @DisplayName("Should fallback to 'unknown' when no correlation id in request")
    void shouldFallbackWhenNoCorrelationId() {
        when(request.getAttribute(anyString(), anyInt())).thenReturn(null);
        var ex = new InventoryDomainException("msg");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleDomainException(ex, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().correlationId()).isEqualTo("unknown");
    }
}
