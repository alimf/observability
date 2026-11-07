package org.alim.user.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "email is required")
        @Size(max = 254, message = "email must be at most 254 characters")
        String email,

        @NotBlank(message = "password is required")
        @Size(max = 72, message = "password must be at most 72 characters")
        String password
) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
