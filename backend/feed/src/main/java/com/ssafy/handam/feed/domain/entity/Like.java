package com.ssafy.handam.feed.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "likes") // 예약어 충돌 방지
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Like extends  BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    private Feed feed;

    @Builder
    private Like(Long userId, Feed feed) {
        this.userId = userId;
        this.feed = feed;
    }
}