// Smallest possible Go Lambda: talks to the Lambda Runtime API directly with
// the standard library. Used only to test whether the AWS Emulator runs Go.
package main

import (
	"bytes"
	"io"
	"net/http"
	"os"
)

func main() {
	api := "http://" + os.Getenv("AWS_LAMBDA_RUNTIME_API") + "/2018-06-01/runtime/invocation/"
	for {
		resp, err := http.Get(api + "next")
		if err != nil {
			os.Exit(1)
		}
		id := resp.Header.Get("Lambda-Runtime-Aws-Request-Id")
		body, _ := io.ReadAll(resp.Body)
		resp.Body.Close()
		out := []byte(`{"hello":"from go","got":` + string(body) + `}`)
		r, _ := http.Post(api+id+"/response", "application/json", bytes.NewReader(out))
		if r != nil {
			r.Body.Close()
		}
	}
}
