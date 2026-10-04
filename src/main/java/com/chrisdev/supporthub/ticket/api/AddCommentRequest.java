package com.chrisdev.supporthub.ticket.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddCommentRequest(@NotBlank @Size(max = 2000) String body) {
}