#!/bin/sh
GRADLE_OPTS="" exec "$(dirname "$0")/gradle/wrapper/gradlew" "$@"
