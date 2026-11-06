#!/bin/sh

PORT="7777"

islive () {
    health=$(/usr/bin/wget -O - -q http://localhost:${PORT}/actuator/health \
        | tr -d '\n' \
        | tr -d ' ' \
        | sed 's/.*"status":"\([^"]*\)"}/\1/')

    if [ "${health}"x = "UP"x ]; then
        echo "health success"
        exit 0
    else
        echo "health err [status=${health}]"
        exit 1
    fi
}

islive