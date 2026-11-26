-- =====================================================================
--  AI 패션 스타일링 서비스 - DB 스키마 (MySQL 8.4)
--  문자셋: utf8mb4 / 엔진: InnoDB
--  실행 순서: schema_mysql.sql -> seed_master_data.sql
-- =====================================================================

SET NAMES utf8mb4;

-- 재생성 시 FK 역순으로 삭제
DROP VIEW  IF EXISTS v_outfit_sets_invalid_item_count;
DROP VIEW  IF EXISTS v_hair_recommendations;
DROP TABLE IF EXISTS user_analysis_results;
DROP TABLE IF EXISTS hair_style_face_shapes;
DROP TABLE IF EXISTS hair_style_images;
DROP TABLE IF EXISTS hair_styles;
DROP TABLE IF EXISTS hair_categories;
DROP TABLE IF EXISTS outfit_items;
DROP TABLE IF EXISTS outfit_sets;
DROP TABLE IF EXISTS user_type_styles;
DROP TABLE IF EXISTS user_types;
DROP TABLE IF EXISTS body_types;
DROP TABLE IF EXISTS face_shapes;
DROP TABLE IF EXISTS styles;

-- ---------------------------------------------------------------------
-- 1. 마스터: 스타일 (9종, 이후 행 추가로 확장)
--    ENUM 대신 마스터 + FK를 써서 ALTER 없이 스타일을 추가할 수 있게 함
-- ---------------------------------------------------------------------
CREATE TABLE styles (
    style_code   VARCHAR(20)  NOT NULL COMMENT '스타일 코드 (CASUAL, DANDY ...)',
    style_name   VARCHAR(50)  NOT NULL COMMENT '화면 표시명',
    description  TEXT             NULL COMMENT '스타일 설명',
    sort_order   SMALLINT     NOT NULL DEFAULT 0,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_styles PRIMARY KEY (style_code),
    CONSTRAINT ck_styles_code CHECK (style_code REGEXP '^[A-Z][A-Z0-9_]*$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='스타일 마스터';

-- ---------------------------------------------------------------------
-- 2. 마스터: 얼굴형
-- ---------------------------------------------------------------------
CREATE TABLE face_shapes (
    face_shape_code  VARCHAR(30)  NOT NULL COMMENT '얼굴형 코드 (OVAL, ROUND ...)',
    face_shape_name  VARCHAR(50)  NOT NULL COMMENT '얼굴형 이름',
    description      TEXT         NOT NULL COMMENT '얼굴형별 특징 설명 (상세보기 화면)',
    hair_tip         TEXT             NULL COMMENT '얼굴형별 헤어 연출 요령 (헤어 추천 화면)',
    sort_order       SMALLINT     NOT NULL DEFAULT 0,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_face_shapes PRIMARY KEY (face_shape_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='얼굴형 마스터';

-- ---------------------------------------------------------------------
-- 3. 마스터: 체형
-- ---------------------------------------------------------------------
CREATE TABLE body_types (
    body_type_code  VARCHAR(30)  NOT NULL COMMENT '체형 코드 (SLIM, STANDARD ...)',
    body_type_name  VARCHAR(50)  NOT NULL COMMENT '체형 이름',
    description     TEXT         NOT NULL COMMENT '체형별 특징 설명 (상세보기 화면)',
    styling_tip     TEXT             NULL COMMENT '스타일링 보완 팁',
    sort_order      SMALLINT     NOT NULL DEFAULT 0,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_body_types PRIMARY KEY (body_type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='체형 마스터';

-- ---------------------------------------------------------------------
-- 4. 마스터: 종합 유형 (20종 이상, 명칭 미정 -> 행 INSERT로 확장)
--    type_code: 명칭이 바뀌어도 변하지 않는 안정 키 (예: T01, T02 ...)
-- ---------------------------------------------------------------------
CREATE TABLE user_types (
    user_type_id        BIGINT        NOT NULL AUTO_INCREMENT,
    type_code           VARCHAR(50)   NOT NULL COMMENT '불변 유형 코드',
    type_name           VARCHAR(100)  NOT NULL COMMENT '유형명 ("당신은 [유형명]입니다")',
    summary             VARCHAR(255)      NULL COMMENT '결과 요약 화면 한 줄 설명',
    description         TEXT              NULL COMMENT '유형 상세 설명',
    face_shape_code     VARCHAR(30)   NOT NULL COMMENT '매칭 얼굴형',
    body_type_code      VARCHAR(30)   NOT NULL COMMENT '매칭 체형',
    primary_style_code  VARCHAR(20)   NOT NULL COMMENT '대표 추천 스타일',
    sort_order          SMALLINT      NOT NULL DEFAULT 0,
    is_active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_types PRIMARY KEY (user_type_id),
    CONSTRAINT uq_user_types_code UNIQUE (type_code),
    CONSTRAINT fk_user_types_face  FOREIGN KEY (face_shape_code)    REFERENCES face_shapes (face_shape_code) ON UPDATE CASCADE,
    CONSTRAINT fk_user_types_body  FOREIGN KEY (body_type_code)     REFERENCES body_types (body_type_code)   ON UPDATE CASCADE,
    CONSTRAINT fk_user_types_style FOREIGN KEY (primary_style_code) REFERENCES styles (style_code)           ON UPDATE CASCADE,
    INDEX ix_user_types_face_body (face_shape_code, body_type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='종합 유형 마스터';

-- ---------------------------------------------------------------------
-- 5. 유형별 추천 스타일 (N:M, 선택) - 대표 스타일 외 2·3순위 추천용
-- ---------------------------------------------------------------------
CREATE TABLE user_type_styles (
    user_type_id  BIGINT       NOT NULL,
    style_code    VARCHAR(20)  NOT NULL,
    priority      SMALLINT     NOT NULL DEFAULT 1 COMMENT '1 = 최우선',
    CONSTRAINT pk_user_type_styles PRIMARY KEY (user_type_id, style_code),
    CONSTRAINT fk_uts_type  FOREIGN KEY (user_type_id) REFERENCES user_types (user_type_id) ON DELETE CASCADE,
    CONSTRAINT fk_uts_style FOREIGN KEY (style_code)   REFERENCES styles (style_code)       ON UPDATE CASCADE,
    CONSTRAINT ck_uts_priority CHECK (priority >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='유형-스타일 추천 매핑';

-- ---------------------------------------------------------------------
-- 6. 코디 세트 (부모, 1)
-- ---------------------------------------------------------------------
CREATE TABLE outfit_sets (
    set_id                BIGINT        NOT NULL AUTO_INCREMENT,
    recipe_key            VARCHAR(50)   NOT NULL COMMENT '레시피 고유키 (예: CASUAL_01) - 재실행 시 중복 방지',
    style_code            VARCHAR(20)   NOT NULL,
    set_title             VARCHAR(100)  NOT NULL,
    full_shot_image_path  VARCHAR(500)  NOT NULL COMMENT '전신 착용샷 경로',
    description           TEXT              NULL,
    generation_meta       JSON              NULL COMMENT '프롬프트, seed, 모델 등 생성 메타데이터',
    is_active             BOOLEAN       NOT NULL DEFAULT TRUE COMMENT '품질 미달 컷 숨김용',
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_outfit_sets PRIMARY KEY (set_id),
    CONSTRAINT uq_outfit_sets_recipe UNIQUE (recipe_key),
    CONSTRAINT fk_outfit_sets_style FOREIGN KEY (style_code) REFERENCES styles (style_code) ON UPDATE CASCADE,
    INDEX ix_outfit_sets_style (style_code, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='코디 세트';

-- ---------------------------------------------------------------------
-- 7. 코디 구성 아이템 (자식, N) - 세트당 2~5개
--    상한 5개: display_order 1~5 + UNIQUE(set_id, display_order)로 DB에서 보장
--    하한 2개: 여러 행에 걸친 조건이라 적재 스크립트에서 검증
--    (점검용 뷰 v_outfit_sets_invalid_item_count 제공)
-- ---------------------------------------------------------------------
CREATE TABLE outfit_items (
    item_id          BIGINT        NOT NULL AUTO_INCREMENT,
    set_id           BIGINT        NOT NULL,
    display_order    SMALLINT      NOT NULL COMMENT '출력 순서 (1~5)',
    category         VARCHAR(10)   NOT NULL COMMENT 'OUTER, TOP, INNER, BOTTOM, SHOES, BAG, ACC',
    item_name        VARCHAR(100)  NOT NULL COMMENT '예: 블루종, 체크셔츠, 코튼팬츠',
    item_image_path  VARCHAR(500)  NOT NULL COMMENT '개별 아이템(플랫레이) 이미지 경로',
    generation_meta  JSON              NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_outfit_items PRIMARY KEY (item_id),
    CONSTRAINT uq_outfit_items_order UNIQUE (set_id, display_order),
    CONSTRAINT fk_outfit_items_set FOREIGN KEY (set_id) REFERENCES outfit_sets (set_id) ON DELETE CASCADE,
    CONSTRAINT ck_outfit_items_order CHECK (display_order BETWEEN 1 AND 5),
    CONSTRAINT ck_outfit_items_category CHECK (category IN ('OUTER', 'TOP', 'INNER', 'BOTTOM', 'SHOES', 'BAG', 'ACC'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='코디 구성 아이템';

-- ---------------------------------------------------------------------
-- 7-1. 헤어스타일: 카테고리 -> 스타일(13) -> 이미지(스타일당 5장 내외, 1:N)
--      얼굴형별 추천/비추천은 hair_style_face_shapes (판정 기준: docs/code_artifact.md)
--      이미지는 load_hair_images.py 로 hair_images/{코드}/ 폴더를 적재
-- ---------------------------------------------------------------------
CREATE TABLE hair_categories (
    hair_category_code  VARCHAR(30)  NOT NULL COMMENT '예: PARTED, SLICKED, DANDY, SHADOW_PERM, CROP',
    hair_category_name  VARCHAR(50)  NOT NULL,
    sort_order          SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT pk_hair_categories PRIMARY KEY (hair_category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='헤어스타일 카테고리';

CREATE TABLE hair_styles (
    hair_style_code     VARCHAR(30)  NOT NULL COMMENT '불변 코드. hair_styles.json 키, hair_images/ 폴더명과 동일',
    hair_category_code  VARCHAR(30)  NOT NULL,
    hair_style_name     VARCHAR(50)  NOT NULL,
    description         TEXT             NULL COMMENT '스타일 설명 (헤어 상세 화면)',
    sort_order          SMALLINT     NOT NULL DEFAULT 0,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_hair_styles PRIMARY KEY (hair_style_code),
    CONSTRAINT fk_hair_styles_cat FOREIGN KEY (hair_category_code) REFERENCES hair_categories (hair_category_code) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='헤어스타일 마스터';

CREATE TABLE hair_style_images (
    image_id         BIGINT        NOT NULL AUTO_INCREMENT,
    hair_style_code  VARCHAR(30)   NOT NULL,
    image_path       VARCHAR(500)  NOT NULL COMMENT 'hair_images/ 기준 상대경로 (예: DANDY_CUT/01_front.jpg)',
    view_angle       VARCHAR(10)       NULL COMMENT 'FRONT, SIDE, BACK, DIAGONAL (파일명 접미사에서 읽음, 없으면 NULL)',
    is_primary       BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '대표 이미지 (목록·추천 카드에 노출). 스타일당 1장',
    sort_order       SMALLINT      NOT NULL DEFAULT 1 COMMENT '상세 화면 노출 순서 (파일명 순)',
    is_active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_hair_style_images PRIMARY KEY (image_id),
    CONSTRAINT uq_hair_style_images UNIQUE (hair_style_code, image_path),
    CONSTRAINT uq_hair_style_images_order UNIQUE (hair_style_code, sort_order),
    CONSTRAINT fk_hsi_style FOREIGN KEY (hair_style_code) REFERENCES hair_styles (hair_style_code) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT ck_hsi_angle CHECK (view_angle IN ('FRONT', 'SIDE', 'BACK', 'DIAGONAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='헤어스타일 이미지';

CREATE TABLE hair_style_face_shapes (
    hair_style_code  VARCHAR(30)   NOT NULL,
    face_shape_code  VARCHAR(30)   NOT NULL,
    suitability      VARCHAR(10)   NOT NULL DEFAULT 'RECOMMEND' COMMENT 'RECOMMEND = 추천, AVOID = 비추천',
    priority         SMALLINT      NOT NULL DEFAULT 1 COMMENT '추천 순위 (1 = 최우선). AVOID 행은 의미 없음',
    reason           VARCHAR(255)      NULL COMMENT '추천/비추천 이유 (화면 노출 문구)',
    CONSTRAINT pk_hsfs PRIMARY KEY (hair_style_code, face_shape_code),
    CONSTRAINT fk_hsfs_style FOREIGN KEY (hair_style_code) REFERENCES hair_styles (hair_style_code) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_hsfs_face  FOREIGN KEY (face_shape_code) REFERENCES face_shapes (face_shape_code) ON UPDATE CASCADE,
    CONSTRAINT ck_hsfs_priority CHECK (priority >= 1),
    CONSTRAINT ck_hsfs_suit CHECK (suitability IN ('RECOMMEND', 'AVOID')),
    INDEX ix_hsfs_face (face_shape_code, suitability, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='얼굴형별 추천/비추천 헤어. 매핑이 없는 조합 = 보통';

-- ---------------------------------------------------------------------
-- 8. 사용자 분석 결과
--    user_id: 회원 테이블이 별도 서비스에 있다는 가정으로 FK 없이 둠
--             (회원 테이블이 같은 DB에 있으면 FK 추가 권장)
-- ---------------------------------------------------------------------
CREATE TABLE user_analysis_results (
    result_id           BIGINT        NOT NULL AUTO_INCREMENT,
    user_id             BIGINT        NOT NULL,
    height              DECIMAL(5,1)  NOT NULL COMMENT '키 (cm)',
    weight              DECIMAL(5,1)  NOT NULL COMMENT '몸무게 (kg)',
    face_shape_code     VARCHAR(30)   NOT NULL,
    body_type_code      VARCHAR(30)   NOT NULL,
    user_type_id        BIGINT        NOT NULL,
    face_metrics        JSON              NULL COMMENT '얼굴 측정값 (length_ratio, forehead_ratio, jaw_ratio, chin_ratio, jaw_angle_deg) - 기준 재보정용',
    body_metrics        JSON              NULL COMMENT '체형 측정값 (shoulder_dev, waist_dev, hip_dev, trunk_dev, upper_taper)',
    hair_type          VARCHAR(30)       NULL COMMENT '모질 - 헤어 추천 기능 추가 시 사용 (예: STRAIGHT, WAVY, CURLY)',
    recommended_set_id  BIGINT            NULL COMMENT '상세보기에 노출한 코디 세트 (재현용)',
    recommended_hair_style_code VARCHAR(30) NULL COMMENT '상세보기에 노출한 추천 헤어 (재현용)',
    analyzed_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_analysis_results PRIMARY KEY (result_id),
    CONSTRAINT fk_uar_face FOREIGN KEY (face_shape_code)    REFERENCES face_shapes (face_shape_code) ON UPDATE CASCADE,
    CONSTRAINT fk_uar_body FOREIGN KEY (body_type_code)     REFERENCES body_types (body_type_code)   ON UPDATE CASCADE,
    CONSTRAINT fk_uar_type FOREIGN KEY (user_type_id)       REFERENCES user_types (user_type_id),
    CONSTRAINT fk_uar_set  FOREIGN KEY (recommended_set_id) REFERENCES outfit_sets (set_id) ON DELETE SET NULL,
    CONSTRAINT fk_uar_hair FOREIGN KEY (recommended_hair_style_code) REFERENCES hair_styles (hair_style_code) ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ck_uar_height CHECK (height BETWEEN 100 AND 250),
    CONSTRAINT ck_uar_weight CHECK (weight BETWEEN 30 AND 300),
    INDEX ix_uar_user (user_id, analyzed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사용자 분석 결과';

-- ---------------------------------------------------------------------
-- 점검용 뷰: 아이템 수가 2~5개 범위를 벗어난 세트
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_outfit_sets_invalid_item_count AS
SELECT s.set_id, s.recipe_key, s.style_code, COUNT(i.item_id) AS item_count
FROM outfit_sets s
LEFT JOIN outfit_items i ON i.set_id = s.set_id
GROUP BY s.set_id, s.recipe_key, s.style_code
HAVING COUNT(i.item_id) < 2 OR COUNT(i.item_id) > 5;

-- ---------------------------------------------------------------------
-- 조회용 뷰: 얼굴형별 헤어 추천/비추천 + 대표 이미지 (조건부 출력의 기본 단위)
--   예) SELECT * FROM v_hair_recommendations WHERE face_shape_code = 'ROUND' AND suitability = 'RECOMMEND' ORDER BY priority;
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_hair_recommendations AS
SELECT m.face_shape_code, f.face_shape_name, m.suitability, m.priority, m.reason,
       h.hair_style_code, h.hair_style_name, c.hair_category_name, h.description,
       i.image_path AS primary_image_path,
       (SELECT COUNT(*) FROM hair_style_images x WHERE x.hair_style_code = h.hair_style_code AND x.is_active) AS image_count
FROM hair_style_face_shapes m
JOIN face_shapes f      ON f.face_shape_code = m.face_shape_code
JOIN hair_styles h      ON h.hair_style_code = m.hair_style_code AND h.is_active
JOIN hair_categories c  ON c.hair_category_code = h.hair_category_code
LEFT JOIN hair_style_images i ON i.hair_style_code = h.hair_style_code AND i.is_primary AND i.is_active;
