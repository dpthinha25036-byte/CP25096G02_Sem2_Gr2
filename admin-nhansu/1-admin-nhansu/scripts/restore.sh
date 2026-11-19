#!/usr/bin/env bash
# Phục hồi CSDL: ./restore.sh <tệp.sql>. Tự sao lưu bản hiện tại trước khi ghi đè.
set -euo pipefail
[ $# -eq 1 ] && [ -f "$1" ] || { echo "Dùng: $0 <tệp_sao_lưu.sql>" >&2; exit 1; }
DB_HOST=${DB_HOST:-localhost}; DB_PORT=${DB_PORT:-3306}; DB_NAME=${DB_NAME:-ctv_labor}
DB_USER=${DB_USER:-root}; export MYSQL_PWD=${DB_PASS:-root}
"$(dirname "$0")/backup.sh"
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" --default-character-set=utf8mb4 "$DB_NAME" < "$1"
echo "Đã phục hồi từ $1"
