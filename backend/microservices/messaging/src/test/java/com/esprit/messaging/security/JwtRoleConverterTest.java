package com.esprit.messaging.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtRoleConverterTest {

    private final JwtRoleConverter converter = new JwtRoleConverter();

    private static Jwt jwt(Map<String, Object> claims) {
        var builder = Jwt.withTokenValue("tok").header("alg", "RS256");
        claims.forEach(builder::claim);
        return builder.build();
    }

    private static List<String> authorities(Collection<GrantedAuthority> gas) {
        return gas.stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void convert_realmAccess_singleRole_uppercasedAndPrefixed() {
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
        List<Object> rolesWithNull = new ArrayList<>();
        rolesWithNull.add("teacher");
        rolesWithNull.add(null);
        Jwt j = jwt(Map.of("realm_access", Map.of("roles", rolesWithNull)));
        assertThat(authorities(converter.convert(j))).containsExactlyInAnyOrder("ROLE_TEACHER");
    }

    @Test
    void convert_resourceAccess_singleClient_singleRole() {
        Jwt j = jwt(Map.of("resource_access", Map.of(
                "my-client", Map.of("roles", List.of("admin")))));
        assertThat(authorities(converter.convert(j))).containsExactlyInAnyOrder("ROLE_ADMIN");
    }

    @Test
    void convert_resourceAccess_multipleClients_deduped() {
        Jwt j = jwt(Map.of("resource_access", Map.of(
                "client-a", Map.of("roles", List.of("student")),
                "client-b", Map.of("roles", List.of("student", "teacher")))));
        assertThat(authorities(converter.convert(j)))
                .containsExactlyInAnyOrder("ROLE_STUDENT", "ROLE_TEACHER");
    }

    @Test
    void convert_realmAndResource_mergedAndDeduped() {
        Jwt j = jwt(Map.of(
                "realm_access", Map.of("roles", List.of("student")),
                "resource_access", Map.of("c", Map.of("roles", List.of("student", "admin")))));
        assertThat(authorities(converter.convert(j)))
                .containsExactlyInAnyOrder("ROLE_STUDENT", "ROLE_ADMIN");
    }

    @Test
    void convert_noRelevantClaims_returnsEmpty() {
        Jwt j = jwt(Map.of("sub", "u1"));
        assertThat(converter.convert(j)).isEmpty();
    }

    @Test
    void convert_emptyRoles_returnsEmpty() {
        Jwt j = jwt(Map.of("realm_access", Map.of("roles", List.of())));
        assertThat(converter.convert(j)).isEmpty();
    }
}
