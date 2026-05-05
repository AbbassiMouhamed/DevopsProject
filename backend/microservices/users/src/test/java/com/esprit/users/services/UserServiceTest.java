package com.esprit.users.services;

import com.esprit.users.dto.UserSyncDto;
import com.esprit.users.entities.User;
import com.esprit.users.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService service;

    // ─── syncUser() ───────────────────────────────────────────────────────────

    @Test
    void syncUser_nullDto_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.syncUser(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void syncUser_nullKeycloakId_throwsIllegalArgument() {
        UserSyncDto dto = dto(null, "alice");
        assertThatThrownBy(() -> service.syncUser(dto))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void syncUser_nullUsername_throwsIllegalArgument() {
        UserSyncDto dto = dto("kc-1", null);
        assertThatThrownBy(() -> service.syncUser(dto))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void syncUser_newUser_createsAndSaves() {
        UserSyncDto dto = dto("kc-1", "alice");
        dto.setEmail("a@b.com");
        dto.setRole("student");

        when(userRepository.findByKeycloakId("kc-1")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        User result = service.syncUser(dto);

        assertThat(result.getKeycloakId()).isEqualTo("kc-1");
        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getEmail()).isEqualTo("a@b.com");
        assertThat(result.getRole()).isEqualTo("STUDENT");   // normalized
        assertThat(result.isDeleted()).isFalse();
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getCreatedAt()).isNotNull();
    }

    @Test
    void syncUser_existingUser_updatesAndSaves() {
        User existing = user(5L, "kc-2", "bob");
        existing.setRole("STUDENT");

        UserSyncDto dto = dto("kc-2", "bob_updated");
        dto.setRole("ROLE_TEACHER");

        when(userRepository.findByKeycloakId("kc-2")).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = service.syncUser(dto);

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getUsername()).isEqualTo("bob_updated");
        assertThat(result.getRole()).isEqualTo("TEACHER");   // ROLE_ stripped + uppercase
    }

    @Test
    void syncUser_existingUser_doesNotOverrideCreatedAt() {
        User existing = user(3L, "kc-3", "carol");
        java.time.Instant originalCreatedAt = existing.getCreatedAt();

        UserSyncDto dto = dto("kc-3", "carol");
        when(userRepository.findByKeycloakId("kc-3")).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = service.syncUser(dto);

        assertThat(result.getCreatedAt()).isEqualTo(originalCreatedAt);
    }

    @Test
    void syncUser_nullRole_defaultsToStudent() {
        UserSyncDto dto = dto("kc-4", "dave");
        dto.setRole(null);

        when(userRepository.findByKeycloakId(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = service.syncUser(dto);
        assertThat(result.getRole()).isEqualTo("STUDENT");
    }

    @Test
    void syncUser_blankRole_defaultsToStudent() {
        UserSyncDto dto = dto("kc-5", "eve");
        dto.setRole("  ");

        when(userRepository.findByKeycloakId(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = service.syncUser(dto);
        assertThat(result.getRole()).isEqualTo("STUDENT");
    }

    // ─── softDeleteUser() ─────────────────────────────────────────────────────

    @Test
    void softDeleteUser_notFound_throwsIllegalArgument() {
        when(userRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.softDeleteUser(99L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void softDeleteUser_setsDeletedAndDisabled() {
        User u = user(1L, "kc-6", "frank");
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(u));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = service.softDeleteUser(1L);

        assertThat(result.isDeleted()).isTrue();
        assertThat(result.isEnabled()).isFalse();
        verify(userRepository).save(u);
    }

    // ─── updateRole() ─────────────────────────────────────────────────────────

    @Test
    void updateRole_notFound_throwsIllegalArgument() {
        when(userRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRole(10L, "ADMIN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateRole_normalizesAndSaves() {
        User u = user(2L, "kc-7", "grace");
        when(userRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(u));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = service.updateRole(2L, "role_admin");
        assertThat(result.getRole()).isEqualTo("ADMIN");
    }

    // ─── findAll() filters deleted ────────────────────────────────────────────

    @Test
    void findAll_filtersDeletedUsers() {
        User active = user(1L, "kc-a", "active");
        User deleted = user(2L, "kc-b", "deleted");
        deleted.setDeleted(true);

        when(userRepository.findAll()).thenReturn(List.of(active, deleted));

        List<User> result = service.findAll();
        assertThat(result).containsExactly(active);
    }

    // ─── count methods ────────────────────────────────────────────────────────

    @Test
    void countStudents_delegatesToRepository() {
        when(userRepository.countByRoleIgnoreCaseAndDeletedFalse("STUDENT")).thenReturn(5L);
        assertThat(service.countStudents()).isEqualTo(5L);
    }

    @Test
    void countTeachers_delegatesToRepository() {
        when(userRepository.countByRoleIgnoreCaseAndDeletedFalse("TEACHER")).thenReturn(3L);
        assertThat(service.countTeachers()).isEqualTo(3L);
    }

    @Test
    void countUsers_delegatesToRepository() {
        when(userRepository.countByDeletedFalse()).thenReturn(8L);
        assertThat(service.countUsers()).isEqualTo(8L);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static UserSyncDto dto(String keycloakId, String username) {
        UserSyncDto d = new UserSyncDto();
        d.setKeycloakId(keycloakId);
        d.setUsername(username);
        return d;
    }

    private static User user(Long id, String keycloakId, String username) {
        User u = new User();
        u.setId(id);
        u.setKeycloakId(keycloakId);
        u.setUsername(username);
        return u;
    }
}
