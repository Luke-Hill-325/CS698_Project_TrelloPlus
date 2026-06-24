# Red Team Findings

## Finding: Silent Security Perimeter in Authentication Flow

### Weakness
`AuthController`, `AuthService`, and `JWTAuthenticationFilter` currently have no logging.

Every other service layer has `@Slf4j` plus meaningful log statements, but the entire security perimeter is effectively silent. In production, this means:

- Failed logins are never recorded.
- JWT validation failures leave no trace.
- Brute-force attacks are undetectable.
- No audit trail exists for authentication-protected API endpoint access.

### How To Fix
- Add `@Slf4j` to `AuthService` and log `warn` on failed login attempts.
- Log JWT parse and validation exceptions in `JWTAuthenticationFilter`.
- Enable `CommonsRequestLoggingFilter` for HTTP-level audit logging.

## Co-Author
Co-authored-by: Saanvi Elaty <elatysaanvi@gmail.com>
