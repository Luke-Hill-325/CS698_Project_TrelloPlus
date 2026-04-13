#!/bin/bash
set -e

echo "Creating Lambda-compatible JAR..."

JAR_DIR="$(pwd)/target"
ORIGINAL_JAR="$JAR_DIR/flowboard-backend-1.0.0.jar"
LAMBDA_JAR="$JAR_DIR/flowboard-backend-1.0.0-lambda.jar"
UNPACK_DIR="$JAR_DIR/lambda-unpacked"
WORK_DIR="$JAR_DIR/lambda-work"

# Clean up previous runs
rm -rf "$UNPACK_DIR" "$WORK_DIR"
mkdir -p "$UNPACK_DIR" "$WORK_DIR"

# Step 1: Extract original Spring Boot JAR
echo "Step 1: Extracting original JAR..."
unzip -q "$ORIGINAL_JAR" -d "$UNPACK_DIR"

# Step 2: Extract critical META-INF files from spring-boot-autoconfigure JAR
# These files are needed for auto-configuration but are in BOOT-INF/lib
echo "Step 2: Extracting Spring Boot auto-configuration files..."
unzip -qo "$UNPACK_DIR/BOOT-INF/lib/spring-boot-autoconfigure-"*.jar \
    "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports" \
    -d "$UNPACK_DIR" 2>/dev/null || true

unzip -qo "$UNPACK_DIR/BOOT-INF/lib/spring-boot-autoconfigure-"*.jar \
    "META-INF/spring.factories" \
    -d "$UNPACK_DIR" 2>/dev/null || true

# Step 3: Create the Lambda JAR structure
cd "$WORK_DIR"

# Create root with application classes
cp -r "$UNPACK_DIR/BOOT-INF/classes/"* . 2>/dev/null || true

# Extract all dependencies to root
for lib in "$UNPACK_DIR/BOOT-INF/lib/"*.jar; do
    if [ -f "$lib" ]; then
        unzip -qo "$lib" -d . || true
    fi
done

# Copy META-INF from unpack dir (now includes extracted auto-config files)
mkdir -p META-INF
if [ -d "$UNPACK_DIR/META-INF" ]; then
    cp -r "$UNPACK_DIR/META-INF/"* META-INF/ 2>/dev/null || true
fi

# Remove signatures and unnecessary files
rm -f META-INF/*.SF META-INF/*.DSA META-INF/*.RSA
rm -rf org/springframework/boot/loader/

# Create the final JAR
echo "Creating Lambda JAR..."
rm -f "$LAMBDA_JAR"
jar -cf "$LAMBDA_JAR" .

echo "Lambda JAR created: $LAMBDA_JAR"
ls -lh "$LAMBDA_JAR"
