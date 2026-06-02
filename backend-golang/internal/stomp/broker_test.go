package stomp

import "testing"

func TestMatchDestination(t *testing.T) {
	cases := []struct {
		pattern, dest string
		want          bool
	}{
		{"/topic/planet/*", "/topic/planet/123", true},
		{"/topic/planet/*", "/topic/planet/123/extra", false},
		{"/topic/research/*", "/topic/research/42", true},
		{"/topic/planet/*", "/topic/research/42", false},
		{"/topic/planet/123", "/topic/planet/123", true},
		{"/topic/**", "/topic/planet/123", true},
		{"/topic/planet/**", "/topic/planet/1/2/3", true},
	}
	for _, c := range cases {
		if got := matchDestination(c.pattern, c.dest); got != c.want {
			t.Errorf("matchDestination(%q,%q)=%v want %v", c.pattern, c.dest, got, c.want)
		}
	}
}

func TestParseFrame(t *testing.T) {
	cmd, headers, body := parseFrame("SUBSCRIBE\nid:sub-0\ndestination:/topic/planet/*\n\n")
	if cmd != "SUBSCRIBE" {
		t.Fatalf("cmd=%q", cmd)
	}
	if headers["id"] != "sub-0" || headers["destination"] != "/topic/planet/*" {
		t.Fatalf("headers=%v", headers)
	}
	if body != "" {
		t.Fatalf("body=%q", body)
	}
}
