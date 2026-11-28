import React from 'react';
import { StyleSheet, View } from 'react-native';
import Svg, { Line, Path } from 'react-native-svg';

interface Props {
  active?: boolean;
}

/**
 * 전신 촬영용 사람 실루엣 가이드 (다리는 모으고, 팔은 몸통에서 살짝 벌린 정면 자세)
 * - TODO(AI팀 김민건 확인 후 조정): 다리 벌림 여부·팔 각도 확정 시 아래 Path 좌표만 미세 조정
 */
export function BodySilhouetteGuide({ active = false }: Props) {
  const strokeColor = active ? '#FFFFFF' : 'rgba(255, 255, 255, 0.9)';
  const strokeWidth = active ? '2.8' : '2.3';
  const strokeDasharray = active ? undefined : '7 5';

  return (
    <View style={styles.container} pointerEvents="none">
      <Svg
        width="100%"
        height="100%"
        viewBox="0 0 220 440"
        preserveAspectRatio="xMidYMid meet"
        fill="none"
      >
        {/* 머리 끝 / 발 끝 기준 수평 가이드선 */}
        <Line
          x1="78"
          y1="14"
          x2="142"
          y2="14"
          stroke={active ? '#FFFFFF' : 'rgba(255, 255, 255, 0.55)'}
          strokeWidth="1.5"
          strokeDasharray="4 4"
        />
        <Line
          x1="68"
          y1="430"
          x2="152"
          y2="430"
          stroke={active ? '#FFFFFF' : 'rgba(255, 255, 255, 0.55)'}
          strokeWidth="1.5"
          strokeDasharray="4 4"
        />

        {/* 사람 실루엣 외곽선 (정면 · 팔 벌림 · 다리 모음) */}
        <Path
          d="
            M 110 18
            C 125 18, 133 30, 133 46
            C 133 60, 125 70, 121 73
            L 121 85
            Q 121 91, 136 93
            Q 154 95, 160 104
            L 179 164
            L 194 224
            Q 198 238, 190 242
            Q 182 244, 178 232
            L 163 174
            L 148 134
            L 144 196
            Q 147 216, 146 234
            L 143 292
            L 138 334
            Q 140 364, 135 408
            L 141 420
            Q 143 426, 132 426
            L 116 426
            Q 112 426, 112 418
            L 113 334
            L 112 252
            Q 110 248, 108 252
            L 107 334
            L 108 418
            Q 108 426, 104 426
            L 88 426
            Q 77 426, 79 420
            L 85 408
            Q 80 364, 82 334
            L 77 292
            L 74 234
            Q 73 216, 76 196
            L 72 134
            L 57 174
            L 42 232
            Q 38 244, 30 242
            Q 22 238, 26 224
            L 41 164
            L 60 104
            Q 66 95, 84 93
            Q 99 91, 99 85
            L 99 73
            C 95 70, 87 60, 87 46
            C 87 30, 95 18, 110 18
            Z
          "
          stroke={strokeColor}
          strokeWidth={strokeWidth}
          strokeDasharray={strokeDasharray}
          strokeLinecap="round"
          strokeLinejoin="round"
          fill={active ? 'rgba(255, 255, 255, 0.08)' : 'rgba(255, 255, 255, 0.04)'}
        />
      </Svg>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    width: '78%',
    height: '82%',
    alignItems: 'center',
    justifyContent: 'center',
  },
});
