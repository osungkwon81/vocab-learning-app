# 외국어 학습 앱

아이들을 위한 시험 대비용 외국어 학습 안드로이드 앱의 초기 구현입니다. 회원가입은 없고 최초 1회 닉네임만 입력하면 되며, 카드 기반 단어 학습과 간격 반복 복습 흐름을 중심으로 구성했습니다.

## 현재 포함된 것

- `Android + Jetpack Compose` 기반 앱 골격
- 최초 닉네임 입력, 학년 선택, 학습 개수 선택 온보딩
- 카드 기반 단어 학습 화면
- `Room` 기반 `word_stat`, `quiz_history` 저장소
- `DataStore` 기반 선택 학년 및 데이터 버전 저장
- `DataStore` 기반 닉네임, 학습 개수, 온보딩 완료 상태 저장
- `version.json` 비교 후 학년별 JSON을 내려받는 동기화 구조
- 단어 음성/예문 음성의 로컬 캐시 준비 구조
- 영어 6개 학년 샘플 JSON

## 현재 학습 UX

1. 단어만 먼저 본다.
2. 뜻을 스스로 떠올린다.
3. 카드 하단 영역을 드래그해서 뜻/예문을 연다.
4. `알겠다` 또는 `모르겠다`를 선택한다.
5. 그 결과를 SQLite에 저장한다.
6. 다음 카드로 스와이프한다.

뜻을 열기 전에는 다음 카드로 넘어갈 수 없도록 막아 두었습니다.

## 아키텍처 요약

- 앱 UI: Compose
- 로컬 통계 저장: Room(SQLite)
- 설정/버전 저장: DataStore
- 원본 단어 데이터: JSON 파일
- 원격 배포: Firebase Storage 같은 정적 파일 저장소

상세 설계는 [docs/architecture.md](/Users/gwon-oseong/Desktop/프로젝트/vocab-learning-app/docs/architecture.md)에서 확인할 수 있습니다.

## 원격 저장소 구조 예시

```text
version.json
catalog/en/english_middle1.json
catalog/en/english_middle2.json
catalog/en/english_middle3.json
catalog/en/english_high1.json
catalog/en/english_high2.json
catalog/en/english_high3.json
audio/en/words/1001.mp3
audio/en/sentences/1001.mp3
```

`version.json`의 `files` 키는 `english_middle1` 같은 파일 키를 기준으로 버전을 관리합니다.

## Firebase Storage URL 설정

현재 `BuildConfig.DEFAULT_STORAGE_BASE_URL` 값은 비어 있습니다. 실제 사용 시 `app/build.gradle.kts`에서 Firebase Storage 공개 URL 또는 CDN URL을 넣으면 앱 실행 시 원격 데이터 갱신을 시도합니다.

예시:

```kotlin
buildConfigField(
    "String",
    "DEFAULT_STORAGE_BASE_URL",
    "\"https://your-storage.example.com\"",
)
```

Firebase Storage REST 기본 URL도 바로 지원합니다.

예시:

```kotlin
buildConfigField(
    "String",
    "DEFAULT_STORAGE_BASE_URL",
    "\"https://firebasestorage.googleapis.com/v0/b/vocab-learning-ff783.firebasestorage.app/o\"",
)
```

이 경우 앱은 내부적으로 다음 형태로 요청합니다.

```text
https://firebasestorage.googleapis.com/v0/b/vocab-learning-ff783.firebasestorage.app/o/version.json?alt=media
https://firebasestorage.googleapis.com/v0/b/vocab-learning-ff783.firebasestorage.app/o/catalog%2Fen%2Fenglish_middle3.json?alt=media
```

단, 원격 동기화에는 여전히 `version.json`이 필요합니다.

## 로컬 실행

```bash
./gradlew test
```

Android Studio에서 열면 바로 Gradle sync 후 실행할 수 있도록 wrapper와 SDK 경로 설정도 정리했습니다.
