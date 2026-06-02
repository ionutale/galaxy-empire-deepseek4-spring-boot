// Package database connects to Postgres via pgx and runs the embedded Flyway
// migrations idempotently.
package database

import (
	"context"
	"fmt"
	"io/fs"
	"os"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
)

// pgx decodes timestamptz into time.Local; pinning the process timezone to UTC
// makes every DB timestamp marshal to JSON as "...Z", identical to the Java
// services' Instant serialization. Safe here: every stored time is computed via
// time.Now().UTC().
func init() { time.Local = time.UTC }

// Connect builds a pool from the DB_* environment variables (the same ones the
// Java services read) and verifies connectivity, retrying briefly so it can
// start alongside Postgres in docker-compose.
func Connect(ctx context.Context) (*pgxpool.Pool, error) {
	host := env("DB_HOST", "localhost")
	port := env("DB_PORT", "5432")
	name := env("DB_NAME", "postgres")
	user := env("DB_USER", "postgres")
	pass := env("DB_PASSWORD", "postgres")

	dsn := fmt.Sprintf("postgres://%s:%s@%s:%s/%s?sslmode=disable&pool_max_conns=10",
		user, pass, host, port, name)

	cfg, err := pgxpool.ParseConfig(dsn)
	if err != nil {
		return nil, err
	}
	// Return timestamptz values in UTC so they marshal to JSON as "...Z",
	// matching the Java services' Instant serialization.
	cfg.ConnConfig.RuntimeParams["timezone"] = "UTC"

	var pool *pgxpool.Pool
	for attempt := 1; attempt <= 30; attempt++ {
		pool, err = pgxpool.NewWithConfig(ctx, cfg)
		if err == nil {
			if pingErr := pool.Ping(ctx); pingErr == nil {
				return pool, nil
			} else {
				err = pingErr
				pool.Close()
			}
		}
		time.Sleep(2 * time.Second)
	}
	return nil, fmt.Errorf("could not connect to database: %w", err)
}

func env(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

// Migrate runs the embedded migrations. If sentinelTable already exists (the
// schema was provisioned by the Java/Flyway service) the migrations are marked
// applied without running, so the Go services drop cleanly onto an existing
// database. On a fresh database the migrations run in version order, each in
// its own transaction, tracked in go_schema_migrations.
func Migrate(ctx context.Context, pool *pgxpool.Pool, migrations fs.FS, sentinelTable string) error {
	if _, err := pool.Exec(ctx, `CREATE TABLE IF NOT EXISTS go_schema_migrations (
		version TEXT PRIMARY KEY,
		applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
	)`); err != nil {
		return fmt.Errorf("create migrations table: %w", err)
	}

	files, err := orderedMigrations(migrations)
	if err != nil {
		return err
	}

	applied := map[string]bool{}
	rows, err := pool.Query(ctx, `SELECT version FROM go_schema_migrations`)
	if err != nil {
		return err
	}
	for rows.Next() {
		var v string
		if err := rows.Scan(&v); err != nil {
			rows.Close()
			return err
		}
		applied[v] = true
	}
	rows.Close()

	// Detect an externally-provisioned (Flyway) schema.
	if len(applied) == 0 && sentinelTable != "" {
		var exists bool
		if err := pool.QueryRow(ctx, `SELECT to_regclass($1) IS NOT NULL`, sentinelTable).Scan(&exists); err != nil {
			return err
		}
		if exists {
			for _, f := range files {
				if _, err := pool.Exec(ctx,
					`INSERT INTO go_schema_migrations(version) VALUES($1) ON CONFLICT DO NOTHING`, f.version); err != nil {
					return err
				}
			}
			return nil
		}
	}

	for _, f := range files {
		if applied[f.version] {
			continue
		}
		sql, err := fs.ReadFile(migrations, f.path)
		if err != nil {
			return err
		}
		conn, err := pool.Acquire(ctx)
		if err != nil {
			return err
		}
		// Use the simple query protocol so multi-statement migration files run.
		_, err = conn.Conn().PgConn().Exec(ctx, string(sql)).ReadAll()
		if err != nil {
			conn.Release()
			return fmt.Errorf("migration %s failed: %w", f.version, err)
		}
		_, err = conn.Exec(ctx, `INSERT INTO go_schema_migrations(version) VALUES($1)`, f.version)
		conn.Release()
		if err != nil {
			return err
		}
	}
	return nil
}

type migration struct {
	version string
	path    string
	num     int
}

func orderedMigrations(migrations fs.FS) ([]migration, error) {
	var out []migration
	err := fs.WalkDir(migrations, ".", func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.IsDir() || !strings.HasSuffix(path, ".sql") {
			return nil
		}
		base := path[strings.LastIndex(path, "/")+1:]
		version := base
		num := 0
		if strings.HasPrefix(base, "V") {
			if idx := strings.Index(base, "__"); idx > 1 {
				version = base[1:idx]
				num, _ = strconv.Atoi(version)
			}
		}
		out = append(out, migration{version: version, path: path, num: num})
		return nil
	})
	if err != nil {
		return nil, err
	}
	sort.Slice(out, func(i, j int) bool { return out[i].num < out[j].num })
	return out, nil
}
