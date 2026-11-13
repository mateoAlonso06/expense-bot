package com.expensebot.accounts.application;

import com.expensebot.accounts.IssuedLinkCode;
import com.expensebot.accounts.domain.LinkCode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@Tag("mocks")
class RequestLinkCodeServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    @Mock
    private LinkCodeRepository linkCodeRepository;

    private RequestLinkCodeService service() {
        return new RequestLinkCodeService(linkCodeRepository, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(10));
    }

    @Test
    void issuesACodeInTheFormatTheUserTypes() {
        IssuedLinkCode issued = service().execute(UUID.randomUUID());

        assertThat(issued.code()).matches("[A-Z2-9]{4}-[A-Z2-9]{4}");
    }

    @Test
    void issuesACodeThatExpiresAfterTheTtl() {
        IssuedLinkCode issued = service().execute(UUID.randomUUID());

        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    void storesTheHashOfTheCodeForTheUserNeverTheCode() {
        var userId = UUID.randomUUID();

        IssuedLinkCode issued = service().execute(userId);

        var stored = ArgumentCaptor.forClass(LinkCode.class);
        verify(linkCodeRepository).save(stored.capture());
        assertThat(stored.getValue().getUserId()).isEqualTo(userId);
        assertThat(stored.getValue().getExpiresAt()).isEqualTo(issued.expiresAt());
        assertThat(stored.getValue().getCodeHash())
                .isEqualTo(LinkCodeFormat.hash(issued.code()))
                .doesNotContain(issued.code());
    }
}
