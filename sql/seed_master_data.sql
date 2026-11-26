-- =====================================================================
--  마스터 데이터 초기 적재 (MySQL 8.4 / PostgreSQL 공통 표준 SQL)
--  실행 순서: schema_*.sql -> seed_master_data.sql
--  user_types는 유형 명칭이 미정이므로 구조 확인용 샘플 3건만 넣음
-- =====================================================================

-- MySQL 클라이언트 문자셋을 utf8mb4로 고정 (한글 깨짐 방지).
-- /*! ... */ 는 MySQL만 실행하고 PostgreSQL은 주석으로 무시합니다.
/*!40101 SET NAMES utf8mb4 */;

-- 1. 스타일 9종 ---------------------------------------------------------
INSERT INTO styles (style_code, style_name, description, sort_order) VALUES
('CASUAL',   '캐주얼',   '티셔츠·셔츠·데님·코튼팬츠 중심의 편안하고 기본적인 데일리룩', 1),
('DANDY',    '댄디',     '셔츠·슬랙스·니트·로퍼로 정돈된 인상을 주는 세미 포멀룩', 2),
('MINIMAL',  '미니멀',   '무채색 위주, 장식을 줄이고 핏과 소재로 완성하는 룩', 3),
('CITY_BOY', '시티보이', '오버사이즈 셔츠·와이드 팬츠·볼캡 등 여유로운 실루엣의 도시형 룩', 4),
('AMEKAJI',  '아메카지', '워크재킷·데님·치노·부츠 등 아메리칸 캐주얼을 재해석한 룩', 5),
('STREET',   '스트릿',   '그래픽 티·후디·카고·스니커즈 중심의 자유로운 룩', 6),
('SPORTY',   '스포티',   '트랙재킷·저지·조거팬츠 등 기능성 소재를 일상에 활용한 룩', 7),
('GORPCORE', '고프코어', '쉘 재킷·플리스·카고팬츠·트레일화 등 아웃도어 아이템 중심 룩', 8),
('VINTAGE',  '빈티지',   '워싱 데님·레트로 패턴·올드스쿨 아이템으로 연출하는 룩', 9);

-- 2. 얼굴형 -------------------------------------------------------------
INSERT INTO face_shapes (face_shape_code, face_shape_name, description, hair_tip, sort_order) VALUES
-- 판정 기준: docs/code_artifact.md (얼굴형 6종). 어느 유형 점수도 0.8 미만이면 OVAL
('OVAL',      '계란형',       '얼굴 길이·이마·턱 비율이 모두 평균 범위인 균형형. 대부분의 헤어·안경·넥라인이 잘 어울립니다.',
              '비율이 균형 잡혀 있어 앞머리를 내리든 올리든 대부분 어울립니다. 이마를 드러내는 스타일로 깔끔함을 살려 보세요.', 1),
('OBLONG',    '긴형',         '얼굴 길이가 광대 폭보다 확연히 긴 형태. 크루넥·터틀넥·볼캡처럼 가로선을 더하는 아이템이 비율을 맞춰 줍니다.',
              '앞머리로 이마를 덮어 세로 길이를 줄이고, 옆머리에 볼륨을 주세요. 윗머리를 높게 세우거나 이마를 다 드러내면 얼굴이 더 길어 보입니다.', 2),
('RECTANGLE', '직사각형',     '턱이 넓고 턱선이 각진 형태(길이가 길면 더 뚜렷). 라운드넥·부드러운 소재로 인상을 완화하면 좋습니다.',
              '컬이 있는 펌으로 옆선을 부드럽게 만들어 각진 턱을 완화하세요. 옆을 짧게 치는 크롭·슬릭백은 턱선을 강조합니다.', 3),
('ROUND',     '둥근형',       '얼굴 길이가 짧고 턱선·턱끝이 둥근 형태. 브이넥·오픈칼라처럼 세로선을 만드는 넥라인이 얼굴을 갸름해 보이게 합니다.',
              '윗머리에 볼륨을 주고 옆머리는 짧게 정리해 세로선을 만드세요. 앞머리를 일자로 내리거나 옆 볼륨이 크면 더 둥글어 보입니다.', 4),
('HEART',     '하트형',       '이마가 상대적으로 넓고 턱과 턱끝이 좁고 뾰족한 형태. 셔츠 칼라·하이넥 등으로 하관에 볼륨을 주면 균형이 맞습니다.',
              '앞머리로 넓은 이마를 덮고, 귀 아래쪽에 볼륨을 주면 좁은 턱과 균형이 맞습니다. 이마를 모두 드러내는 깐머리는 피하세요.', 5),
