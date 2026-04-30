#!/bin/zsh

set -euo pipefail

cd "$(dirname "$0")"

rm -rf out
mkdir -p out
find src -name '*.java' -print0 | xargs -0 javac -d out
java -cp out game.Main
