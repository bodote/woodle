package io.github.bodote.woodle.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ReferrerPolicyFilter extends OncePerRequestFilter {

    private static final String REFERRER_POLICY_HEADER = "Referrer-Policy";
    private static final String NO_REFERRER = "no-referrer";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader(REFERRER_POLICY_HEADER, NO_REFERRER);
        filterChain.doFilter(request, response);
    }
}
