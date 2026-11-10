package org.opensourcepingpong.registration;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.opensourcepingpong.common.api.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 3 / E6-B: individual and double registration, waitlist promotion and
 * mock payment credentials. A competition row is the event aggregate here, so
 * registrations extend {@code competition_entries} and stay visible to the
 * scheduler through the {@code APPROVED} status.
 */
@Service
public class RegistrationService {
    static final Set<String> ACTIVE_REGISTRATION_STATUSES =
        Set.of("PENDING_PAYMENT", "PENDING", "APPROVED", "WAITLISTED");
    static final Set<String> SLOT_HOLDING_STATUSES =
        Set.of("PENDING_PAYMENT", "PENDING", "APPROVED");
    static final int MAX_EVENTS_PER_USER = 2;
    private static final DateTimeFormatter CREDENTIAL_DATE =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbc;

    public RegistrationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Map<String, Object> register(UUID userId, UUID competitionId, RegisterRequest request) {
        Instant now = Instant.now();
        var rows = jdbc.queryForList("""
            select id,status,registration_start,registration_end,registration_scope,
                   registration_requires_review,max_participants,filled_slots
            from competitions where id=?
            """, competitionId);
        if (rows.isEmpty()) throw notFound("COMPETITION_NOT_FOUND", "赛事不存在");
        var competition = rows.getFirst();
        if (!"REGISTRATION".equals(competition.get("status"))) {
            throw new BusinessException(HttpStatus.CONFLICT, "COMPETITION_REGISTRATION_CLOSED", "赛事未开放报名");
        }
        Instant start = instant(competition.get("registration_start"));
        Instant end = instant(competition.get("registration_end"));
        if ((start != null && now.isBefore(start)) || (end != null && !now.isBefore(end))) {
            throw new BusinessException(HttpStatus.CONFLICT, "COMPETITION_REGISTRATION_CLOSED", "不在报名时间内");
        }

        String type = normalizeType(request.type());
        String scope = (String) competition.get("registration_scope");
        requireEligible(userId, scope);
        if ("DOUBLE".equals(type)) requirePartner(request.partnerUserId(), scope, competitionId, userId);

        Long duplicate = jdbc.queryForObject("""
            select count(*) from registration_members rm
            join competition_entries e on e.id=rm.entry_id
            where e.competition_id=? and rm.user_id=?
              and e.status in ('PENDING_PAYMENT','PENDING','APPROVED','WAITLISTED')
            """, Long.class, competitionId, userId);
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "REG_DUPLICATE", "已报名该赛事");
        }
        Long activeEvents = jdbc.queryForObject("""
            select count(distinct e.competition_id) from registration_members rm
            join competition_entries e on e.id=rm.entry_id
            join competitions c on c.id=e.competition_id
            where rm.user_id=? and e.competition_id<>?
              and e.status in ('PENDING_PAYMENT','PENDING','APPROVED','WAITLISTED')
              and c.status not in ('FINISHED','CANCELLED')
            """, Long.class, userId, competitionId);
        if (activeEvents != null && activeEvents >= MAX_EVENTS_PER_USER) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                "REGISTRATION_LIMIT_EXCEEDED", "兼项超过 2 项");
        }

        int claimed = jdbc.update("""
            update competitions set filled_slots=filled_slots+1
            where id=? and filled_slots<max_participants
            """, competitionId);
        boolean review = Boolean.TRUE.equals(competition.get("registration_requires_review"));
        Integer waitlistNo = null;
        String status;
        if (claimed == 0) {
            jdbc.queryForList("select id from competitions where id=? for update", competitionId);
            // A withdrawal may have released a slot while we waited for the row lock.
            int reclaimed = jdbc.update("""
                update competitions set filled_slots=filled_slots+1
                where id=? and filled_slots<max_participants
                """, competitionId);
            if (reclaimed == 1) {
                status = review ? "PENDING" : "PENDING_PAYMENT";
            } else {
                waitlistNo = nextWaitlistNo(competitionId);
                status = "WAITLISTED";
            }
        } else {
            status = review ? "PENDING" : "PENDING_PAYMENT";
        }

        UUID entryId = UUID.randomUUID();
        Integer finalWaitlistNo = waitlistNo;
        try {
            jdbc.update("""
                insert into competition_entries(id,competition_id,user_id,status,entry_type,group_id,
                    partner_user_id,waitlist_no,registered_at,payment_status,refund_status)
                values(?,?,?,?,?,?,?,?,?,'UNPAID','NONE')
                """, statement -> {
                    statement.setObject(1, entryId);
                    statement.setObject(2, competitionId);
                    statement.setObject(3, userId);
                    statement.setString(4, status);
                    statement.setString(5, type);
                    if (request.groupId() == null) statement.setNull(6, java.sql.Types.OTHER);
                    else statement.setObject(6, request.groupId());
                    if (request.partnerUserId() == null) statement.setNull(7, java.sql.Types.OTHER);
                    else statement.setObject(7, request.partnerUserId());
                    if (finalWaitlistNo == null) statement.setNull(8, java.sql.Types.INTEGER);
                    else statement.setInt(8, finalWaitlistNo);
                    statement.setTimestamp(9, Timestamp.from(now));
                });
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, "REG_DUPLICATE", "已报名该赛事");
        }
        jdbc.update("insert into registration_members(id,entry_id,user_id,member_role) values(?,?,?,?)",
            UUID.randomUUID(), entryId, userId, "LEADER");
        if ("DOUBLE".equals(type)) {
            jdbc.update("insert into registration_members(id,entry_id,user_id,member_role) values(?,?,?,?)",
                UUID.randomUUID(), entryId, request.partnerUserId(), "PARTNER");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entryId);
        result.put("status", status);
        result.put("waitlistNo", waitlistNo);
        return result;
    }

    @Transactional
    public Map<String, Object> pay(UUID userId, UUID entryId) {
        var rows = jdbc.queryForList("""
            select e.id,e.user_id,e.status,e.payment_status,e.credential_no,e.qr_token,
                   c.fee_cents,c.registration_requires_review
            from competition_entries e join competitions c on c.id=e.competition_id
            where e.id=?
            """, entryId);
        if (rows.isEmpty()) throw notFound("REG_NOT_FOUND", "报名不存在");
        var entry = rows.getFirst();
        if (!userId.equals(entry.get("user_id"))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "只能支付自己的报名");
        }
        if ("WAITLISTED".equals(entry.get("status"))) {
            throw new BusinessException(HttpStatus.CONFLICT, "REG_WAITLISTED", "候补转正后才能支付");
        }
        if (Set.of("CANCELLED", "REJECTED", "REFUNDED").contains(entry.get("status"))) {
            throw new BusinessException(HttpStatus.CONFLICT, "REG_INVALID_STATE", "报名状态不允许支付");
        }
        if ("PAID".equals(entry.get("payment_status"))) {
            return credential(entryId, entry.get("credential_no"), entry.get("qr_token"), String.valueOf(entry.get("status")));
        }

        String credentialNo = credentialNo();
        String qrToken = UUID.randomUUID().toString();
        int amount = ((Number) entry.get("fee_cents")).intValue();
        jdbc.update("""
            insert into registration_payments(id,entry_id,amount_cents,status,mock_txn_no)
            values(?,?,?,'PAID',?) on conflict (entry_id) do nothing
            """, UUID.randomUUID(), entryId, amount, "MOCK-" + qrToken.substring(0, 18).toUpperCase());
        String newStatus = Boolean.TRUE.equals(entry.get("registration_requires_review")) ? "PENDING" : "APPROVED";
        int changed = jdbc.update("""
            update competition_entries set payment_status='PAID',paid_at=?,credential_no=?,qr_token=?,
                status=?,version=version+1
            where id=? and payment_status='UNPAID'
            """, Timestamp.from(Instant.now()), credentialNo, qrToken, newStatus, entryId);
        if (changed == 0) {
            var current = jdbc.queryForList(
                "select credential_no,qr_token,status from competition_entries where id=?", entryId);
            if (!current.isEmpty()) {
                var row = current.getFirst();
                return credential(entryId, row.get("credential_no"), row.get("qr_token"), String.valueOf(row.get("status")));
            }
        }
        return credential(entryId, credentialNo, qrToken, newStatus);
    }

    @Transactional
    public Map<String, Object> withdraw(UUID userId, UUID entryId) {
        var rows = jdbc.queryForList("""
            select e.id,e.user_id,e.status,e.payment_status,e.competition_id,
                   c.start_time,c.registration_requires_review
            from competition_entries e join competitions c on c.id=e.competition_id
            where e.id=? for update of e
            """, entryId);
        if (rows.isEmpty()) throw notFound("REG_NOT_FOUND", "报名不存在");
        var entry = rows.getFirst();
        if (!userId.equals(entry.get("user_id"))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "只能退出自己的报名");
        }
        String status = (String) entry.get("status");
        if (!ACTIVE_REGISTRATION_STATUSES.contains(status)) {
            throw new BusinessException(HttpStatus.CONFLICT, "REG_INVALID_STATE", "报名状态不允许退赛");
        }
        Instant startTime = instant(entry.get("start_time"));
        if (startTime != null && !Instant.now().isBefore(startTime.minusSeconds(24 * 3600))) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                "REG_WITHDRAW_TOO_LATE", "开赛前 24 小时内不可退赛");
        }

        boolean heldSlot = SLOT_HOLDING_STATUSES.contains(status);
        String refundStatus = "PAID".equals(entry.get("payment_status")) ? "REFUNDED" : "NONE";
        jdbc.update("""
            update competition_entries set status='CANCELLED',cancelled_at=?,refund_status=?,version=version+1
            where id=?
            """, Timestamp.from(Instant.now()), refundStatus, entryId);
        UUID competitionId = (UUID) entry.get("competition_id");
        UUID promotedEntryId = null;
        if (heldSlot) {
            jdbc.update("update competitions set filled_slots=greatest(filled_slots-1,0) where id=?", competitionId);
            promotedEntryId = promoteWaitlist(competitionId,
                Boolean.TRUE.equals(entry.get("registration_requires_review")));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entryId);
        result.put("status", "CANCELLED");
        result.put("refundStatus", refundStatus);
        result.put("promotedEntryId", promotedEntryId);
        return result;
    }

    public List<Map<String, Object>> mine(UUID userId) {
        return jdbc.queryForList("""
            select e.id,e.competition_id,c.title,c.sport,c.start_time,c.location,
                   e.entry_type,e.group_id,e.status,e.waitlist_no,e.credential_no,e.qr_token,
                   e.payment_status,e.refund_status,e.registered_at
            from competition_entries e join competitions c on c.id=e.competition_id
            where e.user_id=? or exists (
                select 1 from registration_members rm where rm.entry_id=e.id and rm.user_id=?
            )
            order by e.registered_at desc
            """, userId, userId);
    }

    private UUID promoteWaitlist(UUID competitionId, boolean review) {
        var rows = jdbc.queryForList("""
            select id,user_id from competition_entries
            where competition_id=? and status='WAITLISTED'
            order by waitlist_no nulls last,registered_at,id
            limit 1 for update
            """, competitionId);
        if (rows.isEmpty()) return null;
        var next = rows.getFirst();
        UUID promotedId = (UUID) next.get("id");
        UUID promotedUser = (UUID) next.get("user_id");
        String status = review ? "PENDING" : "PENDING_PAYMENT";
        jdbc.update("""
            update competition_entries set status=?,waitlist_no=null,version=version+1 where id=?
            """, status, promotedId);
        jdbc.update("update competitions set filled_slots=filled_slots+1 where id=?", competitionId);
        jdbc.update("""
            insert into notifications(id,user_id,notification_type,title,content)
            values(?,?,?,?,?)
            """, UUID.randomUUID(), promotedUser, "REGISTRATION_PROMOTED",
            "候补转正", "您已从候补转为正式报名，请及时完成支付");
        return promotedId;
    }

    private void requireEligible(UUID userId, String scope) {
        var users = jdbc.queryForList("select status from users where id=?", userId);
        if (users.isEmpty() || !"NORMAL".equals(users.getFirst().get("status"))) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "REG_NOT_ELIGIBLE", "账号状态不允许报名");
        }
        if ("MEMBERS_ONLY".equals(scope)
            && jdbc.queryForList("select id from memberships where user_id=? and status='ACTIVE'", userId).isEmpty()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "REG_NOT_ELIGIBLE", "仅限正式成员报名");
        }
        var credits = jdbc.queryForList("select score from credit_accounts where user_id=?", userId);
        if (!credits.isEmpty() && ((Number) credits.getFirst().get("score")).intValue() < 60) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "REG_CREDIT_TOO_LOW", "信用分低于 60");
        }
    }

    private void requirePartner(UUID partnerUserId, String scope, UUID competitionId, UUID userId) {
        if (partnerUserId == null) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "REG_NOT_ELIGIBLE", "双打报名缺少搭档");
        }
        if (partnerUserId.equals(userId)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "REG_NOT_ELIGIBLE", "不能与自己组队");
        }
        requireEligible(partnerUserId, scope);
        Long partnerEntries = jdbc.queryForObject("""
            select count(*) from registration_members rm
            join competition_entries e on e.id=rm.entry_id
            where e.competition_id=? and rm.user_id=?
              and e.status in ('PENDING_PAYMENT','PENDING','APPROVED','WAITLISTED')
            """, Long.class, competitionId, partnerUserId);
        if (partnerEntries != null && partnerEntries > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "REG_DUPLICATE", "搭档已报名该赛事");
        }
    }

    private Integer nextWaitlistNo(UUID competitionId) {
        Integer next = jdbc.queryForObject("""
            select coalesce(max(waitlist_no),0)+1 from competition_entries
            where competition_id=? and status='WAITLISTED'
            """, Integer.class, competitionId);
        return next == null ? 1 : next;
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) return "INDIVIDUAL";
        String normalized = type.trim().toUpperCase();
        if (!normalized.equals("INDIVIDUAL") && !normalized.equals("DOUBLE")) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "报名类型必须为 INDIVIDUAL 或 DOUBLE");
        }
        return normalized;
    }

    private static Map<String, Object> credential(UUID entryId, Object credentialNo, Object qrToken, String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entryId);
        result.put("status", status);
        result.put("credentialNo", credentialNo);
        result.put("qrToken", qrToken);
        return result;
    }

    private static String credentialNo() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "REG-" + CREDENTIAL_DATE.format(Instant.now()) + "-" + suffix;
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.OffsetDateTime offset) return offset.toInstant();
        throw new IllegalArgumentException("unsupported time value: " + value.getClass());
    }

    private static BusinessException notFound(String code, String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, code, message);
    }

    public record RegisterRequest(String type, UUID partnerUserId, UUID groupId) {}
}