('DIAMOND',   '다이아몬드형', '광대가 가장 넓고 이마와 턱이 좁은 형태. 이마와 턱 쪽 시선을 분산하는 칼라·액세서리가 효과적입니다.',
              '이마를 채우는 앞머리와 광대 옆을 감싸는 볼륨으로 광대를 완화하세요. 옆을 짧게 치면 광대가 더 도드라집니다.', 6);

-- 3. 체형 ---------------------------------------------------------------
INSERT INTO body_types (body_type_code, body_type_name, description, styling_tip, sort_order) VALUES
-- 판정 기준: docs/code_artifact.md (체형 4종, Size Korea 20대 남성 평균 대비 편차). 순서 규칙: 표준 -> 큰 사각 -> 작은 역삼각 -> 사각
('STANDARD',           '표준체형',       '어깨와 몸통(허리·엉덩이) 폭이 모두 20대 남성 평균에서 ±5% 이내인 균형 체형.',
                       '대부분의 핏이 어울립니다. 레귤러핏을 기본으로 스타일에 맞게 실루엣을 조절하세요.', 1),
('LARGE_SQUARE',       '큰 사각체형',    '몸통(허리·엉덩이) 폭이 평균보다 8% 이상 큰 체형.',
                       '어두운 톤과 세로 라인, 드롭숄더 없는 정핏 아우터로 실루엣을 정리하세요. 상하의 톤을 맞추면 길어 보입니다.', 2),
('SMALL_INV_TRIANGLE', '작은 역삼각체형', '몸통은 평균 이하인데 어깨가 몸통보다 6%p 이상 넓은 체형.',
                       '상의는 신축성 있는 레귤러핏, 하의는 테이퍼드·와이드로 하체 볼륨을 맞추면 균형이 좋아집니다.', 3),
('SQUARE',             '사각체형',       '어깨와 몸통 폭이 비슷해 허리선이 두드러지지 않는 체형(위 세 유형에 해당하지 않는 나머지).',
                       '레이어드와 구조감 있는 어깨선의 아우터로 입체감을 주고, 허리선이 있는 셔츠·재킷으로 실루엣에 변화를 주세요.', 4);

-- 4. 종합 유형 (샘플 3건 - 확정 시 같은 형식으로 20건 이상 추가) ---------
INSERT INTO user_types (type_code, type_name, summary, face_shape_code, body_type_code, primary_style_code, sort_order) VALUES
('T01', '[샘플] 소프트 미니멀형', '부드러운 인상과 균형 잡힌 체형으로 미니멀한 룩이 잘 어울리는 유형', 'OVAL',   'STANDARD', 'MINIMAL',  1),
('T02', '[샘플] 레이어드 시티형', '레이어드로 볼륨을 더하면 매력이 살아나는 유형',                     'OBLONG', 'SQUARE',   'CITY_BOY', 2),
('T03', '[샘플] 클린 댄디형',     '정돈된 셔츠·슬랙스로 탄탄한 체격을 살리는 유형',                     'RECTANGLE', 'SMALL_INV_TRIANGLE', 'DANDY', 3);

-- 4-1. 헤어스타일 -------------------------------------------------------
-- 이미지(hair_style_images)는 SQL로 넣지 않음: hair_images/{코드}/ 에 사진을 넣고 load_hair_images.py 실행
INSERT INTO hair_categories (hair_category_code, hair_category_name, sort_order) VALUES
('PARTED',      '가르마',   1),
('SLICKED',     '깐머리',   2),
('DANDY',       '댄디',     3),
('SHADOW_PERM', '쉐도우펌', 4),
('CROP',        '크롭',     5);

