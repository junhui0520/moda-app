package com.moda.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.moda.common.enums.BodyType;
import com.moda.common.enums.FaceShape;
import com.moda.common.enums.HairCurl;
import com.moda.common.enums.HairDensity;
import com.moda.common.enums.HairThickness;
import com.moda.recommendation.dto.RecommendationDtos.Adjustment;
import com.moda.recommendation.dto.RecommendationDtos.FashionRecommendation;
import com.moda.recommendation.dto.RecommendationDtos.HairRecommendation;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code docs/recommendation-dataset.csv}(S001~S010, 규칙 엔진 v1.1-draft 정답값)와 추천 엔진 결과가 100% 일치하는지 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
class RecommendationDatasetTest {

    static final Path DATASET = Path.of("..", "docs", "recommendation-dataset.csv");

    @Autowired
    StyleRecommendationService service;

    record Row(Map<String, String> v) {
        String get(String column) {
            return v.get(column);
        }

        @Override
        public String toString() {
            return v.get("sample_id");
        }
    }

    static Stream<Row> rows() throws IOException {
        List<String> lines = Files.readAllLines(DATASET, StandardCharsets.UTF_8).stream().filter(l -> !l.isBlank()).toList();
        String[] header = lines.get(0).split(",", -1);
        return lines.stream().skip(1).map(line -> {
            String[] cells = line.split(",", -1);
            Map<String, String> m = new HashMap<>();
            for (int i = 0; i < header.length; i++) {
                m.put(header[i].strip(), cells[i].strip());
            }
            return new Row(m);
        });
    }

