package com.reeltrip.domain.admin;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.global.exception.ErrorResponse;

@RestControllerAdvice(assignableTypes = AdminController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminExceptionHandler {
    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class,
            QueryTimeoutException.class, TransactionTimedOutException.class})
    public ResponseEntity<ErrorResponse> conflict(Exception e) {
        var code = ErrorCode.ADMIN_OPERATION_BLOCKED;
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.from(code));
    }
}
