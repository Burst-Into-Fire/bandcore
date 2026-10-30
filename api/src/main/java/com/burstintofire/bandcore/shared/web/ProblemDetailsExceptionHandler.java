package com.burstintofire.bandcore.shared.web;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception raised by a controller into an {@code application/problem+json}
 * response. Errors raised outside controllers are handled by {@link ProblemDetailsErrorController}.
 */
@RestControllerAdvice
class ProblemDetailsExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ProblemDetailsExceptionHandler.class);

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.sorted(Comparator.comparing(FieldError::getField))
			.map(ProblemDetailsExceptionHandler::fieldError)
			.toList();
		ProblemDetail problem = Problems.of(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
				"The request contains invalid fields.");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = Problems.of(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
				"The request contains invalid parameters.");
		return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		// The default detail may echo parser internals; keep it generic.
		ProblemDetail problem = Problems.of(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
				"The request body is missing or malformed.");
		return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
	}

	/**
	 * Last resort: log the full error with the request id and return a generic 500. Security
	 * exceptions are rethrown so Spring Security can answer with 401 or 403.
	 */
	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) throws Exception {
		if (ex instanceof AuthenticationException || ex instanceof AccessDeniedException) {
			throw ex;
		}
		log.error("Unhandled exception", ex);
		ProblemDetail problem = Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
				"An unexpected error occurred.");
		return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
	}

	@Override
	protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode statusCode,
			WebRequest request) {
		if (body instanceof ProblemDetail problem) {
			HttpServletRequest servletRequest = request instanceof NativeWebRequest nativeRequest
					? nativeRequest.getNativeRequest(HttpServletRequest.class) : null;
			Problems.enrich(problem, servletRequest);
		}
		return super.createResponseEntity(body, headers, statusCode, request);
	}

	private static Map<String, String> fieldError(FieldError error) {
		String code = error.getCode() != null ? error.getCode() : "Invalid";
		String message = error.getDefaultMessage() != null ? error.getDefaultMessage() : "is invalid";
		return Map.of("field", error.getField(), "code", code, "message", message);
	}

}
