package com.reeltrip.domain.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.reeltrip.global.config.TimeConfig;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

/** 관리 화면은 필요한 열만 읽어요. 초기화 SQL의 테이블·조건은 코드에서 고정해요. */
@Repository
public class AdminDataRepository {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public AdminDataRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    public Page<UserRow> users(String query, int page, int size) {
        return page(jdbc.query("SELECT user_id,email,nickname FROM `user` WHERE "
                + "LOCATE(LOWER(?),LOWER(email))>0 OR LOCATE(LOWER(?),LOWER(nickname))>0 "
                + "OR CAST(user_id AS CHAR)=? ORDER BY user_id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new UserRow(rs.getLong("user_id"), rs.getString("email"), rs.getString("nickname")),
                query, query, query, size + 1, (long) page * size), page, size);
    }

    public Page<SourceRow> sources(Long userId, int page, int size) {
        return read("SELECT s.source_id,s.title,s.status AS source_status,l.source_link_id,l.url,l.platform,l.status,"
                + "l.fail_code,l.fail_message,l.updated_at FROM source s JOIN source_link l ON l.source_id=s.source_id "
                + "WHERE s.user_id=? ORDER BY s.source_id DESC,l.source_link_id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new SourceRow(rs.getLong("source_id"), rs.getString("title"), rs.getString("source_status"),
                        rs.getLong("source_link_id"), rs.getString("url"), rs.getString("platform"),
                        rs.getString("status"), rs.getString("fail_code"), rs.getString("fail_message"), time(rs, "updated_at")),
                userId, page, size);
    }

    public Page<PlaceRow> places(Long userId, boolean trash, int page, int size) {
        return read("SELECT place_id,category,name,address,latitude,longitude,city,deleted_at,updated_at FROM place "
                + "WHERE user_id=? AND deleted_at IS " + (trash ? "NOT NULL" : "NULL") + " ORDER BY place_id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new PlaceRow(rs.getLong("place_id"), rs.getString("category"), rs.getString("name"),
                        rs.getString("address"), rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"),
                        rs.getString("city"), time(rs, "deleted_at"), time(rs, "updated_at")), userId, page, size);
    }

    public Page<PlanRow> plans(Long userId, int page, int size) {
        return read("SELECT plan_id,name,city,start_date,end_date,status,share_type FROM plan WHERE user_id=? "
                + "ORDER BY plan_id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new PlanRow(rs.getLong("plan_id"), rs.getString("name"), rs.getString("city"),
                        rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate(),
                        rs.getString("status"), rs.getString("share_type")), userId, page, size);
    }

    public void requireOwnedPlan(Long userId, Long planId) {
        if (count("SELECT COUNT(*) FROM plan WHERE user_id=? AND plan_id=?", userId, planId) != 1) {
            throw new BusinessException(ErrorCode.PLAN_NOT_FOUND);
        }
    }

    public List<String> blockers(Long userId) {
        var blockers = new java.util.ArrayList<String>();
        if (count("SELECT COUNT(*) FROM source WHERE user_id=? AND status NOT IN ('DONE','FAILED')", userId) > 0
                || count("SELECT COUNT(*) FROM source_link l JOIN source s ON s.source_id=l.source_id "
                + "WHERE s.user_id=? AND l.status NOT IN ('DONE','FAILED')", userId) > 0) {
            blockers.add("진행 중인 링크 분석이 있어요. 완료 후 다시 확인해요.");
        }
        if (count("SELECT COUNT(*) FROM plan p WHERE p.user_id=? AND (p.share_type IS NOT NULL OR EXISTS "
                + "(SELECT 1 FROM plan_member m WHERE m.plan_id=p.plan_id AND m.user_id<>?))", userId, userId) > 0) {
            blockers.add("공유 중이거나 다른 회원이 참여한 계획이 있어요. 공유와 참여 관계를 먼저 정리해요.");
        }
        if (count("SELECT COUNT(*) FROM plan_node n JOIN plan p ON p.plan_id=n.plan_id "
                + "JOIN place c ON c.place_id=n.place_id WHERE c.user_id=? AND p.user_id<>?", userId, userId) > 0
                || count("SELECT COUNT(*) FROM plan_stay n JOIN plan p ON p.plan_id=n.plan_id "
                + "JOIN place c ON c.place_id=n.place_id WHERE c.user_id=? AND p.user_id<>?", userId, userId) > 0
                || count("SELECT COUNT(*) FROM plan_alternative a JOIN plan_node n ON n.plan_node_id=a.plan_node_id "
                + "JOIN plan p ON p.plan_id=n.plan_id JOIN place c ON c.place_id=a.place_id "
                + "WHERE c.user_id=? AND p.user_id<>?", userId, userId) > 0) {
            blockers.add("이 계정의 카드를 다른 계정의 계획에서 사용하고 있어요.");
        }
        if (count("SELECT COUNT(*) FROM place c JOIN source_link l ON l.source_link_id=c.source_link_id "
                + "JOIN source s ON s.source_id=l.source_id WHERE s.user_id=? AND c.user_id<>?", userId, userId) > 0) {
            blockers.add("이 계정의 분석 링크를 다른 계정의 카드에서 사용하고 있어요.");
        }
        return List.copyOf(blockers);
    }

    /** 부모를 잠근 뒤 자식 ID를 읽어 새 FK 참조가 삭제 중 끼어들지 않게 해요. */
    public Map<String, List<Long>> snapshot(Long userId, boolean lock) {
        var result = new LinkedHashMap<String, List<Long>>();
        result.put("source", ids("s.source_id", "source s", "s.user_id", userId, lock));
        result.put("source_link", ids("l.source_link_id", "source_link l JOIN source s ON s.source_id=l.source_id", "s.user_id", userId, lock));
        result.put("plan", ids("p.plan_id", "plan p", "p.user_id", userId, lock));
        result.put("place", ids("c.place_id", "place c", "c.user_id", userId, lock));
        result.put("plan_day", ids("d.plan_day_id", "plan_day d JOIN plan p ON p.plan_id=d.plan_id", "p.user_id", userId, lock));
        result.put("plan_node", ids("n.plan_node_id", "plan_node n JOIN plan p ON p.plan_id=n.plan_id", "p.user_id", userId, lock));
        result.put("plan_stay", ids("n.plan_stay_id", "plan_stay n JOIN plan p ON p.plan_id=n.plan_id", "p.user_id", userId, lock));
        result.put("plan_alternative", ids("a.plan_alternative_id", "plan_alternative a JOIN plan_node n ON n.plan_node_id=a.plan_node_id JOIN plan p ON p.plan_id=n.plan_id", "p.user_id", userId, lock));
        result.put("plan_day_area", ids("a.plan_day_area_id", "plan_day_area a JOIN plan_day d ON d.plan_day_id=a.plan_day_id JOIN plan p ON p.plan_id=d.plan_id", "p.user_id", userId, lock));
        result.put("plan_member", ids("m.plan_member_id", "plan_member m JOIN plan p ON p.plan_id=m.plan_id", "p.user_id", userId, lock));
        result.put("place_key_point", ids("k.place_key_point_id", "place_key_point k JOIN place c ON c.place_id=k.place_id", "c.user_id", userId, lock));
        return result;
    }

    public void deleteSnapshot(Map<String, List<Long>> snapshot) {
        for (String table : List.of("plan_alternative", "plan_node", "plan_stay", "plan_day_area", "plan_member",
                "plan_day", "plan", "place_key_point", "place", "source_link", "source")) {
            var ids = snapshot.get(table);
            if (ids.isEmpty()) { continue; }
            int changed = named.update("DELETE FROM " + table + " WHERE " + table + "_id IN (:ids)", Map.of("ids", ids));
            if (changed != ids.size()) { throw new BusinessException(ErrorCode.ADMIN_PREVIEW_CHANGED); }
        }
    }

    private List<Long> ids(String column, String from, String ownerColumn, Long userId, boolean lock) {
        var ids = jdbc.queryForList("SELECT " + column + " FROM " + from + " WHERE " + ownerColumn
                + "=? ORDER BY " + column + " LIMIT 10001" + (lock ? " FOR UPDATE" : ""), Long.class, userId);
        if (ids.size() > 10000) { throw new BusinessException(ErrorCode.ADMIN_OPERATION_BLOCKED); }
        return ids;
    }

    private long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }

    private <T> Page<T> read(String sql, RowMapper<T> mapper, Long userId, int page, int size) {
        return page(jdbc.query(sql, mapper, userId, size + 1, (long) page * size), page, size);
    }

    private static <T> Page<T> page(List<T> rows, int page, int size) {
        return new Page<>(List.copyOf(rows.subList(0, Math.min(size, rows.size()))), page, size, rows.size() > size);
    }

    private static OffsetDateTime time(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime().atZone(TimeConfig.SEOUL).toOffsetDateTime();
    }

    public record Page<T>(List<T> items, int page, int size, boolean hasNext) { }
    public record UserRow(Long userId, String email, String nickname) { }
    public record SourceRow(Long sourceId, String title, String sourceStatus, Long sourceLinkId, String url,
                            String platform, String status, String failCode, String failMessage, OffsetDateTime updatedAt) { }
    public record PlaceRow(Long placeId, String category, String name, String address, java.math.BigDecimal latitude,
                           java.math.BigDecimal longitude, String city, OffsetDateTime deletedAt, OffsetDateTime updatedAt) { }
    public record PlanRow(Long planId, String name, String city, LocalDate startDate, LocalDate endDate,
                          String status, String shareType) { }
}
