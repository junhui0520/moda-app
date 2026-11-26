import { Asset } from 'expo-asset';
import { Platform } from 'react-native';
import { analyze, createSession, getResult, submitProfile, type UploadImage } from './api/modaApi';
import type { BodyTypeKey, FaceShapeKey, ResultData } from './api/types';

// shortcut: 기본 정보 입력·얼굴·체형 분석 화면 대신 랜덤 값 사용 (서버 moda.ai.mode=mock 필요).
// 실제 입력·촬영 화면이 생기면 이 파일 대신 그 화면에서 같은 API를 호출하면 됨.

const FACE_SHAPES: FaceShapeKey[] = ['diamond', 'heart', 'oval', 'rectangle', 'round', 'long'];
const BODY_TYPES: BodyTypeKey[] = ['standard', 'small_inv_triangle', 'large_rectangle', 'rectangle'];

const pick = <T,>(xs: readonly T[]): T => xs[Math.floor(Math.random() * xs.length)];
const int = (min: number, max: number) => min + Math.floor(Math.random() * (max - min + 1));

/** Mock 서버는 사진 내용을 보지 않으므로 앱 아이콘을 사진 대신 보낸다 */
async function dummyImage(): Promise<UploadImage> {
  const asset = await Asset.fromModule(require('../assets/icon.png')).downloadAsync();
  if (Platform.OS === 'web') return (await fetch(asset.uri)).blob();
  return { uri: asset.localUri ?? asset.uri, name: 'photo.png', type: 'image/png' };
}

export async function runRandomDemo(): Promise<{ sessionId: string; result: ResultData }> {
  const { sessionId } = await createSession();
  await submitProfile(sessionId, {
    age: int(18, 35),
    heightCm: int(160, 190),
    weightKg: int(50, 95),
    hairThickness: pick(['THIN', 'MEDIUM', 'THICK'] as const),
    hairCurl: pick(['STRAIGHT', 'SEMI_CURLY', 'CURLY'] as const),
    hairDensity: pick(['LOW', 'MEDIUM', 'HIGH'] as const),
  });
  const image = await dummyImage();
  await analyze(sessionId, 'face', image, { 'X-Mock-Face-Shape': pick(FACE_SHAPES) });
  await analyze(sessionId, 'body', image, { 'X-Mock-Body-Type': pick(BODY_TYPES) });
  return { sessionId, result: await getResult(sessionId) };
}
