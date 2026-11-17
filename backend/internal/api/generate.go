// Package api holds the Go types and server interfaces generated from the
// OpenAPI contract in api/openapi.yaml (decision 60). Do not edit api.gen.go;
// change the contract and run `go generate ./...` from backend/.
package api

//go:generate go tool oapi-codegen -config oapi-codegen.yaml ../../../api/openapi.yaml
