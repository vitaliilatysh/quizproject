# MySQL backup and recovery

The container CI job exercises `mysqldump` against the disposable MySQL database
after Flyway migration and API smoke tests. It restores into a **different, empty
MySQL instance**, then compares complete schema/data dumps (including Unicode and
binary test data). Dumps and temporary credentials are never uploaded as artifacts.
This proves the restore procedure in CI, **not production recoverability**.

## Before production

Choose and document an owner, acceptable data loss (RPO), recovery time (RTO),
backup schedule and retention. Enable the database provider's encrypted backups
and point-in-time recovery where available. Keep backups outside the database's
failure domain, restrict access and alert on backup age/failure. Preserve access
to the encryption key and backup credentials separately from the database.

For a logical backup, use a dedicated least-privilege backup account and a MySQL
option file supplied by the secret manager with mode `0600`. Never put passwords
in command-line arguments, Git, shell history or CI artifacts. Use MySQL 8.4 tools
for a MySQL 8.4 database. All application tables must be transactional (InnoDB),
and schema migrations must not run during the dump.

```bash
umask 077
mysqldump --defaults-extra-file=/secure/mysql-backup.cnf \
  --single-transaction --quick --no-tablespaces --set-gtid-purged=OFF \
  --routines --events --triggers --hex-blob tests_db > backup.sql
```

Check exit status and size, record a checksum and the application image digest,
encrypt the dump with an approved tool/key and transfer it to the protected backup
store. Define cleanup of the plaintext temporary file; do not rely on a local disk
copy as the only backup. Binlogs/PITR are needed if the required RPO is shorter
than the backup interval. Provider-managed backups may be preferable to this dump.

## Restore drill and incident recovery

1. Record source backup timestamp/checksum, API digest and Flyway version. Verify
   the checksum and decrypt only on the isolated recovery host.
2. Provision a fresh isolated MySQL instance. **Never restore over the live DB.**
   Configure a restore-account option file that points only to this new host.
3. Create the empty `tests_db` database and restore:

   ```bash
   mysql --defaults-extra-file=/secure/mysql-restore.cnf tests_db < backup.sql
   ```

4. Check every table's row count, foreign-key relationships, Flyway history,
   completed attempt scores and saved question/answer snapshots. Start the matching
   API image against the restored DB and verify readiness, login, catalogue,
   results and a new attempt. Recreate runtime secrets through the secret manager;
   database backups alone do not preserve JWT/Redis credentials.
5. Measure actual data loss and elapsed recovery time against RPO/RTO. Record
   evidence without copying personal data into the report. Repeat after material
   schema/infrastructure changes and on the agreed schedule.
6. For an incident, stop writes and obtain explicit operator approval before
   changing the live DB endpoint. Keep the previous instance until acceptance and
   rollback decisions are complete. Restoring an old backup can resurrect revoked
   sessions: invalidate refresh sessions and rotate JWT signing keys at cutover.

No production schedule, retention policy or live restore is configured by this PR.
Those require the actual production provider, access and recovery objectives.
