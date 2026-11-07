package org.opensourcepingpong.competition;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.organization.AuthorizationService;
import org.opensourcepingpong.organization.Permission;
import org.springframework.jdbc.core.JdbcTemplate;

class ScheduleServiceTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final AuthorizationService auth = mock(AuthorizationService.class);
  private final ScheduleService service = new ScheduleService(jdbc, auth);
  private final UUID manager = UUID.randomUUID(), competition = UUID.randomUUID();

  @Test void thirteenPlayerPreviewHasThreeByesAndDoesNotPersist() {
    when(jdbc.queryForList(startsWith("select format,status"), eq(competition)))
        .thenReturn(List.of(Map.of("format", "SINGLE_ELIMINATION", "status", "ARRANGING")));
    var users = players(13);
    when(jdbc.queryForList(startsWith("select user_id"), eq(UUID.class), eq(competition))).thenReturn(users);
    var first = service.preview(manager, competition, 42);
    var second = service.preview(manager, competition, 42);
    assertEquals(first, second);
    assertEquals(13, first.get("participantCount"));
    var matches = (List<Map<String,Object>>) first.get("matches");
    assertEquals(15, matches.size());
    assertEquals(3, matches.stream().filter(row -> Boolean.TRUE.equals(row.get("bye"))).count());
    verify(auth, atLeastOnce()).require(manager, Permission.COMPETITION_MANAGE);
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test void fourPlayerRoundRobinPreviewHasSixMatches() {
    when(jdbc.queryForList(startsWith("select format,status"), eq(competition)))
        .thenReturn(List.of(Map.of("format", "ROUND_ROBIN", "status", "ARRANGING")));
    when(jdbc.queryForList(startsWith("select user_id"), eq(UUID.class), eq(competition))).thenReturn(players(4));
    var preview = service.preview(manager, competition, 42);
    assertEquals(6, ((List<?>) preview.get("matches")).size());
  }

  @Test void previewRejectsWrongState() {
    when(jdbc.queryForList(startsWith("select format,status"), eq(competition)))
        .thenReturn(List.of(Map.of("format", "ROUND_ROBIN", "status", "READY")));
    assertEquals("COMPETITION_INVALID_STATE", assertThrows(BusinessException.class,
        () -> service.preview(manager, competition, 42)).code());
  }

  private static List<UUID> players(int count) {
    List<UUID> users = new ArrayList<>();
    for (int i = 0; i < count; i++) users.add(new UUID(0, i + 1));
    return users;
  }
}
