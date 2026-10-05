package com.hongmap.hongmapbackend.community;

/** 제보의 🔥 수가 이정표(10·50·100)를 처음 넘었을 때. 커밋 뒤 작성자에게 푸시한다(ReportCommunityPushDispatcher). */
public record ReportFireMilestoneEvent(Long reportId, Long authorId, String reportTitle, int milestone) {
}
