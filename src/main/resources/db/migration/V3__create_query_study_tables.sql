CREATE TABLE query_study_members (
    member_id BIGINT NOT NULL,
    business_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    PRIMARY KEY (member_id),
    CONSTRAINT uk_query_study_members_business_id UNIQUE (business_id),
    CONSTRAINT uk_query_study_members_name UNIQUE (name)
);

CREATE TABLE query_study_notes (
    note_id BIGINT NOT NULL,
    title VARCHAR(255),
    PRIMARY KEY (note_id)
);
