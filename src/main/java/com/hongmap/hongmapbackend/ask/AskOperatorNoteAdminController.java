package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.ask.dto.AskOperatorNoteDto;
import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 관리자 '운영진 확인 안내'(11-06). /admin/** 는 관리자만(SecurityConfig). 지우지 않고 끈다(active=false) — 언제 무엇을 답했는지 남긴다. */
@RestController
@RequiredArgsConstructor
@Tag(name = SwaggerConfig.TAG_ADMIN)
public class AskOperatorNoteAdminController {

    private final AskOperatorNoteRepository repository;
    private final AskOperatorNotes notes;

    @Operation(summary = "운영진 확인 안내 목록(관리자)", description = "최신순, 꺼진 것도 함께.")
    @GetMapping("/admin/ask/notes")
    @Transactional(readOnly = true)
    public AskOperatorNoteDto.ListResponse list() {
        return new AskOperatorNoteDto.ListResponse(repository.findAllByOrderByIdDesc().stream().map(AskOperatorNoteDto.Item::of).toList());
    }

    @Operation(summary = "운영진 확인 안내 등록(관리자)", description = "물어보기가 비슷한 질문에 답할 때 근거로 읽는다. "
            + "휴대전화·주민등록번호·학번 형태가 있으면 400.")
    @PostMapping("/admin/ask/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AskOperatorNoteDto.Item create(@AuthenticationPrincipal Long adminId, @Valid @RequestBody AskOperatorNoteDto.Create request) {
        String question = request.question().strip();
        String answer = request.answer().strip();
        rejectPersonal(question, answer);
        AskOperatorNote saved = repository.save(new AskOperatorNote(question, answer,
                request.askRef() == null || request.askRef().isBlank() ? null : request.askRef().strip(), adminId));
        notes.reload();
        return AskOperatorNoteDto.Item.of(saved);
    }

    @Operation(summary = "운영진 확인 안내 고치기·끄기(관리자)", description = "question·answer·active 중 보낸 것만 바꾼다.")
    @PatchMapping("/admin/ask/notes/{id}")
    @Transactional
    public AskOperatorNoteDto.Item update(@PathVariable long id, @Valid @RequestBody AskOperatorNoteDto.Update request) {
        AskOperatorNote note = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 안내입니다."));
        String question = request.question() == null ? null : request.question().strip();
        String answer = request.answer() == null ? null : request.answer().strip();
        rejectPersonal(question, answer);
        note.edit(question, answer, request.active());
        repository.flush();
        notes.reload();
        return AskOperatorNoteDto.Item.of(note);
    }

    private static void rejectPersonal(String... texts) {
        for (String text : texts) {
            String kind = AskPrivacy.personalKind(text);
            if (kind != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        kind + "처럼 보이는 내용이 있어요. 학생 개인 정보는 빼고 적어 주세요.");
            }
        }
    }
}
