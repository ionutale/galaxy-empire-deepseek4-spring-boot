package game

import (
	"strconv"
	"time"
)

func itoa(i int64) string { return strconv.FormatInt(i, 10) }

// today returns the current UTC date (midnight), used as the daily-quest reset
// key — the analogue of Java's LocalDate.now().
func today() *time.Time {
	n := time.Now().UTC()
	d := time.Date(n.Year(), n.Month(), n.Day(), 0, 0, 0, 0, time.UTC)
	return &d
}
