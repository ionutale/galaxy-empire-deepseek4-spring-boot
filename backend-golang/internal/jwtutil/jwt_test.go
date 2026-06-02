package jwtutil

import (
	"testing"

	"github.com/golang-jwt/jwt/v5"
)

func TestRoundTrip(t *testing.T) {
	u := New("default-secret-change-in-production-at-least-256-bits-long", 86400000)
	tok, err := u.GenerateToken(42, "neo")
	if err != nil {
		t.Fatal(err)
	}
	sub, err := u.Subject(tok)
	if err != nil {
		t.Fatal(err)
	}
	if sub != "42" {
		t.Fatalf("subject = %q, want 42", sub)
	}
}

// The default 56-byte secret must select HS384, matching jjwt's
// hmacShaKeyFor selection, so tokens are interchangeable with the Java pair.
func TestDefaultSecretUsesHS384(t *testing.T) {
	u := New("default-secret-change-in-production-at-least-256-bits-long", 1000)
	if u.method != jwt.SigningMethodHS384 {
		t.Fatalf("method = %v, want HS384", u.method.Alg())
	}
}

func TestRejectsTamperedToken(t *testing.T) {
	u := New("default-secret-change-in-production-at-least-256-bits-long", 1000)
	if _, err := u.Subject("not.a.jwt"); err == nil {
		t.Fatal("expected error for malformed token")
	}
}
