package com.liang.xz.system.service;

import com.liang.xz.system.dto.NoticePageQuery;
import com.liang.xz.system.dto.NoticeRequest;
import com.liang.xz.system.dto.NoticeResponse;
import com.liang.xz.system.dto.PageResponse;
import com.liang.xz.system.entity.SysNoticeEntity;
import com.liang.xz.system.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 系统公告服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;

    public List<NoticeResponse> listPublished(int limit) {
        return noticeRepository.findPublished(limit).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public PageResponse<NoticeResponse> page(NoticePageQuery query) {
        long total = noticeRepository.count(query);
        if (total == 0) {
            return PageResponse.empty(query.getPage(), query.getPageSize());
        }
        List<NoticeResponse> records = noticeRepository.findPage(query).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return PageResponse.of(records, total, query.getPage(), query.getPageSize());
    }

    public NoticeResponse getById(Long id) {
        return noticeRepository.findById(id)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional
    public NoticeResponse create(NoticeRequest request, Long publisherId, String publisherName) {
        SysNoticeEntity entity = SysNoticeEntity.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .noticeType(request.getNoticeType())
                .priority(request.getPriority() != null ? request.getPriority() : 1)
                .publisherId(publisherId)
                .publisherName(publisherName)
                .top(request.getTop() != null ? request.getTop() : false)
                .publishTime(request.getPublishTime() != null ? request.getPublishTime() : LocalDateTime.now())
                .expireTime(request.getExpireTime())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .readCount(0L)
                .build();
        Long id = noticeRepository.insert(entity);
        entity.setId(id);
        log.info("[Notice] 创建公告: id={}, title={}", id, entity.getTitle());
        return toResponse(entity);
    }

    @Transactional
    public NoticeResponse update(Long id, NoticeRequest request) {
        return noticeRepository.findById(id).map(existing -> {
            SysNoticeEntity entity = SysNoticeEntity.builder()
                    .id(id)
                    .title(request.getTitle())
                    .content(request.getContent())
                    .noticeType(request.getNoticeType())
                    .priority(request.getPriority())
                    .publisherId(existing.getPublisherId())
                    .publisherName(existing.getPublisherName())
                    .top(request.getTop())
                    .publishTime(request.getPublishTime())
                    .expireTime(request.getExpireTime())
                    .enabled(request.getEnabled())
                    .readCount(existing.getReadCount())
                    .build();
            noticeRepository.update(entity);
            log.info("[Notice] 更新公告: id={}", id);
            return toResponse(entity);
        }).orElse(null);
    }

    @Transactional
    public boolean delete(Long id) {
        int rows = noticeRepository.deleteById(id);
        if (rows > 0) {
            log.info("[Notice] 删除公告: id={}", id);
        }
        return rows > 0;
    }

    private NoticeResponse toResponse(SysNoticeEntity e) {
        return NoticeResponse.builder()
                .id(e.getId())
                .title(e.getTitle())
                .content(e.getContent())
                .noticeType(e.getNoticeType())
                .priority(e.getPriority())
                .publisherId(e.getPublisherId())
                .publisherName(e.getPublisherName())
                .top(e.getTop())
                .publishTime(e.getPublishTime())
                .expireTime(e.getExpireTime())
                .enabled(e.getEnabled())
                .readCount(e.getReadCount())
                .createTime(e.getCreateTime())
                .updateTime(e.getUpdateTime())
                .build();
    }
}
