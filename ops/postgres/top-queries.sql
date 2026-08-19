-- Top statements by total time (just db-stats). Per-call mean matters more than totals:
-- this app's profile is PK lookups and short row-lock writes, so mean times should be low ms.
SELECT left(regexp_replace(query, '\s+', ' ', 'g'), 90) AS query,
       calls,
       round(mean_exec_time::numeric, 3) AS mean_ms,
       round(max_exec_time::numeric, 1) AS max_ms,
       round(total_exec_time::numeric, 1) AS total_ms
FROM pg_stat_statements
WHERE query NOT LIKE '%pg_stat_statements%'
ORDER BY total_exec_time DESC
LIMIT 12;
