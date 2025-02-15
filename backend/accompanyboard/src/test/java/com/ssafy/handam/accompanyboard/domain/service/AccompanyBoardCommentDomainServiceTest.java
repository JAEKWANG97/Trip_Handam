package com.ssafy.handam.accompanyboard.domain.service;

import static org.junit.jupiter.api.Assertions.*;


import com.ssafy.handam.accompanyboard.domain.entity.Comment;
import com.ssafy.handam.accompanyboard.domain.repository.AccompanyBoardCommentRepository;
import com.ssafy.handam.accompanyboard.presentation.request.comment.AccompanyBoardCommentCreationRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AccompanyBoardCommentDomainServiceTest {

    @Autowired
    private AccompanyBoardCommentRepository accompanyBoardCommentRepository;

    @Autowired
    private AccompanyBoardCommentDomainService accompanyBoardCommentDomainService;

    @BeforeEach
    void setUp() {
        accompanyBoardCommentDomainService = new AccompanyBoardCommentDomainService(accompanyBoardCommentRepository);
    }

    @Test
    @DisplayName("동행 게시글 댓글 작성 단위 테스트 - H2 DB에 저장")
    void createComment() {
        AccompanyBoardCommentCreationRequest request = new AccompanyBoardCommentCreationRequest(
                1L,
                1L,
                "testContent"
        );

        // when: 댓글을 생성하고 DB에 저장
        Comment savedComment = accompanyBoardCommentDomainService.createComment(request);

        // Then: 저장된 기사가 DB에 있는지 확인
        Comment foundComment = accompanyBoardCommentRepository.findById(savedComment.getId()).orElse(null);

        assertNotNull(foundComment);  // 데이터가 null이 아닌지 확인
        assertEquals(request.userId(), foundComment.getUserId());  // 사용자 ID가 일치하는지 확인
        assertEquals(request.accompanyBoardArticleId(), foundComment.getAccompanyBoardArticleId());  // 게시글이 일치하는지 확인
        assertEquals(request.content(), foundComment.getContent());  // 내용이 일치하는지 확인
    }

    @Test
    @DisplayName("동행 게시글 댓글 조회 테스트 - H2 DB에 저장")
    void getCommentsByAccompanyBoardArticleIdTest() {
        Comment comment1 = new Comment(1L, 1L, "testContent1");
        Comment comment2 = new Comment(1L, 1L, "testContent2");
        Comment comment3 = new Comment(1L, 2L, "testContent3");
        Comment comment4 = new Comment(1L, 2L, "testContent4");
        Comment comment5 = new Comment(1L, 3L, "testContent5");

        accompanyBoardCommentRepository.save(comment1);
        accompanyBoardCommentRepository.save(comment2);
        accompanyBoardCommentRepository.save(comment3);
        accompanyBoardCommentRepository.save(comment4);
        accompanyBoardCommentRepository.save(comment5);

        List<Comment> result = accompanyBoardCommentDomainService.getCommentsByAccompanyBoardArticleId(2L);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getUserId());
        assertEquals(2L, result.get(0).getAccompanyBoardArticleId());
        assertEquals("testContent3", result.get(0).getContent());
        assertEquals(1L, result.get(1).getUserId());
        assertEquals(2L, result.get(1).getAccompanyBoardArticleId());
        assertEquals("testContent4", result.get(1).getContent());
    }
}