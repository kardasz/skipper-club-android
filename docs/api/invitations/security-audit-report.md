# Invitation System - Security Audit & Improvement Report

**Date:** 2026-01-17
**Updated:** 2026-01-27
**Scope:** Complete analysis of the invitation system implementation
**Status:** MOSTLY IMPLEMENTED

> **Note:** This is an admin-only invitation system - only administrators can send, view, and cancel invitations. Recommendations are scoped accordingly.

---

## Executive Summary

The invitation system has solid security foundations with most critical features now implemented. Attempt tracking, cancel, and list endpoints are all functional. Remaining work focuses on observability, compliance, and hardening.

| Category             | Score | Notes                                                      |
| -------------------- | ----- | ---------------------------------------------------------- |
| Security             | 9/10  | Attempt tracking and CAPTCHA implemented, missing alerting |
| Functionality        | 9/10  | Cancel, list, attempt tracking done. Resend pending        |
| Standards Compliance | 8/10  | Good RFC 7807, missing email compliance details            |
| Observability        | 5/10  | No metrics, limited analytics                              |

---

## 1. Security Analysis

### 1.1 Strengths

| Feature          | Implementation                                            | Assessment                       |
| ---------------- | --------------------------------------------------------- | -------------------------------- |
| Code entropy     | ~40 bits (8 chars from 32-char alphabet)                  | Good for invitation code         |
| Hash storage     | SHA-256 for codes                                         | Industry standard                |
| Rate limiting    | Public: 5/60s                                             | Reasonable                       |
| Timing attacks   | Response delay implemented                                | Good practice                    |
| Attempt tracking | Per-code limit (default 5), incremented on email mismatch | Effective brute-force mitigation |

### 1.2 Issues

#### Issue #1: ~~Missing CAPTCHA for Public Endpoints~~ ✅ RESOLVED

**Risk:** MEDIUM → **RESOLVED**
**Status:** Implemented via Cloudflare Turnstile CAPTCHA guard on `POST /invitations/register`.

**Implementation:**

- `TurnstileGuard` validates `X-Turnstile-Token` header against Cloudflare's `siteverify` API
- Kill switch: when `TURNSTILE_SECRET_KEY` env var is empty/unset, CAPTCHA is completely disabled
- Errors returned in RFC 7807 format: `captcha-token-missing` (403), `captcha-verification-failed` (403), `captcha-service-unavailable` (503)

---

#### Issue #2: No Brute Force Alerting

**Risk:** MEDIUM
**Problem:** Per-code attempt tracking is implemented, but there is no mechanism to alert on suspicious patterns across codes or IPs.

**What's implemented:**

- Per-code attempt limit (5 attempts, configurable via `INVITATION_MAX_ATTEMPTS`)
- Attempt increment on email mismatch
- Blocking after max attempts reached

**What's missing:**

1. Alerting when a single code receives multiple failed attempts
2. Cross-code IP blocking (e.g., temporary block after 10 failures across any codes)
3. Failed attempt logging with full IP context for analysis

---

#### Issue #3: Token/Code Logged in Plaintext

**Location:** Email sending logs invitation URL

**Problem:** The invitation URL contains the code. If logs are compromised, codes are exposed.

**Recommendation:** Ensure logs never contain the full URL or mask the code portion.

---

#### Issue #4: Missing Security Headers Documentation

**Problem:** No documentation about required security headers for the invitation flow.

**Recommendation:** Document and enforce:

- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Strict-Transport-Security`
- `Content-Security-Policy` for email links

---

## 2. Functionality Gaps

### 2.1 Missing Features

| Feature                | Priority | Status  |
| ---------------------- | -------- | ------- |
| Resend invitation      | HIGH     | Pending |
| Reminder before expiry | LOW      | Pending |

#### Feature #1: Resend Invitation

**Use Case:** Recipient didn't receive email or deleted it.

**Proposed Endpoint:**

```
POST /invitations/{id}/resend
Authorization: Bearer <token> (admin only)
```

**Business Rules:**

- Only PENDING invitations can be resent
- Limit: 3 resends per invitation
- Reset expiration on resend
- Include resend count in email ("2nd reminder")

---

### 2.2 Missing Edge Cases

| Scenario                         | Current Behavior | Recommendation    |
| -------------------------------- | ---------------- | ----------------- |
| Inviting previously deleted user | No check         | Warn or block     |
| Unicode emails                   | Unknown          | Test and document |

---

## 3. Compliance Considerations

### 3.1 GDPR (EU)

| Requirement           | Status  | Notes                                            |
| --------------------- | ------- | ------------------------------------------------ |
| Consent for email     | PARTIAL | Inviter consents, invitee implicit               |
| Data retention policy | YES     | 30-day cleanup documented                        |
| Right to erasure      | UNKNOWN | What happens to invitation when inviter deleted? |
| Purpose limitation    | YES     | Clear purpose stated                             |

**Recommendation:** Add clear unsubscribe/opt-out mechanism in invitation email.

### 3.2 CAN-SPAM (US)

| Requirement           | Status                |
| --------------------- | --------------------- |
| Clear identification  | Review email template |
| Unsubscribe mechanism | ADD                   |
| Physical address      | ADD to email footer   |
| No deceptive headers  | OK                    |

---

## 4. Observability & Metrics

### 4.1 Current State

Logging exists but metrics are missing:

```typescript
// Current logging
logger.log({ event: 'invitation.sent', inviterId, emailHash, ip });
logger.log({ event: 'invitation.accepted', userId, invitationId, inviterId });
```

### 4.2 Missing Metrics

| Metric                               | Type      | Purpose               |
| ------------------------------------ | --------- | --------------------- |
| invitations_sent_total               | Counter   | Volume tracking       |
| invitations_accepted_total           | Counter   | Success tracking      |
| invitations_expired_total            | Counter   | Waste identification  |
| invitation_conversion_rate           | Gauge     | KPI                   |
| invitation_time_to_accept_seconds    | Histogram | UX insight            |
| invitation_validation_failures_total | Counter   | Security monitoring   |
| invitation_code_attempts_total       | Counter   | Brute force detection |

---

## 5. Performance Considerations

### 5.1 Database Indexes

Current indexes are well-designed:

- `idx_invitations_code_hash` - O(1) code lookup
- `idx_invitations_email` - Fast duplicate checks
- `idx_invitations_cleanup` - Efficient batch deletion

### 5.2 Potential Bottlenecks

| Area             | Risk   | Mitigation                |
| ---------------- | ------ | ------------------------- |
| Email queue      | Medium | Monitor queue depth       |
| Hash computation | Low    | SHA-256 is fast           |
| Token generation | Low    | Crypto is async           |
| Database writes  | Low    | Single row per invitation |

---

## 6. Testing Gaps

### 6.1 Missing E2E Test Cases

| Test Case                        | Priority |
| -------------------------------- | -------- |
| Rate limit enforcement           | MEDIUM   |
| Unicode email handling           | MEDIUM   |
| Concurrent registration attempts | MEDIUM   |

### 6.2 Security Test Recommendations

1. **Penetration Testing** - Test rate limiting bypass techniques
2. **Fuzz Testing** - Malformed codes, emails
3. **Timing Analysis** - Verify timing-safe comparisons
4. **Concurrent Request Testing** - Race conditions in registration

---

## 7. Recommendations Summary

### 7.1 High Priority

1. **Add unsubscribe mechanism to emails** - CAN-SPAM compliance
2. **Add resend invitation endpoint** - UX improvement
3. **Add brute force alerting** - Cross-code/IP monitoring
4. **Implement basic metrics** - Observability

### 7.2 Medium Priority

1. ~~**Add CAPTCHA for public endpoints**~~ - ✅ Implemented (Cloudflare Turnstile)
2. **Review log sanitization** - No plaintext codes in logs
3. **Document security headers** - Compliance
4. **Add reminder emails** - Conversion boost

### 7.3 Low Priority

1. **Add invitation analytics** - Business insights
2. **Add bulk invite feature** - Enterprise use case

---

## 8. Architecture Recommendations

### 8.1 Event-Driven Improvements

Add domain events for better extensibility:

```typescript
// Events to add
InvitationCreatedEvent; // For analytics, webhooks
InvitationAcceptedEvent; // For notifications
InvitationExpiredEvent; // For cleanup, reminders
InvitationCancelledEvent; // For audit trail
InvitationResendEvent; // For rate limiting, tracking
```

### 8.2 Configuration

Current configuration values (all configurable via environment variables):

| Variable                               | Default                   | Purpose                                                    |
| -------------------------------------- | ------------------------- | ---------------------------------------------------------- |
| `INVITATION_EXPIRATION_DAYS`           | 7                         | Days until invitation expires                              |
| `INVITATION_MAX_ATTEMPTS`              | 5                         | Max registration attempts per code                         |
| `INVITATION_RESPONSE_DELAY_MS`         | 500                       | Minimum response time (timing-attack mitigation)           |
| `INVITATION_CLEANUP_ENABLED`           | true                      | Enable scheduled cleanup job                               |
| `INVITATION_CLEANUP_DAYS_AFTER_EXPIRY` | 30                        | Days after expiry before invitations are deleted           |
| `INVITATION_CLEANUP_BATCH_SIZE`        | 100                       | Batch size for cleanup operations                          |
| `WEBAPP_BASE_URL`                      | `https://skipperclub.app` | Base URL for invitation links                              |
| `TURNSTILE_SECRET_KEY`                 | -                         | Cloudflare Turnstile secret key (empty = CAPTCHA disabled) |

