package com.expensebot.accounts.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProfileTest {

    @Test
    void createsAProfileWithIdAndEmail() {
        var id = UUID.randomUUID();

        var profile = UserProfile.create(id, "ana@example.com");

        assertThat(profile.getId()).isEqualTo(id);
        assertThat(profile.getEmail()).isEqualTo("ana@example.com");
    }

    @Test
    void rejectsABlankEmail() {
        assertThatThrownBy(() -> UserProfile.create(UUID.randomUUID(), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingIdOrEmail() {
        assertThatThrownBy(() -> UserProfile.create(null, "ana@example.com"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> UserProfile.create(UUID.randomUUID(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void restoreRebuildsWithoutValidating() {
        var id = UUID.randomUUID();

        var profile = UserProfile.restore(id, "ana@example.com");

        assertThat(profile.getId()).isEqualTo(id);
    }

    @Test
    void isIdentifiedByIdNotByEmail() {
        var id = UUID.randomUUID();

        assertThat(UserProfile.restore(id, "ana@example.com")).isEqualTo(UserProfile.restore(id, "other@example.com"));
        assertThat(UserProfile.restore(id, "ana@example.com")).isNotEqualTo(UserProfile.restore(UUID.randomUUID(), "ana@example.com"));
    }
}
