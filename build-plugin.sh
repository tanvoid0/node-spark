#!/usr/bin/env sh
set -e
echo "Building NodeSpark plugin..."

# Increment patch version in gradle.properties
CURRENT_VERSION=$(grep 'pluginVersion=' gradle.properties | cut -d= -f2)
MAJOR=$(echo "$CURRENT_VERSION" | cut -d. -f1)
MINOR=$(echo "$CURRENT_VERSION" | cut -d. -f2)
PATCH=$(echo "$CURRENT_VERSION" | cut -d. -f3)
NEW_VERSION="$MAJOR.$MINOR.$((PATCH + 1))"
sed -i "s/pluginVersion=$CURRENT_VERSION/pluginVersion=$NEW_VERSION/" gradle.properties
echo "Version: $CURRENT_VERSION > $NEW_VERSION"

# Build JDK comes from org.gradle.java.home in gradle.properties
./gradlew buildPlugin

echo ""
echo "Build successful!"
echo "Plugin zip:"
ls build/distributions/*.zip
echo ""
echo "To install: Settings → Plugins → ⚙ → Install Plugin from Disk"
echo "            Select: $(pwd)/build/distributions/$(ls build/distributions/*.zip | head -1 | xargs basename)"
