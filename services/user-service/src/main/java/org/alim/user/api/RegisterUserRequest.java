package org.alim.user.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Size(max = 254, message = "email must be at most 254 characters")
        String email,

        // bcrypt only considers the first 72 bytes, so longer passwords add no strength.
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
        String password,

        @NotBlank(message = "displayName is required")
        @Size(max = 100, message = "displayName must be at most 100 characters")
        String displayName
) {

    @Override
    public String toString() {
        return "RegisterUserRequest[email=" + email + ", password=***, displayName=" + displayName + "]";
    }
}
