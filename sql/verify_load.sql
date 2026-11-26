/*!40101 SET NAMES utf8mb4 */;
-- 적재 확인용 조회 (읽기 전용). 기대값은 각 줄 끝 주석 참고
SELECT 'styles' AS t, COUNT(*) AS n FROM styles                       -- 9
UNION ALL SELECT 'hair_styles', COUNT(*) FROM hair_styles             -- 12 (아이비리그 제외)
UNION ALL SELECT 'hair_style_images', COUNT(*) FROM hair_style_images -- 12
UNION ALL SELECT 'hair_primary', COUNT(*) FROM hair_style_images WHERE is_primary   -- 12
UNION ALL SELECT 'hair_face_map', COUNT(*) FROM hair_style_face_shapes             -- 36
UNION ALL SELECT 'ivy_league_rows', (SELECT COUNT(*) FROM hair_styles WHERE hair_style_code = 'IVY_LEAGUE')
                                  + (SELECT COUNT(*) FROM hair_style_face_shapes WHERE hair_style_code = 'IVY_LEAGUE')  -- 0
UNION ALL SELECT 'outfit_sets', COUNT(*) FROM outfit_sets             -- 45
UNION ALL SELECT 'outfit_items', COUNT(*) FROM outfit_items           -- 220
UNION ALL SELECT 'sets_bad_item_count', COUNT(*) FROM v_outfit_sets_invalid_item_count;  -- 0

-- 헤어스타일별 이미지 (모두 1장, 이미지 없는 스타일이 없어야 함)
SELECT h.hair_style_code, h.hair_style_name, COUNT(i.image_id) AS images, MAX(i.image_path) AS image_path
FROM hair_styles h LEFT JOIN hair_style_images i ON i.hair_style_code = h.hair_style_code
GROUP BY h.hair_style_code, h.hair_style_name, h.sort_order ORDER BY h.sort_order;

-- 패션 스타일별 세트·아이템 수 (스타일당 세트 5개)
SELECT s.style_code, COUNT(DISTINCT o.set_id) AS sets, COUNT(i.item_id) AS items
FROM styles s LEFT JOIN outfit_sets o ON o.style_code = s.style_code LEFT JOIN outfit_items i ON i.set_id = o.set_id
GROUP BY s.style_code, s.sort_order ORDER BY s.sort_order;

-- 샘플: CASUAL_01 세트 전체
SELECT o.recipe_key, o.full_shot_image_path, i.display_order, i.category, i.item_name, i.item_image_path
FROM outfit_sets o JOIN outfit_items i ON i.set_id = o.set_id
WHERE o.recipe_key = 'CASUAL_01' ORDER BY i.display_order;
