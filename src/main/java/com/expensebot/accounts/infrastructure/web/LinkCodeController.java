package com.expensebot.accounts.infrastructure.web;

import com.expensebot.accounts.IssuedLinkCode;
import com.expensebot.accounts.RequestLinkCodeUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/link-codes")
public class LinkCodeController {
    private final RequestLinkCodeUseCase requestLinkCode;

    public LinkCodeController(RequestLinkCodeUseCase requestLinkCode) {
        this.requestLinkCode = requestLinkCode;
    }

    @PostMapping
    public ResponseEntity<IssuedLinkCode> request(@AuthenticationPrincipal Jwt jwt) {
        // UserProvisioningFilter has already rejected any token whose subject is not a UUID.
        IssuedLinkCode issued = requestLinkCode.execute(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.status(HttpStatus.CREATED).body(issued);
    }
}
