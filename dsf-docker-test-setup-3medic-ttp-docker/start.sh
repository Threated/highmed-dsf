#!/bin/bash -e

export PROXY1_ID=${PROXY1_ID:-proxy1.broker}
export PROXY2_ID=${PROXY2_ID:-proxy2.broker}
export PROXY1_ID_SHORT=$(echo $PROXY1_ID | cut -d '.' -f 1)
export PROXY2_ID_SHORT=$(echo $PROXY2_ID | cut -d '.' -f 1)
export BROKER_ID=$(echo $PROXY1_ID | cut -d '.' -f 2-)
export BROKER_URL=http://broker:8080
export APP1_ID_SHORT=app1
export APP2_ID_SHORT=app2
export APP1_P1=${APP1_ID_SHORT}.$PROXY1_ID
export APP2_P1=${APP2_ID_SHORT}.$PROXY1_ID
export APP1_P2=${APP1_ID_SHORT}.$PROXY2_ID
export APP2_P2=${APP2_ID_SHORT}.$PROXY2_ID
export APP_KEY=App1Secret
export RUST_LOG=${RUST_LOG:-info}

export P1="http://localhost:8081" # for scripts
export P2="http://localhost:8082" # for scripts

export VAULT_TOKEN=$(echo $RANDOM | md5sum | head -c 20; echo;)

export ARCH=$(docker version --format "{{.Server.Arch}}")
export TAG=${TAG:-develop}

function start {
    rm -fv pki/*.pem pki/*.json pki/pki.secret
    pki/pki devsetup
    echo "$VAULT_TOKEN" > ./pki/pki.secret
    docker compose up
    # rm -fv pki/*.pem pki/*.json pki/pki.secret
    # pki/pki clean
}

start
