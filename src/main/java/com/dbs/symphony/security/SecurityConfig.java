package com.dbs.symphony.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.security.enabled:true}")
    private boolean securityEnabled;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable());

        if (!securityEnabled) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            http.addFilterBefore(new DevUserHeaderFilter(), AnonymousAuthenticationFilter.class);
            http.anonymous(anon -> anon.authorities("workbench.user", "workbench.manager"));
            return http.build();
        }

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/openapi.yaml").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/self").authenticated()
                .requestMatchers(HttpMethod.GET, "/v1/self/managed-groups")
                    .hasAnyAuthority("SCOPE_workbench.manager", "workbench.manager")
                .requestMatchers(HttpMethod.GET, "/v1/groups/*/members")
                    .hasAnyAuthority("SCOPE_workbench.manager", "workbench.manager")
                .requestMatchers(HttpMethod.GET, "/v1/groups/*")
                    .hasAnyAuthority("SCOPE_workbench.user", "workbench.user",
                                     "SCOPE_workbench.manager", "workbench.manager")
                .requestMatchers(HttpMethod.GET, "/v1/operations/*").authenticated()
                .requestMatchers("/v1/projects/*/managed-groups/**")
                    .hasAnyAuthority("SCOPE_workbench.manager", "workbench.manager")
                .requestMatchers("/v1/projects/*/self/**", "/v1/projects/*/workbench/**")
                    .hasAnyAuthority("SCOPE_workbench.user", "workbench.user")
                // Everything else authenticated; method-level controls can be added later
                .anyRequest().authenticated()
        );

        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
