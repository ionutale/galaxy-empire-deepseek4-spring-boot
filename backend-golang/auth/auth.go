// Package auth is the Go port of the Spring auth-service: player registration
// and login, BCrypt password hashing (wire-compatible with Spring Security's
// BCryptPasswordEncoder) and JWT issuance.
package auth

import (
	"context"
	"embed"
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"galaxyempire/internal/database"
	"galaxyempire/internal/jwtutil"
	"galaxyempire/internal/web"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"golang.org/x/crypto/bcrypt"
)

//go:embed migrations/*.sql
var Migrations embed.FS

// ---- domain ----

type Player struct {
	ID           int64
	Username     string
	PasswordHash string
}

// ---- repository ----

type Repository struct{ pool *pgxpool.Pool }

func NewRepository(pool *pgxpool.Pool) *Repository { return &Repository{pool: pool} }

func (r *Repository) FindByUsername(ctx context.Context, username string) (*Player, error) {
	var p Player
	err := r.pool.QueryRow(ctx,
		`SELECT id, username, password_hash FROM player WHERE username = $1`, username).
		Scan(&p.ID, &p.Username, &p.PasswordHash)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &p, nil
}

func (r *Repository) ExistsByUsername(ctx context.Context, username string) (bool, error) {
	var exists bool
	err := r.pool.QueryRow(ctx,
		`SELECT EXISTS(SELECT 1 FROM player WHERE username = $1)`, username).Scan(&exists)
	return exists, err
}

func (r *Repository) Save(ctx context.Context, username, passwordHash string) (*Player, error) {
	var id int64
	err := r.pool.QueryRow(ctx,
		`INSERT INTO player (username, password_hash, dark_matter) VALUES ($1, $2, 0) RETURNING id`,
		username, passwordHash).Scan(&id)
	if err != nil {
		return nil, err
	}
	return &Player{ID: id, Username: username, PasswordHash: passwordHash}, nil
}

// ---- service ----

type AuthResponse struct {
	Token    string `json:"token"`
	PlayerID int64  `json:"playerId"`
	Username string `json:"username"`
}

type Service struct {
	repo *Repository
	jwt  *jwtutil.Util
}

func NewService(repo *Repository, jwt *jwtutil.Util) *Service {
	return &Service{repo: repo, jwt: jwt}
}

func (s *Service) Register(ctx context.Context, username, password string) (*AuthResponse, error) {
	if strings.TrimSpace(username) == "" {
		return nil, errors.New("Username is required")
	}
	if strings.TrimSpace(password) == "" {
		return nil, errors.New("Password is required")
	}
	exists, err := s.repo.ExistsByUsername(ctx, username)
	if err != nil {
		return nil, err
	}
	if exists {
		return nil, errors.New("Username already taken")
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return nil, err
	}
	player, err := s.repo.Save(ctx, username, string(hash))
	if err != nil {
		return nil, err
	}
	return s.tokenResponse(player)
}

func (s *Service) Login(ctx context.Context, username, password string) (*AuthResponse, error) {
	if strings.TrimSpace(username) == "" {
		return nil, errors.New("Username is required")
	}
	if strings.TrimSpace(password) == "" {
		return nil, errors.New("Password is required")
	}
	player, err := s.repo.FindByUsername(ctx, username)
	if err != nil {
		return nil, err
	}
	if player == nil {
		return nil, errors.New("Invalid username or password")
	}
	if bcrypt.CompareHashAndPassword([]byte(player.PasswordHash), []byte(password)) != nil {
		return nil, errors.New("Invalid username or password")
	}
	return s.tokenResponse(player)
}

func (s *Service) tokenResponse(p *Player) (*AuthResponse, error) {
	token, err := s.jwt.GenerateToken(p.ID, p.Username)
	if err != nil {
		return nil, err
	}
	return &AuthResponse{Token: token, PlayerID: p.ID, Username: p.Username}, nil
}

// ---- handlers ----

type loginRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

// RegisterRoutes wires the /api/auth endpoints onto mux.
func (s *Service) RegisterRoutes(mux *http.ServeMux) {
	mux.HandleFunc("POST /api/auth/register", func(w http.ResponseWriter, r *http.Request) {
		s.handle(w, r, s.Register, http.StatusBadRequest)
	})
	mux.HandleFunc("POST /api/auth/login", func(w http.ResponseWriter, r *http.Request) {
		s.handle(w, r, s.Login, http.StatusUnauthorized)
	})
}

func (s *Service) handle(w http.ResponseWriter, r *http.Request,
	fn func(context.Context, string, string) (*AuthResponse, error), errStatus int) {
	var req loginRequest
	if err := decodeJSON(r, &req); err != nil {
		web.Error(w, http.StatusBadRequest, "Invalid request body")
		return
	}
	resp, err := fn(r.Context(), req.Username, req.Password)
	if err != nil {
		web.Error(w, errStatus, err.Error())
		return
	}
	web.JSON(w, http.StatusOK, resp)
}

func decodeJSON(r *http.Request, v any) error {
	return json.NewDecoder(r.Body).Decode(v)
}

// MigrateDB runs the auth migrations.
func MigrateDB(ctx context.Context, pool *pgxpool.Pool) error {
	return database.Migrate(ctx, pool, Migrations, "player")
}
