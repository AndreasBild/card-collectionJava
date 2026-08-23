#!/usr/bin/env bash
set -euo pipefail

echo "🤖 [Jules Environment Setup] Initializing environment for card-collectionJava..."
echo "🔒 [Java 26 Invariant] Target runtime: Java 26 (Do NOT downgrade pom.xml)"

# 1. Display Java and Maven environment
CURRENT_JAVA_VERSION=$(java -version 2>&1 | head -n 1 || echo "Java not found in standard PATH")
echo "Installed Java: ${CURRENT_JAVA_VERSION}"
mvn -version 2>&1 || echo "Maven not found in standard PATH"

# 2. Warm up project dependencies & plugins
echo "📦 Resolving Maven dependencies..."
mvn dependency:go-offline -B || true

# 3. Compile sources and test classpath
echo "🔨 Compiling main and test sources..."
mvn test-compile -B || {
  echo "⚠️ [Jules Note] If compilation fails due to JDK version incompatibilities in this runner,"
  echo "DO NOT downgrade pom.xml compiler release properties to Java 21."
  echo "Java 26 is strictly required and enforced by GitHub Actions CI (.github/workflows/ci.yml)."
  exit 1
}

echo "✅ [Jules Environment Setup] Environment ready for test generation and validation."