INSERT INTO hair_styles (hair_style_code, hair_category_code, hair_style_name, description, sort_order) VALUES
('PARTED_PERM', 'PARTED',      '가르마펌',     '가르마를 타고 앞머리를 자연스러운 웨이브로 넘긴 스타일. 부드럽고 성숙한 인상.', 1),
('GUILE_CUT',   'PARTED',      '가일컷',       '앞머리를 한쪽으로 쓸어 넘기고 옆은 짧게 정리한 스타일. 이마 일부를 가려 시원하면서 단정함.', 2),
('LEAF_CUT',    'PARTED',      '리프컷',       '앞머리를 가운데서 나눠 나뭇잎처럼 양옆으로 흘린 스타일. 옆 볼륨이 얼굴선을 감쌈.', 3),
('REGENT_CUT',  'SLICKED',     '리젠트컷',     '앞머리를 위로 세워 뒤로 넘기고 옆은 짧게 한 스타일. 윗볼륨으로 세로 길이를 더함.', 4),
('SLICK_BACK',  'SLICKED',     '슬릭백',       '머리 전체를 뒤로 매끈하게 넘긴 스타일. 이마와 얼굴선이 모두 드러남.', 5),
('POMADE',      'SLICKED',     '포마드',       '포마드로 가르마를 선명하게 타고 윤기 있게 넘긴 스타일. 포멀하고 단정함.', 7),
('DANDY_CUT',   'DANDY',       '댄디컷',       '이마를 덮는 자연스러운 앞머리에 옆과 뒤를 단정하게 정리한 기본 스타일.', 8),
('SHADOW_PERM', 'SHADOW_PERM', '쉐도우펌',     '모발 끝에 가벼운 컬을 넣어 공기감과 볼륨을 준 펌. 부드럽고 자연스러운 인상.', 9),
('WAVE_PERM',   'SHADOW_PERM', '웨이브펌',     '중간 길이 모발에 굵은 웨이브를 넣은 펌. 옆 볼륨이 풍성함.', 10),
('HIPPIE_PERM', 'SHADOW_PERM', '히피펌',       '중단발에 잔컬을 전체로 넣은 펌. 볼륨이 크고 개성 있는 인상.', 11),
('DROP_CUT',    'CROP',        '드롭컷',       '짧은 크롭 기장에 앞머리를 이마 위로 떨어뜨린 스타일. 깔끔하고 어려 보이는 인상.', 12),
('CROP_CUT',    'CROP',        '크롭컷',       '윗머리를 짧게 질감 처리하고 옆은 페이드로 친 스타일. 시원하고 남성적인 인상.', 13);

-- 4-2. 얼굴형별 추천/비추천 헤어 [초안 - 팀 확정 필요] --------------------
-- 근거: 기준서(docs/code_artifact.md)의 판정 특징
--   긴형(길이 비↑) -> 세로를 줄이는 앞머리·옆 볼륨 / 둥근형(길이 비↓, 턱 각도↑) -> 윗볼륨·옆 짧게
--   직사각형(턱 비↑, 턱 각도↓) -> 컬로 턱선 완화 / 하트형(이마 비↑, 턱·턱끝 비↓) -> 이마 커버 + 아래 볼륨
--   다이아몬드형(이마·턱 비↓, 광대 최대) -> 이마 채우고 광대 옆 볼륨 / 계란형 -> 대부분 OK
-- 매핑이 없는 조합은 '보통'으로 취급 (조회 시 LEFT JOIN 후 COALESCE(suitability, 'NEUTRAL'))
INSERT INTO hair_style_face_shapes (face_shape_code, hair_style_code, suitability, priority, reason) VALUES
('OVAL',      'DANDY_CUT',   'RECOMMEND', 1, '균형 잡힌 비율이라 기본 앞머리 스타일이 가장 자연스럽게 어울립니다'),
('OVAL',      'PARTED_PERM', 'RECOMMEND', 2, '가르마와 웨이브로 성숙한 인상을 더하기 좋습니다'),
('OVAL',      'REGENT_CUT',  'RECOMMEND', 3, '이마를 드러내도 비율이 무너지지 않습니다'),
('OVAL',      'SLICK_BACK',  'RECOMMEND', 4, '얼굴선이 고르게 드러나 깔끔한 인상을 줍니다'),

('OBLONG',    'DANDY_CUT',   'RECOMMEND', 1, '앞머리가 이마를 덮어 얼굴 세로 길이가 짧아 보입니다'),
('OBLONG',    'SHADOW_PERM', 'RECOMMEND', 2, '옆 볼륨이 가로 폭을 더해 긴 얼굴의 비율을 맞춥니다'),
('OBLONG',    'WAVE_PERM',   'RECOMMEND', 3, '풍성한 옆 웨이브가 얼굴 폭을 넓혀 보이게 합니다'),
('OBLONG',    'DROP_CUT',    'RECOMMEND', 4, '이마 위로 떨어지는 앞머리가 세로선을 끊어 줍니다'),
('OBLONG',    'REGENT_CUT',  'AVOID',     1, '윗볼륨이 세로 길이를 더해 얼굴이 더 길어 보입니다'),
('OBLONG',    'SLICK_BACK',  'AVOID',     1, '이마를 모두 드러내 얼굴 길이가 강조됩니다'),
('OBLONG',    'POMADE',      'AVOID',     1, '넘긴 머리로 이마가 드러나 세로 비율이 길어 보입니다'),

