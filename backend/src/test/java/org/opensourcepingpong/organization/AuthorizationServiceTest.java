package org.opensourcepingpong.organization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.opensourcepingpong.common.api.BusinessException;

class AuthorizationServiceTest {
  private final UserOrganizationRoleRepository repository = mock(UserOrganizationRoleRepository.class);
  private final AuthorizationService authorization = new AuthorizationService(repository);

  @Test
  void adminCanManageExistingFeaturesButCannotAppointRoles() {
    UUID userId = UUID.randomUUID();
    when(repository.activeCodes(userId)).thenReturn(Set.of(RoleCode.ADMIN));

    for (Permission permission : Permission.values()) {
      if (permission == Permission.ROLE_MANAGE) {
        continue;
      }
      assertDoesNotThrow(() -> authorization.require(userId, permission));
    }

    BusinessException exception = assertThrows(
        BusinessException.class, () -> authorization.require(userId, Permission.ROLE_MANAGE));
    assertEquals("PERMISSION_DENIED", exception.code());
  }

  @Test
  void presidentAndAdvisorCanManageRoles() {
    UUID presidentId = UUID.randomUUID();
    UUID advisorId = UUID.randomUUID();
    when(repository.activeCodes(presidentId)).thenReturn(Set.of(RoleCode.PRESIDENT));
    when(repository.activeCodes(advisorId)).thenReturn(Set.of(RoleCode.ADVISOR));

    assertDoesNotThrow(() -> authorization.require(presidentId, Permission.ROLE_MANAGE));
    assertDoesNotThrow(() -> authorization.require(advisorId, Permission.ROLE_MANAGE));
  }

  @Test
  void ordinaryMemberCannotManageSports() {
    UUID userId = UUID.randomUUID();
    when(repository.activeCodes(userId)).thenReturn(Set.of(RoleCode.MEMBER));

    BusinessException exception = assertThrows(
        BusinessException.class, () -> authorization.require(userId, Permission.SPORTS_MANAGE));
    assertEquals("PERMISSION_DENIED", exception.code());
  }
}
