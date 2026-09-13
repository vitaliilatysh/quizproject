/**
 * Administration of the catalogue, its questions, its users and its results.
 *
 * <h2>Why these are four services and not one</h2>
 *
 * <p>They were one, and it held nine repositories and sixteen public methods
 * across five things that have nothing to do with each other: reference data,
 * quizzes, questions, accounts, reporting. Nothing linked them except the URL
 * prefix their endpoints share. A reader asking what happens when a quiz is
 * deleted had to find it among account blocking and result ranges, and every
 * one of the nine repositories was in reach of every one of the sixteen
 * methods whether it belonged there or not.
 *
 * <p>There is deliberately no facade in front of them. {@code AdminController}
 * is itself a thin layer — each of its methods is one call plus pagination —
 * so a class whose only job was to forward to these four would have made two
 * layers of forwarding where the point was to have fewer.
 *
 * <h2>Transactions</h2>
 *
 * <p>Each service reads in one read-only transaction so that every statement a
 * request issues sees the same snapshot. Without it each repository call opened
 * its own session on its own connection, so a multi-query read could observe a
 * database that changed underneath it.
 *
 * <p>The isolation level is pinned rather than inherited because sharing a
 * transaction is not by itself enough: under {@code READ COMMITTED} every
 * statement takes a fresh snapshot, so a concurrent commit is still visible
 * between two queries of the same read. MySQL defaults to repeatable read and
 * would behave correctly by accident; stating it keeps the guarantee from
 * depending on how a given database happens to be configured.
 *
 * <p>The write methods carry their own {@code @Transactional}, because under a
 * read-only transaction Hibernate never flushes and a modified entity is
 * discarded without an error. Each one repeats the isolation level, and a new
 * one must too: a method annotation replaces the class annotation rather than
 * adding to it, so a bare {@code @Transactional} silently drops the pin and
 * leaves the write at whatever the database defaults to.
 *
 * <p>That rule does not rest on this comment. {@code ApiContractTest} finds
 * every service that pins the level on its class and asserts that each of its
 * transactional methods pins it too — by discovery rather than from a list, so
 * a service added later is held to it without anyone remembering to say so.
 */
package ua.nure.latysh.quizzes.api.admin;
