package com.moda.image;

import com.moda.common.enums.HairStyleCode;
import com.moda.global.response.ApiResponse;
import com.moda.seed.domain.FashionStyle;
import com.moda.seed.domain.HairStyle;
import com.moda.seed.repository.FashionStyleRepository;
import com.moda.seed.repository.HairStyleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 추천·챗봇의 {@code imageKey} → 이미지 경로. 데이터는 sql/ 로 적재한 hair_style_images·outfit_sets·outfit_items,
 * 파일은 {@code WebConfig}가 {@code /images/hair/**}, {@code /images/style/**} 로 서빙합니다. 앱은 {@code BASE_URL + url}로 표시.
 */
@RestController
@Tag(name = "StyleImage", description = "스타일 이미지 경로")
public class StyleImageController {

    private static final Logger log = LoggerFactory.getLogger(StyleImageController.class);

    /** 백엔드 헤어 카탈로그(5종) → sql hair_categories. 카테고리 안 첫 스타일(sort_order)의 대표 이미지를 씀. */
    static final Map<HairStyleCode, String> HAIR_CATEGORY = Map.of(
            HairStyleCode.UP_STYLE, "SLICKED",
            HairStyleCode.PARTED_STYLE, "PARTED",
            HairStyleCode.DANDY_STYLE, "DANDY",
            HairStyleCode.CROP_STYLE, "CROP",
            HairStyleCode.SHADOW_STYLE, "SHADOW_PERM");

    private final JdbcTemplate jdbc;
    private final HairStyleRepository hairStyles;
    private final FashionStyleRepository fashionStyles;

    public StyleImageController(JdbcTemplate jdbc, HairStyleRepository hairStyles, FashionStyleRepository fashionStyles) {
        this.jdbc = jdbc;
        this.hairStyles = hairStyles;
        this.fashionStyles = fashionStyles;
    }

    /** {@code url}: 큰 사진(헤어 대표 / 코디 full_shot), {@code items}: 작은 아이템 사진 (헤어는 []). */
    public record StyleImages(String url, List<ItemImage> items) {
    }

    public record ItemImage(String label, String url) {
    }

    @Operation(summary = "스타일 이미지 경로", description = "imageKey → {url, items[]}. 이미지가 없는 스타일은 키가 없음 (앱은 텍스트로 대체)")
    @GetMapping("/api/v1/style-images")
    public ApiResponse<Map<String, StyleImages>> styleImages() {
        Map<String, StyleImages> result = new LinkedHashMap<>();
        try {
            for (HairStyle h : hairStyles.findAllByOrderBySortOrderAsc()) {
                jdbc.queryForList("""
                                SELECT i.image_path FROM hair_style_images i
                                JOIN hair_styles s ON s.hair_style_code = i.hair_style_code
                                WHERE s.hair_category_code = ? AND s.is_active AND i.is_active AND i.is_primary
                                ORDER BY s.sort_order LIMIT 1""", String.class, HAIR_CATEGORY.get(h.getStyleCode()))
                        .stream().findFirst()
                        .ifPresent(path -> result.put(h.getImageKey(), new StyleImages("/images/hair/" + path, List.of())));
            }
            for (FashionStyle f : fashionStyles.findAllByOrderBySortOrderAsc()) {
                // shortcut: 스타일당 코디 세트 5개 중 첫 세트만 노출, 세트 넘겨보기가 필요해지면 세트 목록으로 확장
                jdbc.queryForList("SELECT set_id, full_shot_image_path FROM outfit_sets WHERE style_code = ? AND is_active ORDER BY recipe_key LIMIT 1",
                                f.getCode())
                        .stream().findFirst()
                        .ifPresent(set -> result.put(f.getImageKey(), new StyleImages(
                                "/images/style/" + set.get("full_shot_image_path"),
                                jdbc.query("SELECT item_name, item_image_path FROM outfit_items WHERE set_id = ? ORDER BY display_order",
                                        (rs, n) -> new ItemImage(rs.getString(1), "/images/style/" + rs.getString(2)),
                                        set.get("set_id")))));
            }
        } catch (DataAccessException e) {
            // sql/ 미적재(MySQL에 아직 실행 안 함 등): 앱이 텍스트로 대체하도록 빈 결과
            log.warn("스타일 이미지 테이블 조회 실패 — sql/ 적재 여부 확인: {}", e.getMessage());
            return ApiResponse.success(Map.of());
        }
        return ApiResponse.success(result);
    }
}
