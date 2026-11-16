package com.expensebot.expenses.infrastructure.web;

import com.expensebot.expenses.ExpenseNotFoundException;
import com.expensebot.expenses.UnknownCategoryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns the expenses module's client errors into RFC 9457 problem details. Only these two: any other exception
 * (including a stray {@link IllegalArgumentException} that got past the request validation) is a bug and stays a 500.
 * Spring's own errors (invalid body, failed validation) become problem details through
 * {@code spring.mvc.problemdetails.enabled}.
 */
@RestControllerAdvice(basePackageClasses = ExpenseExceptionHandler.class)
class ExpenseExceptionHandler {

    @ExceptionHandler(ExpenseNotFoundException.class)
    ProblemDetail notFound(ExpenseNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(UnknownCategoryException.class)
    ProblemDetail unknownCategory(UnknownCategoryException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
