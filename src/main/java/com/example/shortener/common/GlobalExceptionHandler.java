package com.example.shortener.common;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ProblemDetail handleApiException(ApiException exception, HttpServletRequest request) {
		ProblemDetail detail = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
		detail.setType(URI.create("https://sho.rt/errors/" + exception.getType()));
		detail.setTitle(exception.getStatus().getReasonPhrase());
		detail.setInstance(URI.create(request.getRequestURI()));
		return detail;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
		String message = exception.getBindingResult().getFieldErrors().stream().findFirst()
				.map(error -> error.getField() + ": " + error.getDefaultMessage()).orElse("Request validation failed");
		ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
		detail.setType(URI.create("https://sho.rt/errors/invalid-request"));
		detail.setTitle("Invalid request");
		detail.setInstance(URI.create(request.getRequestURI()));
		return detail;
	}

	@ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
	ProblemDetail handleDataAccess(Exception exception, HttpServletRequest request) {
		LOGGER.warn("Storage unavailable while handling {}", request.getRequestURI(), exception);
		ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"The service is temporarily unavailable");
		detail.setType(URI.create("https://sho.rt/errors/link-store-unavailable"));
		detail.setTitle(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
		detail.setInstance(URI.create(request.getRequestURI()));
		return detail;
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
		LOGGER.error("Unhandled exception while handling {}", request.getRequestURI(), exception);
		ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"An unexpected error occurred");
		detail.setType(URI.create("https://sho.rt/errors/internal-error"));
		detail.setTitle(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
		detail.setInstance(URI.create(request.getRequestURI()));
		return detail;
	}
}