('RECTANGLE', 'SHADOW_PERM', 'RECOMMEND', 1, '가벼운 컬이 각진 턱선을 부드럽게 감쌉니다'),
('RECTANGLE', 'PARTED_PERM', 'RECOMMEND', 2, '웨이브가 직선적인 얼굴형에 곡선을 더합니다'),
('RECTANGLE', 'WAVE_PERM',   'RECOMMEND', 3, '옆 웨이브가 넓은 턱과 균형을 맞춥니다'),
('RECTANGLE', 'LEAF_CUT',    'RECOMMEND', 4, '양옆으로 흐르는 앞머리가 얼굴 윤곽을 부드럽게 합니다'),
('RECTANGLE', 'CROP_CUT',    'AVOID',     1, '짧은 옆머리가 각진 턱선을 그대로 드러냅니다'),
('RECTANGLE', 'SLICK_BACK',  'AVOID',     1, '얼굴 윤곽이 모두 드러나 각진 인상이 강해집니다'),

('ROUND',     'REGENT_CUT',  'RECOMMEND', 1, '윗볼륨이 세로선을 만들어 얼굴이 갸름해 보입니다'),
('ROUND',     'POMADE',      'RECOMMEND', 2, '옆을 붙이고 위를 정돈해 둥근 윤곽이 줄어 보입니다'),
('ROUND',     'CROP_CUT',    'RECOMMEND', 4, '옆을 짧게 치고 윗머리에 질감을 줘 세로 비율을 살립니다'),
('ROUND',     'GUILE_CUT',   'RECOMMEND', 5, '사선 앞머리가 둥근 얼굴에 대각선을 만들어 줍니다'),
('ROUND',     'HIPPIE_PERM', 'AVOID',     1, '전체 볼륨이 커서 얼굴이 더 둥글고 커 보입니다'),
('ROUND',     'DANDY_CUT',   'AVOID',     1, '일자 앞머리가 얼굴 길이를 줄여 더 둥글어 보입니다'),

('HEART',     'DANDY_CUT',   'RECOMMEND', 1, '앞머리가 넓은 이마를 자연스럽게 가립니다'),
('HEART',     'LEAF_CUT',    'RECOMMEND', 2, '이마를 덮으면서 옆선이 좁은 턱 쪽 균형을 잡아 줍니다'),
('HEART',     'SHADOW_PERM', 'RECOMMEND', 3, '아래쪽 컬 볼륨이 좁은 턱을 보완합니다'),
('HEART',     'WAVE_PERM',   'RECOMMEND', 4, '귀 아래 웨이브가 하관에 볼륨을 더합니다'),
('HEART',     'SLICK_BACK',  'AVOID',     1, '넓은 이마가 그대로 드러나 역삼각 인상이 강해집니다'),
('HEART',     'POMADE',      'AVOID',     1, '이마를 드러내는 넘김 머리가 상하 불균형을 키웁니다'),
('HEART',     'REGENT_CUT',  'AVOID',     1, '윗볼륨이 이마 쪽을 더 넓어 보이게 합니다'),

('DIAMOND',   'GUILE_CUT',   'RECOMMEND', 1, '사선 앞머리가 좁은 이마를 채우고 시선을 분산합니다'),
('DIAMOND',   'LEAF_CUT',    'RECOMMEND', 2, '양옆으로 흐르는 머리가 광대 옆을 부드럽게 감쌉니다'),
('DIAMOND',   'SHADOW_PERM', 'RECOMMEND', 3, '옆 볼륨이 도드라진 광대를 완화합니다'),
('DIAMOND',   'DANDY_CUT',   'RECOMMEND', 4, '앞머리가 좁은 이마를 채워 줍니다'),
('DIAMOND',   'CROP_CUT',    'AVOID',     1, '옆을 짧게 쳐 광대가 더 도드라집니다'),
('DIAMOND',   'SLICK_BACK',  'AVOID',     1, '좁은 이마와 넓은 광대의 대비가 커집니다');

-- 5. 유형별 추천 스타일 (대표 스타일 + 보조 추천) -------------------------
INSERT INTO user_type_styles (user_type_id, style_code, priority)
SELECT user_type_id, primary_style_code, 1 FROM user_types;

INSERT INTO user_type_styles (user_type_id, style_code, priority)
SELECT user_type_id, 'CASUAL', 2 FROM user_types WHERE type_code IN ('T01', 'T02');

INSERT INTO user_type_styles (user_type_id, style_code, priority)
SELECT user_type_id, 'MINIMAL', 2 FROM user_types WHERE type_code = 'T03';
