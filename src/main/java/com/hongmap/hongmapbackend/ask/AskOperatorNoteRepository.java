package com.hongmap.hongmapbackend.ask;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AskOperatorNoteRepository extends JpaRepository<AskOperatorNote, Long> {

    List<AskOperatorNote> findAllByOrderByIdDesc();

    List<AskOperatorNote> findByActiveTrue();
}
