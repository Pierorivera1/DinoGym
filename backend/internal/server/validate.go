package server

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strings"

	"github.com/getkin/kin-openapi/openapi3"

	"github.com/Pierorivera1/DinoGym/backend/internal/api"
)

// validatingRouter checks every JSON request body against its operation's
// schema in the contract before the generated code decodes it: required
// fields, types, enums, minimums and lengths (row ids are 36 characters).
// Go's JSON decoder alone would turn a missing field into a zero value and
// store it. It hands each route on to next, so on secured routes the bearer
// check still runs first and no 400 reaches a caller without a token.
type validatingRouter struct {
	next    api.ServeMux
	schemas map[string]*openapi3.Schema // "POST /sync/push" → ChangeSet
}

func (v *validatingRouter) HandleFunc(pattern string, h func(http.ResponseWriter, *http.Request)) {
	schema := v.schemas[pattern]
	if schema == nil {
		v.next.HandleFunc(pattern, h)
		return
	}
	v.next.HandleFunc(pattern, func(w http.ResponseWriter, r *http.Request) {
		body, err := io.ReadAll(r.Body)
		var tooBig *http.MaxBytesError
		switch {
		case errors.As(err, &tooBig):
			writeError(w, http.StatusRequestEntityTooLarge, "body_too_large", "request body is over 1 MiB")
			return
		case err != nil:
			writeError(w, http.StatusBadRequest, "bad_request", "could not read the request body")
			return
		}
		var value any
		if err := json.Unmarshal(body, &value); err != nil {
			writeError(w, http.StatusBadRequest, "bad_request", "the request body is not valid JSON")
			return
		}
		if err := schema.VisitJSON(value); err != nil {
			writeError(w, http.StatusBadRequest, "bad_request", describeSchemaError(err))
			return
		}
		r.Body = io.NopCloser(bytes.NewReader(body))
		h(w, r)
	})
}

func (v *validatingRouter) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	v.next.ServeHTTP(w, r)
}

// describeSchemaError names the field and the rule it broke, for example
// "/weighIns/0/id: minimum string length is 36", without the schema dump
// that kin-openapi's Error() adds.
func describeSchemaError(err error) string {
	var se *openapi3.SchemaError
	if errors.As(err, &se) {
		return fmt.Sprintf("/%s: %s", strings.Join(se.JSONPointer(), "/"), se.Reason)
	}
	return "the request body does not match the contract"
}

// requestBodySchemas reads the embedded contract and returns the JSON body
// schema of every operation that has one, by ServeMux pattern.
func requestBodySchemas() map[string]*openapi3.Schema {
	spec, err := api.GetSwagger()
	if err != nil {
		panic(fmt.Sprintf("embedded contract: %v", err))
	}
	out := map[string]*openapi3.Schema{}
	for path, item := range spec.Paths.Map() {
		for method, op := range item.Operations() {
			if op.RequestBody == nil || op.RequestBody.Value == nil {
				continue
			}
			if mt := op.RequestBody.Value.Content.Get("application/json"); mt != nil && mt.Schema != nil && mt.Schema.Value != nil {
				out[strings.ToUpper(method)+" "+path] = mt.Schema.Value
			}
		}
	}
	return out
}
