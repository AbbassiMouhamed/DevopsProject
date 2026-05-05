package com.esprit.users;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtRoleConverterTest {

    private final JwtRoleConverter converter = new JwtRoleConverter();

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static Jwt jwt(Map<String, Object> claims) {
        var builder = Jwt.withTokenValue("tok")
                .header("alg", "RS256");
        claims.forEach(builder::claim);
        return builder.build();
    }

    private static List<String> authorities(Collection<GrantedAuthority> gas) {
        return gas.stream().map(GrantedAuthority::getAuthority).toList();
    }

    // ─── realm_access ─────────────────────────────────────────────────────────

    @Test
    void convert_realmAccess_singleRole_prefixedAndUppercase() {
        Jwt j = jwt(Map.of("realm_access", Map.of("roles", List.of("student"))));
        assertThat(authorities(converter.convert(j))).containsExactlyInAnyOrder("ROLE_STUDENT");
    }

    @Test
    void convert_realmAccess_roleWithRolePrefix_strippedOnce() {
        Jwt j = jwt(Map.of("realm_access", Map.of("roles", List.of("ROLE_TEACHER"))));
        assertThat(authorities(converter.convert(j))).containsExactlyInAnyOrder("ROLE_TEACHER");
    }

    @Test
    void convert_realmAccess_multipleRoles() {
        Jwt j = jwt(Map.of("realm_access", Map.of("roles", List.of("student", "admin"))));
        assertThat(authorities(converter.convert(j)))
                .containsExactlyInAnyOrder("ROLE_STUDENT", "ROLE_ADMIN");
    }

    @Test
    void convert_realmAccess_nullRoleFiltered() {
        List<Object> rolesWithNull = new java.util.ArrayList<>();
        rolesWithNull.add("teacher");
        rolesWithNull.add(null);
        Jwt j = jwt(Map.of("realm_access", Map.of("roles", rolesWithNull)));
        assertThat(authorities(converter.convert(j))).containsExactlyInAnyOrder("ROLE_TEACHER");
    }

    // ─── resource_access ──────────────────────────────────────────────────────

    @Test
    void convert_resourceAccess_singleClient_singleRole() {
        Jwt j = jwt(Map.of("resource_access", Map.of(
                "my-client", Map.of("roles", List.of("admin")))));
        assertThat(authorities(converter.convert(j))).containsExactlyInAnyOrder("ROLE_ADMIN");
    }

    @Test
    void convert_resourceAccess_multipleClients_rolesDeduped() {
        Jwt j = jwt(Map.of("resource_access", Map.of(
                "client-a", Map.of("roles", List.of("student")),
                "client-b", Map.of("roles", List.of("student", "teacher")))));
        assertThat(authorities(converter.convert(j)))
                .containsExactlyInAnyOrder("ROLE_STUDENT", "ROLE_TEACHER");
    }

    // ─── combined realm + resource ────────────────────────────────────────────

    @Test
    void convert_realmAndResourceAccess_rolesMergedAndDeduped() {
        Jwt j = jwt(Map.of(
                "realm_access", Map.of("roles", List.of("student")),
                "resource_access", Map.of("client", Map.of("roles", List.of("student", "admin")))));
        assertThat(authorities(converter.convert(j)))
                .containsExactlyInAnyOrder("ROLE_STUDENT", "ROLE_ADMIN");
    }

    // ─── empty / missing claims ────────────────────────────────────────────────

    @Test
    void convert_noRelevantClaims_returnsEmpty() {
        Jwt j = jwt(Map.of("sub", "user1"));
        assertThat(converter.convert(j)).isEmpty();
    }

    @Test
    void convert_realmAccess_emptyRoles_returnsEmpty() {
        Jwt j = jwt(Map.of("realm_access", Map.of("roles", List.of())));
        assertThat(converter.convert(j)).isEmpty();
    }

    @Test
    void convert_realmAccess_whitespaceRole_filteredOut() {
        Jwt j = jwt(Map.of("realm_access", Map.of("roles", List.of("   "))));
        assertThat(converter.convert(j)).isEmpty();
    }
}
