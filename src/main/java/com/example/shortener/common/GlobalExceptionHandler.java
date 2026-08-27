package com.example.shortener.common;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
}