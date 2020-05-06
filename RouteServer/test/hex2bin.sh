#!/bin/sh
sed 's/\([0-9A-F]\{2\}\)/\\\\\\x\1/gI' "$1" | xargs printf
