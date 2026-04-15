#!/bin/bash
set -euo pipefail

# Build a flattened ("uber") JAR suitable for AWS Lambda deployment.
# This script takes the Spring Boot fat JAR, unpacks it, and flattens
# all classes and resources into a single JAR that the Lambda runtime
# can load without the Spring Boot launcher.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_DIR="$SCRIPT_DIR/../target"
ORIGINAL_JAR="$JAR_DIR/flowboard-backend-1.0.0.jar"
LAMBDA_JAR="$JAR_DIR/flowboard-backend-1.0.0-lambda.jar"
UNPACK_DIR="$JAR_DIR/lambda-unpacked"
WORK_DIR="$JAR_DIR/lambda-work"

if [[ ! -f "$ORIGINAL_JAR" ]]; then
  echo "ERROR: Original JAR not found at $ORIGINAL_JAR"
  echo "Run 'mvn clean package -DskipTests' first."
  exit 1
fi

echo "Creating Lambda-compatible JAR..."

# Clean up previous runs
rm -rf "$UNPACK_DIR" "$WORK_DIR"
mkdir -p "$UNPACK_DIR" "$WORK_DIR"

# Step 1: Extract original Spring Boot JAR
echo "Step 1: Extracting original JAR..."
unzip -q "$ORIGINAL_JAR" -d "$UNPACK_DIR"

# Step 2: Extract critical META-INF files from spring-boot-autoconfigure JAR
# These files are needed for auto-configuration but live inside BOOT-INF/lib
echo "Step 2: Extracting Spring Boot auto-configuration metadata..."
unzip -qo "$UNPACK_DIR"/BOOT-INF/lib/spring-boot-autoconfigure-*.jar \
  "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports" \
  -d "$UNPACK_DIR" 2>/dev/null || true

# Extract spring.factories from spring-boot-autoconfigure and merge with our custom one.
# Our custom spring.factories is unpacked to $UNPACK_DIR/META-INF/spring.factories
# because the Spring Boot maven plugin places it at the root META-INF in the fat JAR.
AUTOCONFIG_FACTORIES="$UNPACK_DIR/BOOT-INF/lib/spring-boot-autoconfigure-spring.factories"
unzip -p "$UNPACK_DIR"/BOOT-INF/lib/spring-boot-autoconfigure-*.jar \
  "META-INF/spring.factories" > "$AUTOCONFIG_FACTORIES" 2>/dev/null || true

CUSTOM_FACTORIES="$UNPACK_DIR/META-INF/spring.factories"

if [[ -f "$CUSTOM_FACTORIES" && -f "$AUTOCONFIG_FACTORIES" ]]; then
  MERGED_FACTORIES="$UNPACK_DIR/META-INF/spring.factories.merged"
  cat "$AUTOCONFIG_FACTORIES" "$CUSTOM_FACTORIES" > "$MERGED_FACTORIES"
  mv "$MERGED_FACTORIES" "$CUSTOM_FACTORIES"
  echo "Merged spring.factories"
elif [[ -f "$CUSTOM_FACTORIES" ]]; then
  echo "Using custom spring.factories"
elif [[ -f "$AUTOCONFIG_FACTORIES" ]]; then
  mkdir -p "$UNPACK_DIR/META-INF"
  cp "$AUTOCONFIG_FACTORIES" "$CUSTOM_FACTORIES"
  echo "Using autoconfigure spring.factories"
fi

# Step 3: Create the flattened Lambda JAR structure
cd "$WORK_DIR"

# Extract all dependencies to root FIRST
for lib in "$UNPACK_DIR"/BOOT-INF/lib/*.jar; do
  if [[ -f "$lib" ]]; then
    unzip -qo "$lib" -d . || true
  fi
done

# Copy application classes to root AFTER dependencies so our configs win
cp -r "$UNPACK_DIR"/BOOT-INF/classes/* . 2>/dev/null || true

# Copy META-INF (includes extracted auto-config files and our spring.factories)
mkdir -p META-INF
if [[ -d "$UNPACK_DIR/META-INF" ]]; then
  cp -r "$UNPACK_DIR"/META-INF/* META-INF/ 2>/dev/null || true
fi

# Remove signatures and unnecessary files
rm -f META-INF/*.SF META-INF/*.DSA META-INF/*.RSA
rm -rf org/springframework/boot/loader/

# Create the final JAR
echo "Step 3: Packaging flattened JAR..."
rm -f "$LAMBDA_JAR"
jar -cf "$LAMBDA_JAR" .

echo "Lambda JAR created: $LAMBDA_JAR"
ls -lh "$LAMBDA_JAR"
