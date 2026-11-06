package com.hongmap.hongmapbackend.ask.dto;

import com.hongmap.hongmapbackend.ask.AskOperatorNote;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 관리자 '운영진 확인 안내' 요청·응답. */
public final class AskOperatorNoteDto {

    private AskOperatorNoteDto() {
    }

    public record Create(
            @NotBlank @Size(max = AskOperatorNote.QUESTION_MAX) String question,
            @NotBlank @Size(max = AskOperatorNote.ANSWER_MAX) String answer,
            @Pattern(regexp = "(route:[0-9]{1,18}|flow:[A-Za-z0-9-]{8,64})") String askRef
    ) {
    }

    public record Update(
            @Size(min = 1, max = AskOperatorNote.QUESTION_MAX) String question,
            @Size(min = 1, max = AskOperatorNote.ANSWER_MAX) String answer,
            Boolean active
    ) {
    }

    public record Item(Long id, String question, String answer, String askRef, boolean active, Long createdBy,
                       LocalDateTime createdAt, LocalDateTime updatedAt) {
        public static Item of(AskOperatorNote note) {
            return new Item(note.getId(), note.getQuestion(), note.getAnswer(), note.getAskRef(), note.isActive(),
                    note.getCreatedBy(), note.getCreatedAt(), note.getUpdatedAt());
        }
    }

    public record ListResponse(List<Item> notes) {
    }
}
