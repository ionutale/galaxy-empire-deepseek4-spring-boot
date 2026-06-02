// Package jwtutil produces and validates JWTs that are wire-compatible with the
// Java services. The Java code uses io.jsonwebtoken (jjwt) with
// Keys.hmacShaKeyFor(secret.getBytes()), which selects HS256/HS384/HS512 based
// on the key length. We replicate that selection so a token minted by the Go
// auth-service is accepted by the Go gateway exactly as the Java pair would be.
package jwtutil

import (
	"errors"
	"strconv"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// keyBytes mirrors the gateway's behaviour of padding a short secret to 32
// bytes. With the default 56-byte secret no padding occurs (matching the Java
// auth-service which never pads).
func keyBytes(secret string) []byte {
	b := []byte(secret)
	if len(b) < 32 {
		padded := make([]byte, 32)
		copy(padded, b)
		return padded
	}
	return b
}

// signingMethod replicates jjwt's SignatureAlgorithm selection by key length.
func signingMethod(key []byte) jwt.SigningMethod {
	switch {
	case len(key) >= 64:
		return jwt.SigningMethodHS512
	case len(key) >= 48:
		return jwt.SigningMethodHS384
	default:
		return jwt.SigningMethodHS256
	}
}

// Util generates and validates tokens for a given secret.
type Util struct {
	key          []byte
	method       jwt.SigningMethod
	expirationMs int64
}

func New(secret string, expirationMs int64) *Util {
	k := keyBytes(secret)
	return &Util{key: k, method: signingMethod(k), expirationMs: expirationMs}
}

// GenerateToken mirrors AuthService/JwtUtil: subject = playerId, plus a
// "username" claim, issuedAt and expiration.
func (u *Util) GenerateToken(playerID int64, username string) (string, error) {
	now := time.Now()
	claims := jwt.MapClaims{
		"sub":      strconv.FormatInt(playerID, 10),
		"username": username,
		"iat":      now.Unix(),
		"exp":      now.Add(time.Duration(u.expirationMs) * time.Millisecond).Unix(),
	}
	return jwt.NewWithClaims(u.method, claims).SignedString(u.key)
}

// Subject validates the token and returns its subject (the player id) — used by
// the gateway's auth filter.
func (u *Util) Subject(token string) (string, error) {
	claims := jwt.MapClaims{}
	_, err := jwt.ParseWithClaims(token, claims, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, errors.New("unexpected signing method")
		}
		return u.key, nil
	})
	if err != nil {
		return "", err
	}
	sub, err := claims.GetSubject()
	if err != nil || sub == "" {
		return "", errors.New("missing subject")
	}
	return sub, nil
}
