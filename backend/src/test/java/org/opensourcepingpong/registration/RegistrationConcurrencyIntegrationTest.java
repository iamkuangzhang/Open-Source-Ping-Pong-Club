package org.opensourcepingpong.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Verifies AC-M02-2: 100 concurrent registrations never oversell the quota. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class RegistrationConcurrencyIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired RegistrationService registrations;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void oneHundredConcurrentRegistrationsDoNotOversell() throws Exception {
        UUID creator = UUID.randomUUID();
        List<UUID> users = new ArrayList<>();
        for (int index = 0; index < 100; index++) users.add(UUID.randomUUID());
        try {
            jdbc.update("insert into users(id,account,password_hash,status) values(?,?,?,'NORMAL')",
                creator, "s3c_" + creator.toString().substring(0, 27), "test-only-hash");
            for (UUID user : users) {
                jdbc.update("insert into users(id,account,password_hash,status) values(?,?,?,'NORMAL')",
                    user, "s3c_" + user.toString().substring(0, 27), "test-only-hash");
            }
            UUID competition = UUID.randomUUID();
            jdbc.update("""
                insert into competitions(id,title,competition_type,event_type,format,status,registration_scope,
                    registration_requires_review,max_participants,best_of,location,creator_user_id,
                    registration_start,registration_end,start_time,filled_slots)
                values(?,'Sprint 3 并发报名测试','DAILY','SINGLES','SINGLE_ELIMINATION','REGISTRATION',
                    'PUBLIC',false,10,3,'测试场地',?,?,?,?,0)
                """, competition, creator,
                Timestamp.from(Instant.now().minusSeconds(3600)),
                Timestamp.from(Instant.now().plusSeconds(86400)),
                Timestamp.from(Instant.now().plusSeconds(172800)));

            ExecutorService pool = Executors.newFixedThreadPool(16);
            CountDownLatch ready = new CountDownLatch(100);
            CountDownLatch start = new CountDownLatch(1);
            List<Throwable> failures = new ArrayList<>();
            for (UUID user : users) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        registrations.register(user, competition,
                            new RegistrationService.RegisterRequest("INDIVIDUAL", null, null));
                    } catch (Throwable throwable) {
                        synchronized (failures) {
                            failures.add(throwable);
                        }
                    }
                });
            }
            ready.await(30, TimeUnit.SECONDS);
            start.countDown();
            pool.shutdown();
            pool.awaitTermination(120, TimeUnit.SECONDS);

            assertEquals(List.of(), failures);
            assertEquals(10, jdbc.queryForObject("""
                select count(*) from competition_entries
                where competition_id=? and status='PENDING_PAYMENT'
                """, Integer.class, competition));
            assertEquals(90, jdbc.queryForObject("""
                select count(*) from competition_entries
                where competition_id=? and status='WAITLISTED'
                """, Integer.class, competition));
            assertEquals(10, jdbc.queryForObject(
                "select filled_slots from competitions where id=?", Integer.class, competition));
            assertEquals(90, jdbc.queryForObject("""
                select count(distinct waitlist_no) from competition_entries
                where competition_id=? and status='WAITLISTED'
                """, Integer.class, competition));
        } finally {
            jdbc.update("delete from registration_members where entry_id in "
                + "(select id from competition_entries where competition_id in "
                + "(select id from competitions where creator_user_id=?))", creator);
            jdbc.update("delete from competition_entries where competition_id in "
                + "(select id from competitions where creator_user_id=?)", creator);
            jdbc.update("delete from competitions where creator_user_id=?", creator);
            jdbc.update("delete from users where account like 's3c_%'");
        }
    }
}
