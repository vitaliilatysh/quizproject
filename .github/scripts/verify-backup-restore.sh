#!/usr/bin/env bash
# CI only: quiz-mysql is the disposable database created by container.yml.
set -euo pipefail
umask 077
BACKUP_FILE="$(mktemp)"
RESTORED_FILE="$(mktemp)"
RESTORE_CONTAINER="quiz-mysql-restore-$$"
CREATED=false
cleanup() {
  if [ "${CREATED}" = true ]; then docker rm -f "${RESTORE_CONTAINER}" >/dev/null; fi
  rm -f -- "${BACKUP_FILE}" "${RESTORED_FILE}"
}
trap cleanup EXIT

# Exercise Unicode and binary data as well as the Flyway/application tables.
docker exec -i quiz-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot tests_db' <<'SQL'
CREATE TABLE backup_restore_probe (id INT PRIMARY KEY, text_value VARCHAR(100), binary_value BLOB);
INSERT INTO backup_restore_probe VALUES (1, 'Відновлення тестів', X'000102FF');
SQL

dump() {
  docker exec "$1" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot \
    --single-transaction --quick --no-tablespaces --set-gtid-purged=OFF \
    --routines --events --triggers --hex-blob --order-by-primary \
    --skip-comments --skip-dump-date --skip-add-locks tests_db'
}
dump quiz-mysql > "${BACKUP_FILE}"
test -s "${BACKUP_FILE}"

export MYSQL_ROOT_PASSWORD
MYSQL_ROOT_PASSWORD="$(openssl rand -hex 32)"
docker run --detach --name "${RESTORE_CONTAINER}" --network quizproject-test \
  --env MYSQL_ROOT_PASSWORD --env MYSQL_DATABASE=tests_db mysql:8.4 >/dev/null
CREATED=true
for attempt in {1..60}; do
  if docker exec "${RESTORE_CONTAINER}" sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --protocol=TCP -h127.0.0.1 -e "SELECT 1"' >/dev/null 2>&1; then
    break
  fi
  if [ "${attempt}" -eq 60 ]; then echo 'Restore database did not become ready.' >&2; exit 1; fi
  sleep 2
done

docker exec -i "${RESTORE_CONTAINER}" sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot tests_db' < "${BACKUP_FILE}"
dump "${RESTORE_CONTAINER}" > "${RESTORED_FILE}"
if ! cmp --silent "${BACKUP_FILE}" "${RESTORED_FILE}"; then
  echo 'Restored schema/data differ from the backup. SQL is not printed or uploaded.' >&2
  exit 1
fi
echo 'Backup/restore verified: a fresh MySQL instance reproduces every dumped table and row.'
