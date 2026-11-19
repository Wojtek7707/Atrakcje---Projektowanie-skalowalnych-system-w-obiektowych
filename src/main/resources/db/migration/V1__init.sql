CREATE TABLE categories (
  id    SERIAL PRIMARY KEY,
  code  VARCHAR(32) UNIQUE NOT NULL,
  name  VARCHAR(64) NOT NULL
);

CREATE TABLE locations (
  id       VARCHAR(32) PRIMARY KEY,
  name     VARCHAR(128) NOT NULL,
  lat      DECIMAL(9,6) NOT NULL CHECK (lat BETWEEN -90 AND 90),
  lng      DECIMAL(9,6) NOT NULL CHECK (lng BETWEEN -180 AND 180),
  address  VARCHAR(255)
);

CREATE TABLE attractions (
  id                  VARCHAR(32) PRIMARY KEY,
  name                VARCHAR(128) NOT NULL,
  category_id         INT NOT NULL REFERENCES categories(id),
  location_id         VARCHAR(32) NOT NULL REFERENCES locations(id),
  price_per_person    DECIMAL(8,2) NOT NULL CHECK (price_per_person >= 0),
  visit_duration_min  INT NOT NULL CHECK (visit_duration_min > 0),
  indoor              BOOLEAN NOT NULL,
  description         TEXT,
  active              BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE opening_hours (
  attraction_id  VARCHAR(32) NOT NULL REFERENCES attractions(id) ON DELETE CASCADE,
  weekday        SMALLINT NOT NULL CHECK (weekday BETWEEN 1 AND 7),  -- ISO-8601: 1 = poniedziałek
  open_time      TIME NOT NULL,
  close_time     TIME NOT NULL,
  PRIMARY KEY (attraction_id, weekday),
  CHECK (close_time > open_time)
);

-- PostgreSQL nie indeksuje kluczy obcych automatycznie
CREATE INDEX idx_attr_category ON attractions(category_id);
CREATE INDEX idx_attr_location ON attractions(location_id);
