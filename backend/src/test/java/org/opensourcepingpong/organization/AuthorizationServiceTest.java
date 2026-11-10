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
    private final UserOrganizationRoleRepository roles = mock(UserOrganizationRoleRepository.class);
    private final AuthorizationService authorization = new AuthorizationService(roles);
    private final UUID user = UUID.randomUUID();

    @Test
    void ordinaryMemberCannotCreateOrEditCompetitions() {
        when(roles.activeCodes(user)).thenReturn(Set.of(RoleCode.MEMBER));

        assertEquals("PERMISSION_DENIED", assertThrows(BusinessException.class,
            () -> authorization.require(user, Permission.COMPETITION_CREATE)).code());
        assertEquals("PERMISSION_DENIED", assertThrows(BusinessException.class,
            () -> authorization.require(user, Permission.COMPETITION_MANAGE)).code());
        assertEquals("PERMISSION_DENIED", assertThrows(BusinessException.class,
            () -> authorization.require(user, Permission.MATCH_SCORE_EDIT)).code());
    }

    @Test
    void ministerCanCreateEditAndScoreCompetitions() {
        when(roles.activeCodes(user)).thenReturn(Set.of(RoleCode.MINISTER));

        for (Permission permission : new Permission[]{
            Permission.COMPETITION_CREATE,
            Permission.COMPETITION_MANAGE,
            Permission.MATCH_SCORE_EDIT,
            Permission.COMPETITION_FINALIZE}) {
            assertDoesNotThrow(() -> authorization.require(user, permission));
        }
    }

    @Test
    void presidentAndAdvisorCanCreateEditAndScoreCompetitions() {
        for (RoleCode role : new RoleCode[]{RoleCode.VICE_PRESIDENT, RoleCode.PRESIDENT, RoleCode.ADVISOR}) {
            when(roles.activeCodes(user)).thenReturn(Set.of(role));
            assertDoesNotThrow(() -> authorization.require(user, Permission.COMPETITION_CREATE));
            assertDoesNotThrow(() -> authorization.require(user, Permission.COMPETITION_MANAGE));
        }
    }

    @Test
    void onlyPresidentOrAdvisorCanManageRoles() {
        for (RoleCode role : new RoleCode[]{
            RoleCode.MEMBER, RoleCode.VICE_MINISTER, RoleCode.MINISTER, RoleCode.VICE_PRESIDENT}) {
            when(roles.activeCodes(user)).thenReturn(Set.of(role));
            assertEquals("PERMISSION_DENIED", assertThrows(BusinessException.class,
                () -> authorization.require(user, Permission.ROLE_MANAGE)).code());
        }
        for (RoleCode role : new RoleCode[]{RoleCode.PRESIDENT, RoleCode.ADVISOR}) {
            when(roles.activeCodes(user)).thenReturn(Set.of(role));
            assertDoesNotThrow(() -> authorization.require(user, Permission.ROLE_MANAGE));
        }
    }
}
