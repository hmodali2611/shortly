package com.example.shortener.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String type;

	public ApiException(HttpStatus status, String type, String message) {
		super(message);
		this.status = status;
		this.type = type;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getType() {
		return type;
	}
}