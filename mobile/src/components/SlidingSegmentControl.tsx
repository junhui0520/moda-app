// src/components/SlidingSegmentControl.tsx — iOS 네이티브 스타일 부드러운 슬라이딩 세그먼트 컨트롤

import React, { useEffect, useRef, useState } from 'react';
import {
  Animated,
  LayoutChangeEvent,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { colors } from '../theme/theme';

export interface SegmentOption<T extends string> {
  value: T;
  label: string;
}

interface SlidingSegmentControlProps<T extends string> {
  options: SegmentOption<T>[];
  value: T;
  onChange: (next: T) => void;
}

const TRACK_PADDING = 3;

export function SlidingSegmentControl<T extends string>({
  options,
  value,
  onChange,
}: SlidingSegmentControlProps<T>) {
  const [trackWidth, setTrackWidth] = useState(0);
  const selectedIndex = Math.max(
    0,
    options.findIndex((opt) => opt.value === value),
  );

  const innerWidth = Math.max(0, trackWidth - TRACK_PADDING * 2);
  const itemWidth = options.length > 0 ? innerWidth / options.length : 0;

  const translateX = useRef(new Animated.Value(0)).current;
  const initializedRef = useRef(false);

  useEffect(() => {
    if (itemWidth <= 0) return;
    const targetX = selectedIndex * itemWidth;

    if (!initializedRef.current) {
      translateX.setValue(targetX);
      initializedRef.current = true;
      return;
    }

    Animated.spring(translateX, {
      toValue: targetX,
      stiffness: 280,
      damping: 26,
      mass: 0.85,
      useNativeDriver: true,
    }).start();
  }, [selectedIndex, itemWidth, translateX]);

  const handleLayout = (e: LayoutChangeEvent) => {
    const width = e.nativeEvent.layout.width;
    if (width !== trackWidth) {
      setTrackWidth(width);
    }
  };

  return (
    <View style={styles.track} onLayout={handleLayout}>
      {itemWidth > 0 ? (
        <Animated.View
          pointerEvents="none"
          style={[
            styles.thumb,
            {
              width: itemWidth,
              transform: [{ translateX }],
            },
          ]}
        />
      ) : null}

      {options.map((opt) => {
        const active = opt.value === value;
        return (
          <Pressable
            key={opt.value}
            style={styles.item}
            onPress={() => onChange(opt.value)}
          >
            <Text style={[styles.label, active && styles.labelActive]}>{opt.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  track: {
    flexDirection: 'row',
    position: 'relative',
    backgroundColor: colors.surfaceMuted,
    borderRadius: 11,
    padding: TRACK_PADDING,
  },
  thumb: {
    position: 'absolute',
    top: TRACK_PADDING,
    bottom: TRACK_PADDING,
    left: TRACK_PADDING,
    backgroundColor: colors.surface,
    borderRadius: 8.5,
    shadowColor: '#000000',
    shadowOpacity: 0.08,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 1 },
    elevation: 2,
  },
  item: {
    flex: 1,
    paddingVertical: 10,
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1,
  },
  label: {
    fontSize: 13.5,
    fontWeight: '500',
    color: colors.textSecondary,
  },
  labelActive: {
    fontWeight: '700',
    color: colors.text,
  },
});
