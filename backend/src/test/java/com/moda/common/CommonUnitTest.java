package com.moda.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moda.common.enums.BodyType;
import com.moda.common.enums.FaceShape;
import com.moda.common.enums.TypeCodes;
import com.moda.common.jpa.StringListConverter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommonUnitTest {

    @Test
    @DisplayName("typeCode = TYPE-{bodyIndex × 6 + faceIndex + 1}, 24종 모두 서로 다름")
    void typeCodeFormula() {
        assertThat(TypeCodes.of(BodyType.STANDARD, FaceShape.DIAMOND)).isEqualTo("TYPE-01");
        assertThat(TypeCodes.of(BodyType.SMALL_INV_TRIANGLE, FaceShape.HEART)).isEqualTo("TYPE-08");
        assertThat(TypeCodes.of(BodyType.SMALL_INV_TRIANGLE, FaceShape.DIAMOND)).isEqualTo("TYPE-07");
        assertThat(TypeCodes.of(BodyType.LARGE_RECTANGLE, FaceShape.ROUND)).isEqualTo("TYPE-17");
        assertThat(TypeCodes.of(BodyType.RECTANGLE, FaceShape.LONG)).isEqualTo("TYPE-24");

        Set<String> all = new HashSet<>();
        for (BodyType b : BodyType.values()) {
            for (FaceShape f : FaceShape.values()) {
                all.add(TypeCodes.of(b, f));
            }
        }
        assertThat(all).hasSize(24);
    }

    @Test
    @DisplayName("얼굴형·체형 Key 조회 (소문자), 알 수 없는 Key는 예외")
    void keyedEnums() {
        assertThat(FaceShape.fromKey("heart")).isEqualTo(FaceShape.HEART);
        assertThat(BodyType.fromKey("small_inv_triangle")).isEqualTo(BodyType.SMALL_INV_TRIANGLE);
        assertThat(FaceShape.findByKey("HEART")).isEmpty();
        assertThatThrownBy(() -> BodyType.fromKey("triangle")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("List<String> ↔ JSON 문자열 컬럼 변환 (한글·순서 보존)")
    void stringListConverterRoundTrip() {
        StringListConverter converter = new StringListConverter();
        List<String> keywords = List.of("블레이저", "셔츠", "니트 폴로", "슬랙스", "로퍼");
        String column = converter.convertToDatabaseColumn(keywords);
        assertThat(column).isEqualTo("[\"블레이저\",\"셔츠\",\"니트 폴로\",\"슬랙스\",\"로퍼\"]");
        assertThat(converter.convertToEntityAttribute(column)).containsExactlyElementsOf(keywords);
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }
}
