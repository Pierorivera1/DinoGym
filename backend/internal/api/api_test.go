package api

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

// The embedded contract must load and be valid OpenAPI, since task 16 validates
// requests against it.
func TestEmbeddedSpecIsValid(t *testing.T) {
	spec, err := GetSwagger()
	if err != nil {
		t.Fatalf("load embedded spec: %v", err)
	}
	if err := spec.Validate(context.Background()); err != nil {
		t.Fatalf("embedded spec is invalid: %v", err)
	}
}

// stub answers only what the routing test needs.
type stub struct {
	StrictServerInterface
	gotSince int64
}

func (s *stub) GetHealth(context.Context, GetHealthRequestObject) (GetHealthResponseObject, error) {
	return GetHealth200JSONResponse{Status: Ok}, nil
}

func (s *stub) PullChanges(_ context.Context, r PullChangesRequestObject) (PullChangesResponseObject, error) {
	s.gotSince = r.Params.Since
	return PullChanges200JSONResponse{Cursor: r.Params.Since}, nil
}

// The generated server routes paths and parses parameters as the contract says.
func TestGeneratedServerRoutes(t *testing.T) {
	s := &stub{}
	h := HandlerFromMux(NewStrictHandler(s, nil), http.NewServeMux())

	cases := []struct {
		name, path string
		want       int
	}{
		{"health", "/health", http.StatusOK},
		{"pull", "/sync/pull?since=42", http.StatusOK},
		{"pull without since", "/sync/pull", http.StatusBadRequest},
		{"pull with a bad since", "/sync/pull?since=abc", http.StatusBadRequest},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			rec := httptest.NewRecorder()
			h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, c.path, nil))
			if rec.Code != c.want {
				t.Fatalf("GET %s = %d, want %d; body %s", c.path, rec.Code, c.want, rec.Body)
			}
		})
	}
	if s.gotSince != 42 {
		t.Fatalf("since = %d, want 42", s.gotSince)
	}
}

// A deletion travels as a whole row with deletedAt set (decision 51); a live row
// may send deletedAt as null or leave it out.
func TestRowDeletedAtDecoding(t *testing.T) {
	var cs ChangeSet
	body := `{"weighIns":[
		{"id":"00000000-0000-0000-0000-000000000001","measuredAt":1,"weightKg":80.5,"updatedAt":2,"deletedAt":null},
		{"id":"00000000-0000-0000-0000-000000000002","measuredAt":1,"weightKg":80.5,"updatedAt":2},
		{"id":"00000000-0000-0000-0000-000000000003","measuredAt":1,"weightKg":80.5,"updatedAt":2,"deletedAt":3}
	]}`
	if err := json.Unmarshal([]byte(body), &cs); err != nil {
		t.Fatal(err)
	}
	rows := *cs.WeighIns
	if rows[0].DeletedAt != nil || rows[1].DeletedAt != nil {
		t.Fatal("live rows decoded with deletedAt set")
	}
	if rows[2].DeletedAt == nil || *rows[2].DeletedAt != 3 {
		t.Fatal("deleted row lost deletedAt")
	}
}
