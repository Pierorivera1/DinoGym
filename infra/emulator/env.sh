# Source this file to point the AWS CLI and SDKs in the current shell at the
# local AWS Emulator:  source infra/emulator/env.sh
# Open a new shell to go back to normal. Never source it in a shell that
# should reach real AWS.
_port=9297
_dir="$HOME/.aws/emulator/$_port"

# Real credentials in the environment beat the credentials file, and the
# emulator rejects them. Drop them in this shell.
unset AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN AWS_PROFILE

export AWS_ENDPOINT_URL="https://127.0.0.1:$_port"
export AWS_SHARED_CREDENTIALS_FILE="$_dir/emulator_credentials"
export AWS_CONFIG_FILE="$_dir/config"
export AWS_CA_BUNDLE="$_dir/ca.pem"
export AWS_EC2_METADATA_DISABLED=true
export AWS_DISABLE_HOST_PREFIX_INJECTION=true
export AWS_REGION=us-east-1
export AWS_DEFAULT_REGION=us-east-1
unset _port _dir