The invitation code format is hardcoded (not configurable): 8 characters from the alphabet `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (no `0`, `O`, `1`, `I`).

Values still worth adding:

```typescript
INVITATION_RESEND_LIMIT = 3; // Per invitation
INVITATION_CAPTCHA_THRESHOLD = 3; // Failures before CAPTCHA
```

---

## 9. Implementation Checklist

### Security Hardening

- [ ] Add failed attempt logging with IP context
- [ ] Review and secure logging (no plaintext codes in URLs)
- [ ] Add unsubscribe link to email template
- [x] Add CAPTCHA for public endpoints (Cloudflare Turnstile)
- [ ] Document security headers

### Core Features

- [ ] Add POST /invitations/{id}/resend endpoint
- [ ] Add resentCount field to entity
- [ ] Add reminder emails before expiry

### Observability

- [ ] Add Prometheus metrics
- [ ] Add structured logging improvements
- [ ] Create Grafana dashboard
- [ ] Set up alerting for anomalies

---

## Appendix: Metrics Implementation

```typescript
// invitations.metrics.ts

import { Injectable } from '@nestjs/common';
import { Counter, Histogram, Gauge } from 'prom-client';

@Injectable()
export class InvitationMetrics {
  readonly sentTotal = new Counter({
    name: 'invitations_sent_total',
    help: 'Total number of invitations sent',
    labelNames: ['status'],
  });

  readonly acceptedTotal = new Counter({
    name: 'invitations_accepted_total',
    help: 'Total number of invitations accepted',
  });

  readonly validationFailures = new Counter({
    name: 'invitation_validation_failures_total',
    help: 'Total number of failed validation attempts',
    labelNames: ['type', 'reason'],
  });

  readonly timeToAccept = new Histogram({
    name: 'invitation_time_to_accept_seconds',
    help: 'Time from invitation sent to accepted',
    buckets: [3600, 86400, 172800, 604800], // 1h, 1d, 2d, 7d
  });

  readonly pendingCount = new Gauge({
    name: 'invitations_pending_total',
    help: 'Current number of pending invitations',
  });
}
```

---

**Report prepared for:** SkipperClub Development Team
**Next review:** After implementing observability recommendations
