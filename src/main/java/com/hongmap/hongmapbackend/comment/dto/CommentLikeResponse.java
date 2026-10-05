package com.hongmap.hongmapbackend.comment.dto;

/** 댓글 👍 누르기·끄기 결과. */
public record CommentLikeResponse(boolean liked, long likeCount) {
}
