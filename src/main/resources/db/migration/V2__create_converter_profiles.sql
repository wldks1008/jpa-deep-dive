CREATE TABLE converter_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    secret_memo VARCHAR(500) NOT NULL,
    marketing_agreed VARCHAR(1) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_converter_profiles_marketing_agreed
        CHECK (marketing_agreed IN ('Y', 'N'))
);
