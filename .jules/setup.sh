#!/usr/bin/env bash
set -euo pipefail

echo "🤖 [Jules Environment Setup] Initializing environment for card-collectionJava..."

# 1. Display Java and Maven environment
java -version 2>&1 || echo "Java not found in standard PATH"
mvn -version 2>&1 || echo "Maven not found in standard PATH"

# 2. Warm up project dependencies & plugins
echo "📦 Resolving Maven dependencies..."
mvn dependency:go-offline -B || true

# 3. Compile sources and test classpath to verify Java 26 preview support
echo "🔨 Compiling main and test sources..."
mvn test-compile -B

echo "✅ [Jules Environment Setup] Environment ready for test generation and validation."