    @Test
    @DisplayName("데이터셋은 S001~S010 10행")
    void datasetHasTenRows() throws IOException {
        assertThat(rows().map(Row::toString)).containsExactly("S001", "S002", "S003", "S004", "S005", "S006", "S007", "S008", "S009", "S010");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    @DisplayName("type_code · hair_rec_1~2 · fashion_rec_1~3 · bmi · rule_version 일치")
    void matchesDataset(Row row) {
        Integer weight = row.get("weight_kg").isEmpty() ? null : Integer.valueOf(row.get("weight_kg"));
        int height = Integer.parseInt(row.get("height_cm"));
        RecommendationInput input = new RecommendationInput(
                FaceShape.fromKey(row.get("face_shape")),
                BodyType.fromKey(row.get("body_type")),
                Integer.parseInt(row.get("age")),
                height,
                weight,
                HairThickness.valueOf(row.get("hair_thickness")),
                HairCurl.valueOf(row.get("hair_curl")),
                HairDensity.valueOf(row.get("hair_density")));

        StyleRecommendation rec = service.recommend(input);

        assertThat(rec.type().typeCode()).as("type_code").isEqualTo(row.get("type_code"));
        assertThat(rec.hairStyles()).extracting(h -> h.code().name()).as("hair_rec_1~2")
                .containsExactly(row.get("hair_rec_1"), row.get("hair_rec_2"));
        assertThat(rec.fashionStyles()).extracting(f -> f.code().name()).as("fashion_rec_1~3")
                .containsExactly(row.get("fashion_rec_1"), row.get("fashion_rec_2"), row.get("fashion_rec_3"));
        assertThat(rec.ruleVersion()).as("rule_version").isEqualTo(row.get("rule_version"));
        if (weight != null) {
            assertThat(BodyMetrics.bmi(height, weight)).as("bmi").isEqualByComparingTo(new BigDecimal(row.get("bmi")));
        }
        // 공통 불변식: rank 1..N, reasons 1~2문장, reasons·reasonCodes 길이 일치
        assertThat(rec.hairStyles()).extracting(HairRecommendation::rank).containsExactly(1, 2);
        assertThat(rec.fashionStyles()).extracting(FashionRecommendation::rank).containsExactly(1, 2, 3);
        rec.hairStyles().forEach(h -> assertThat(h.reasons()).hasSizeBetween(1, 2).hasSameSizeAs(h.reasonCodes()));
        rec.fashionStyles().forEach(f -> assertThat(f.reasons()).hasSizeBetween(1, 2).hasSameSizeAs(f.reasonCodes()));
    }

    @Test
    @DisplayName("S006: api-spec.md ⑤ 예시와 유형·근거 문구·점수 내역이 완전히 같다")
    void s006MatchesApiSpecExample() {
        StyleRecommendation rec = service.recommend(new RecommendationInput(FaceShape.HEART, BodyType.SMALL_INV_TRIANGLE,
                21, 174, 63, HairThickness.THIN, HairCurl.SEMI_CURLY, HairDensity.MEDIUM));

        assertThat(rec.type().typeCode()).isEqualTo("TYPE-08");
        assertThat(rec.type().typeName()).isEqualTo("V라인 프레시");
        assertThat(rec.type().nameConfirmed()).isFalse();
        assertThat(rec.type().headline()).isEqualTo("당신은 V라인 프레시 유형입니다");

        HairRecommendation parted = rec.hairStyles().get(0);
        assertThat(parted.name()).isEqualTo("가르마 스타일");
        assertThat(parted.imageKey()).isEqualTo("hair_parted_style");
        assertThat(parted.score()).isEqualTo(6);
        assertThat(parted.reasons()).containsExactly(
                "가르마로 이마를 사선으로 나눠, 시원한 이마 라인과 갸름한 턱선이 균형 있게 보입니다.",
                "자연스러운 웨이브가 가르마 라인에 입체감을 더해 줍니다.");
        assertThat(parted.reasonCodes()).containsExactly("FACE_HEART_PARTED_STYLE", "ADJ_CURL_SEMI_CURLY_PARTED_STYLE");
        assertThat(parted.scoreBreakdown().base()).isEqualTo(5);
        assertThat(parted.scoreBreakdown().adjustments())
                .containsExactly(new Adjustment("ADJ_CURL_SEMI_CURLY_PARTED_STYLE", "hairCurl", "SEMI_CURLY", 1));

        HairRecommendation shadow = rec.hairStyles().get(1);
        assertThat(shadow.score()).isEqualTo(6);
        assertThat(shadow.reasonCodes()).containsExactly("FACE_HEART_SHADOW_STYLE", "ADJ_THICKNESS_THIN_SHADOW_STYLE"); // 동점 → 굵기 우선
        assertThat(shadow.scoreBreakdown().adjustments()).containsExactly(
                new Adjustment("ADJ_THICKNESS_THIN_SHADOW_STYLE", "hairThickness", "THIN", 1),
                new Adjustment("ADJ_CURL_SEMI_CURLY_SHADOW_STYLE", "hairCurl", "SEMI_CURLY", 1));

        FashionRecommendation dandy = rec.fashionStyles().get(0);
        assertThat(dandy.itemKeywords()).containsExactly("블레이저", "셔츠", "니트 폴로", "슬랙스", "로퍼");
        assertThat(dandy.reasons()).containsExactly("또렷한 어깨선과 슬림한 허리 라인이 테일러드 재킷의 실루엣을 돋보이게 합니다.");
        assertThat(dandy.scoreBreakdown().adjustments()).isEmpty();
        assertThat(rec.fashionStyles()).extracting(FashionRecommendation::score).containsExactly(5, 5, 4);
    }

    @Test
    @DisplayName("api-spec.md ⑦ 예시: 얼굴형 heart → diamond 이면 TYPE-07, 헤어 이유가 다이아몬드형 문구로 바뀜")
    void diamondCorrectionExample() {
        StyleRecommendation rec = service.recommend(new RecommendationInput(FaceShape.DIAMOND, BodyType.SMALL_INV_TRIANGLE,
                21, 174, 63, HairThickness.THIN, HairCurl.SEMI_CURLY, HairDensity.MEDIUM));
        assertThat(rec.type().typeCode()).isEqualTo("TYPE-07");
        assertThat(rec.type().typeName()).isEqualTo("V라인 샤프");
        assertThat(rec.hairStyles()).extracting(h -> h.reasonCodes().get(0))
                .containsExactly("FACE_DIAMOND_PARTED_STYLE", "FACE_DIAMOND_SHADOW_STYLE");
    }

    @Test
    @DisplayName("R-11: BMI 보정은 value=HIDDEN, 코드에도 구간명이 드러나지 않는다 (S003: BMI 26.3)")
    void bmiBandIsHidden() {
        StyleRecommendation rec = service.recommend(new RecommendationInput(FaceShape.RECTANGLE, BodyType.LARGE_RECTANGLE,
                26, 183, 88, HairThickness.THICK, HairCurl.SEMI_CURLY, HairDensity.HIGH));

        List<Adjustment> bmiAdjustments = rec.fashionStyles().stream()
                .flatMap(f -> f.scoreBreakdown().adjustments().stream())
                .filter(a -> a.factor().equals("bmi"))
                .toList();
        assertThat(bmiAdjustments).isNotEmpty().allSatisfy(a -> {
            assertThat(a.value()).isEqualTo("HIDDEN");
            assertThat(a.code()).startsWith("ADJ_BMI_HIDDEN_");
        });
        List<String> allCodes = rec.fashionStyles().stream()
                .flatMap(f -> Stream.concat(f.reasonCodes().stream(), f.scoreBreakdown().adjustments().stream().map(Adjustment::code)))
                .toList();
        assertThat(allCodes).noneMatch(c -> Arrays.stream(new String[]{"BMI_LOW", "BMI_MID", "BMI_HIGH", "BMI_VERY_HIGH"})
                .anyMatch(c::contains));
        // AMEKAJI 7점: 2번째 근거는 BMI(+1)·키(+1) 동점 → BMI 우선, 코드는 마스킹, 문구는 시드 문구
        FashionRecommendation amekaji = rec.fashionStyles().get(0);
        assertThat(amekaji.code().name()).isEqualTo("AMEKAJI");
        assertThat(amekaji.score()).isEqualTo(7);
        assertThat(amekaji.reasonCodes()).containsExactly("BODY_LARGE_RECTANGLE_AMEKAJI", "ADJ_BMI_HIDDEN_AMEKAJI");
        assertThat(amekaji.reasons().get(1)).isEqualTo("탄탄한 소재의 워크웨어가 체격과 어우러져 멋스러운 실루엣을 완성합니다.");
    }

    @Test
    @DisplayName("몸무게 미입력이면 BMI 보정을 생략한다 (키·나이 보정만)")
    void noWeightSkipsBmi() {
        StyleRecommendation rec = service.recommend(new RecommendationInput(FaceShape.RECTANGLE, BodyType.LARGE_RECTANGLE,
                26, 183, null, HairThickness.THICK, HairCurl.SEMI_CURLY, HairDensity.HIGH));
        assertThat(rec.fashionStyles()).flatExtracting(f -> f.scoreBreakdown().adjustments())
                .extracting(Adjustment::factor).doesNotContain("bmi");
    }

    @Test
    @DisplayName("기본 점수 3점 이하 조합은 일반 문구(HAIR_GENERIC_*)를 사용한다 (S005: diamond × UP_STYLE base 3)")
    void genericReasonForLowBase() {
        StyleRecommendation rec = service.recommend(new RecommendationInput(FaceShape.DIAMOND, BodyType.STANDARD,
                31, 177, 74, HairThickness.THICK, HairCurl.STRAIGHT, HairDensity.HIGH));
        HairRecommendation up = rec.hairStyles().get(1);
        assertThat(up.code().name()).isEqualTo("UP_STYLE");
        assertThat(up.scoreBreakdown().base()).isEqualTo(3);
        // THICK(+1)·HIGH(+1) 동점 → 굵기 우선
        assertThat(up.reasonCodes()).containsExactly("HAIR_GENERIC_UP_STYLE", "ADJ_THICKNESS_THICK_UP_STYLE");
    }

    @Test
    @DisplayName("BMI 구간 경계: 소수 첫째 자리 반올림 후 판정 (18.5 → MID, 18.4 → LOW)")
    void bmiBoundaries() {
        assertThat(BodyMetrics.bmi(186, 64)).isEqualByComparingTo("18.5");
        assertThat(BodyMetrics.bmiBand(186, 64)).hasValue(com.moda.common.enums.BmiBand.BMI_MID);
        assertThat(BodyMetrics.bmiBand(165, 50)).hasValue(com.moda.common.enums.BmiBand.BMI_LOW);
        assertThat(BodyMetrics.bmiBand(177, 74)).hasValue(com.moda.common.enums.BmiBand.BMI_HIGH);
        assertThat(BodyMetrics.bmiBand(183, 88)).hasValue(com.moda.common.enums.BmiBand.BMI_VERY_HIGH);
        assertThat(BodyMetrics.bmiBand(174, null)).isEmpty();
        assertThat(BodyMetrics.heightBand(169)).hasToString("HEIGHT_SHORT");
        assertThat(BodyMetrics.heightBand(181)).hasToString("HEIGHT_MID");
        assertThat(BodyMetrics.heightBand(182)).hasToString("HEIGHT_TALL");
        assertThat(BodyMetrics.ageBand(22)).hasToString("AGE_U22");
        assertThat(BodyMetrics.ageBand(29)).hasToString("AGE_23_29");
        assertThat(BodyMetrics.ageBand(39)).hasToString("AGE_30S");
        assertThat(BodyMetrics.ageBand(40)).hasToString("AGE_40P");
    }
}
