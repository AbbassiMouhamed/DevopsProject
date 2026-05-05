-- SmartLingua MySQL initialisation
-- Creates one database per microservice (MySQL equivalent of schemas).
-- The JDBC URLs use createDatabaseIfNotExist=true as a fallback,
-- but pre-creating them here guarantees they exist before Hibernate starts.

CREATE DATABASE IF NOT EXISTS smartlingua_users     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smartlingua_courses   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smartlingua_quiz      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smartlingua_exams     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smartlingua_forum     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smartlingua_messaging CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smartlingua_privetcours CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smartlingua_adaptive  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smartlingua_ai        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Grant root access to all databases (root already has SUPER, this is a no-op but explicit)
GRANT ALL PRIVILEGES ON smartlingua_users.*      TO 'root'@'%';
GRANT ALL PRIVILEGES ON smartlingua_courses.*    TO 'root'@'%';
GRANT ALL PRIVILEGES ON smartlingua_quiz.*       TO 'root'@'%';
GRANT ALL PRIVILEGES ON smartlingua_exams.*      TO 'root'@'%';
GRANT ALL PRIVILEGES ON smartlingua_forum.*      TO 'root'@'%';
GRANT ALL PRIVILEGES ON smartlingua_messaging.*  TO 'root'@'%';
GRANT ALL PRIVILEGES ON smartlingua_privetcours.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON smartlingua_adaptive.*   TO 'root'@'%';
GRANT ALL PRIVILEGES ON smartlingua_ai.*         TO 'root'@'%';
FLUSH PRIVILEGES;
