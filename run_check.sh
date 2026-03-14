#!/bin/bash
./gradlew app:dependencies --configuration debugRuntimeClasspath --console=plain > deps_check.txt 2>&1
grep "com.google.mlkit:barcode-scanning" deps_check.txt > result.txt

