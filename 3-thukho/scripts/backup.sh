#!/usr/bin/env bash
# Sao lưu CSDL ctv_labor (Form 03 VI.3). Chạy hằng ngày bằng cron, ví dụ: 0 22 * * * /đường/dẫn/scripts/backup.sh
# Biến: DB_HOST DB_PORT DB_NAME DB_USER DB_PASS BACKUP_DIR KEEP_DAYS (giữ N ngày, mặc định 30), COPY_TO (thư mục ổ ngoài / đồng bộ đám mây, tùy chọn)
set -euo pipefail
DB_HOST=${DB_HOST:-localhost}; DB_PORT=${DB_PORT:-3306}; DB_NAME=${DB_NAME:-ctv_labor}
DB_USER=${DB_USER:-root}; export MYSQL_PWD=${DB_PASS:-root}
DIR=${BACKUP_DIR:-"$(dirname "$0")/../backups"}; mkdir -p "$DIR"
OUT="$DIR/${DB_NAME}_$(date +%Y%m%d_%H%M%S).sql"
mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" --single-transaction --routines --default-character-set=utf8mb4 "$DB_NAME" > "$OUT"
[ -s "$OUT" ] || { echo "Tệp sao lưu rỗng" >&2; rm -f "$OUT"; exit 1; }
[ -n "${COPY_TO:-}" ] && { mkdir -p "$COPY_TO"; cp "$OUT" "$COPY_TO"/; }
find "$DIR" -name "${DB_NAME}_*.sql" -mtime +"${KEEP_DAYS:-30}" -delete
echo "Đã sao lưu: $OUT"
