#!/bin/bash
set -e

# Ensure we are executing from the project root
# This script is located in databridge-examples/run.sh, so we go up one level
cd "$(dirname "$0")/.."

# Build the project (skipping tests for speed)
echo "Building project..."
mvn clean install -pl databridge-examples -am -DskipTests

# Run the application
echo "Starting PingPongApp..."
mvn exec:java -pl databridge-examples -Dexec.mainClass="dev.heysulo.databridge.examples.pingpong.PingPongApp"
