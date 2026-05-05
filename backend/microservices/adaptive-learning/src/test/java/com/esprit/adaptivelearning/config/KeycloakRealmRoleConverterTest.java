package com.esprit.adaptivelearning.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class KeycloakRealmRoleConverterTest {

    private KeycloakRealmRoleConverter converter;

    @BeforeEach
    void setUp() {
        converter = new KeycloakRealmRoleConverter();
    }

    // ---- realm_access roles ----

    @Test
    void convert_realmAccessRoles_addsRoleAuthorities() {
        Jwt jwt = buildJwt(
                Map.of("realm_access", Map.of("roles", List.of("teacher"))),
                Map.of()
        );

        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        Set<String> names = authorityNames(authorities);

        assertTrue(names.contains("ROLE_teacher"), "should contain ROLE_teacher");
        assertTrue(names.contains("teacher"), "should contain plain teacher");
    }

    @Test
    void convert_studentRealmRole_addsAllStudentAliases() {
        Jwt jwt = buildJwt(
                Map.of("realm_access", Map.of("roles", List.of("student"))),
                Map.of()
        );

        Set<String> names = authorityNames(converter.convert(jwt));

        assertTrue(names.contains("ROLE_STUDENT"));
        assertTrue(names.contains("student"));
        assertTrue(names.contains("ROLE_student"));
    }

    @Test
    void convert_adminRealmRole_addsAllAdminAliases() {
        Jwt jwt = buildJwt(
                Map.of("realm_access", Map.of("roles", List.of("admin"))),
                Map.of()
        );

        Set<String> names = authorityNames(converter.convert(jwt));

        assertTrue(names.contains("ROLE_ADMIN"));
        assertTrue(names.contains("admin"));
        assertTrue(names.contains("ROLE_admin"));
    }

    @Test
    void convert_teacherRealmRole_addsAllTeacherAliases() {
        Jwt jwt = buildJwt(
                Map.of("realm_access", Map.of("roles", List.of("teacher"))),
                Map.of()
        );

        Set<String> names = authorityNames(converter.convert(jwt));

        assertTrue(names.contains("ROLE_TEACHER"));
        assertTrue(names.contains("teacher"));
        assertTrue(names.contains("ROLE_teacher"));
    }

    // ---- resource_access (client) roles ----

    @Test
    void convert_resourceAccessRoles_addsClientRoles() {
        Map<String, Object> claims = Map.of(
                "resource_access", Map.of("my-client", Map.of("roles", List.of("student")))
        );
        Jwt jwt = buildJwt(claims, Map.of());

        Set<String> names = authorityNames(converter.convert(jwt));

        assertTrue(names.contains("ROLE_student"));
        assertTrue(names.contains("student"));
    }

    // ---- groups claim ----

    @Test
    void convert_groupsClaim_addsGroupAuthorities() {
        Map<String, Object> claims = Map.of("groups", List.of("etudiant"));
        Jwt jwt = buildJwt(claims, Map.of());

        Set<String> names = authorityNames(converter.convert(jwt));

        // "etudiant" normalises to student aliases
        assertTrue(names.contains("ROLE_STUDENT"));
        assertTrue(names.contains("student"));
    }

    @Test
    void convert_groupsClaimEnseignant_addsTeacherAliases() {
        Map<String, Object> claims = Map.of("groups", List.of("enseignant"));
        Jwt jwt = buildJwt(claims, Map.of());

        Set<String> names = authorityNames(converter.convert(jwt));

        assertTrue(names.contains("ROLE_TEACHER"));
        assertTrue(names.contains("teacher"));
    }

    // ---- empty / missing claims ----

    @Test
    void convert_noRelevantClaims_returnsEmptyCollection() {
        Jwt jwt = buildJwt(Map.of(), Map.of());

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertNotNull(authorities);
        assertTrue(authorities.isEmpty());
    }

    @Test
    void convert_nullRealmAccess_doesNotThrow() {
        // realm_access claim not present
        Jwt jwt = buildJwt(Map.of(), Map.of());
        assertDoesNotThrow(() -> converter.convert(jwt));
    }

    // ---- helpers ----

    private Set<String> authorityNames(Collection<GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    /**
     * Builds a minimal Jwt with the given extra claims merged together.
     */
    private Jwt buildJwt(Map<String, Object> claims1, Map<String, Object> claims2) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));

        claims1.forEach(builder::claim);
        claims2.forEach(builder::claim);

        return builder.build();
    }
}
