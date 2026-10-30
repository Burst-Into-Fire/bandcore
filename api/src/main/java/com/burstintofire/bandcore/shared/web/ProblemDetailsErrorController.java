package com.burstintofire.bandcore.shared.web;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.shared.logging.RequestId;

/**
 * Replaces Spring Boot's default {@code /error} page, which is used for errors raised outside
 * controllers (filters, Spring Security's 401 and 403, rejected requests). The response is
 * Problem Details and never contains the exception message.
 */
@Hidden
@RestController
class ProblemDetailsErrorController implements ErrorController {

	private static final Logger log = LoggerFactory.getLogger(ProblemDetailsErrorController.class);

	@RequestMapping("${server.error.path:/error}")
	ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
		HttpStatusCode status = status(request);
		if (status.is5xxServerError() && request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable ex) {
			withRequestId(request, () -> log.error("Unhandled exception outside a controller", ex));
		}
		ProblemDetail problem = Problems.of(status, ErrorCode.forStatus(status), null);
		Problems.enrich(problem, request);
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
	}

	private static HttpStatusCode status(HttpServletRequest request) {
		if (request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code && code >= 400
				&& code <= 599) {
			return HttpStatusCode.valueOf(code);
		}
		return HttpStatus.INTERNAL_SERVER_ERROR;
	}

	/** The request id filter does not run again on error dispatches, so restore the MDC here. */
	private static void withRequestId(HttpServletRequest request, Runnable action) {
		if (request.getAttribute(RequestId.ATTRIBUTE) instanceof String requestId) {
			try (MDC.MDCCloseable ignored = MDC.putCloseable(RequestId.MDC_KEY, requestId)) {
				action.run();
			}
		}
		else {
			action.run();
		}
	}

}
