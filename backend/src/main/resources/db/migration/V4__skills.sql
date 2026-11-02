-- skill module: reference catalog shared by candidates (and later jobs and matching).
-- Names are stored normalised (trimmed, lower-case, single spaces); aliases resolve to a canonical skill.
CREATE TABLE skills (
    id           uuid PRIMARY KEY,
    name         varchar(60) NOT NULL,
    display_name varchar(80) NOT NULL,
    category     varchar(40),
    active       boolean     NOT NULL DEFAULT true,
    version      bigint      NOT NULL DEFAULT 0,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_skills_name UNIQUE (name)
);

CREATE TABLE skill_aliases (
    skill_id uuid        NOT NULL REFERENCES skills (id) ON DELETE CASCADE,
    alias    varchar(60) NOT NULL,
    PRIMARY KEY (skill_id, alias),
    CONSTRAINT uq_skill_aliases_alias UNIQUE (alias)
);

-- Seed data (reference data, so database-generated ids are acceptable here).
INSERT INTO skills (id, name, display_name, category) VALUES
    (gen_random_uuid(), 'java', 'Java', 'Language'),
    (gen_random_uuid(), 'kotlin', 'Kotlin', 'Language'),
    (gen_random_uuid(), 'python', 'Python', 'Language'),
    (gen_random_uuid(), 'javascript', 'JavaScript', 'Language'),
    (gen_random_uuid(), 'typescript', 'TypeScript', 'Language'),
    (gen_random_uuid(), 'c#', 'C#', 'Language'),
    (gen_random_uuid(), 'go', 'Go', 'Language'),
    (gen_random_uuid(), 'sql', 'SQL', 'Language'),
    (gen_random_uuid(), 'spring boot', 'Spring Boot', 'Framework'),
    (gen_random_uuid(), 'spring', 'Spring Framework', 'Framework'),
    (gen_random_uuid(), 'hibernate', 'Hibernate', 'Framework'),
    (gen_random_uuid(), 'react', 'React', 'Framework'),
    (gen_random_uuid(), 'angular', 'Angular', 'Framework'),
    (gen_random_uuid(), 'node.js', 'Node.js', 'Framework'),
    (gen_random_uuid(), 'postgresql', 'PostgreSQL', 'Data'),
    (gen_random_uuid(), 'mysql', 'MySQL', 'Data'),
    (gen_random_uuid(), 'mongodb', 'MongoDB', 'Data'),
    (gen_random_uuid(), 'redis', 'Redis', 'Data'),
    (gen_random_uuid(), 'kafka', 'Apache Kafka', 'Data'),
    (gen_random_uuid(), 'docker', 'Docker', 'DevOps'),
    (gen_random_uuid(), 'kubernetes', 'Kubernetes', 'DevOps'),
    (gen_random_uuid(), 'aws', 'AWS', 'Cloud'),
    (gen_random_uuid(), 'azure', 'Azure', 'Cloud'),
    (gen_random_uuid(), 'terraform', 'Terraform', 'DevOps'),
    (gen_random_uuid(), 'github actions', 'GitHub Actions', 'DevOps'),
    (gen_random_uuid(), 'ci/cd', 'CI/CD', 'DevOps'),
    (gen_random_uuid(), 'rest apis', 'REST APIs', 'Practice'),
    (gen_random_uuid(), 'microservices', 'Microservices', 'Practice'),
    (gen_random_uuid(), 'agile', 'Agile', 'Practice'),
    (gen_random_uuid(), 'testing', 'Automated Testing', 'Practice');

INSERT INTO skill_aliases (skill_id, alias)
SELECT s.id, a.alias FROM (VALUES
    ('postgresql', 'postgres'), ('javascript', 'js'), ('typescript', 'ts'), ('kubernetes', 'k8s'),
    ('go', 'golang'), ('node.js', 'nodejs'), ('spring boot', 'springboot'), ('spring boot', 'spring-boot'),
    ('react', 'reactjs'), ('aws', 'amazon web services'), ('c#', 'csharp'), ('rest apis', 'rest')
) AS a(name, alias) JOIN skills s ON s.name = a.name;
