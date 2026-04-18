# 아키텍처 설계

## 1. 데이터 책임 분리

- JSON: 단어 원본 데이터
- Firebase Storage: `version.json`, 학년별 JSON, mp3 파일 배포
- 앱 내부 저장소: 내려받은 JSON/mp3 캐시
- Room(SQLite): 정답/오답/풀이시간/복습 통계
- DataStore: 선택 학년, 원격 버전 정보

## 2. 주요 흐름

### 앱 시작

1. 저장된 닉네임, 학년, 학습 개수, 온보딩 완료 여부를 읽음
2. 온보딩이 안 끝났으면 `닉네임 -> 학년 -> 학습 개수` 순서로 진행
3. 온보딩이 끝났으면 로컬 JSON 또는 번들 샘플 JSON을 로드
4. `DEFAULT_STORAGE_BASE_URL`이 설정되어 있으면 `version.json` 확인
5. 원격 버전이 더 높으면 변경된 학년별 JSON만 다운로드
6. 화면 상태를 새 데이터로 갱신

### 단어 학습

1. 선택 학년과 학습 개수 기준으로 학습 덱 생성
2. 우선순위: 틀린 단어 -> 오래 안 본 단어 -> 시간이 오래 걸린 단어 -> 일반 단어
3. 단어 카드에는 단어와 발음 버튼만 먼저 보임
4. 사용자가 먼저 뜻을 떠올림
5. 카드 하단 영역을 드래그해야 뜻/예문이 열림
6. 열기 전에는 다음 카드 스와이프 불가
7. `알겠다 / 모르겠다` 선택 시 `word_stat`, `quiz_history` 기록 갱신
8. 단어 음성/예문 음성 재생 시 로컬 캐시가 없으면 다운로드 후 재생

### 퀴즈

- 단어 보고 뜻 맞추기
- 뜻 보고 단어 맞추기
- 예문 빈칸 맞추기

각 문제 제출 시:

1. `quiz_history`에 기록
2. `word_stat` 누적값 갱신
3. 오답 또는 오래 걸림 기준이면 `need_review = true`

### 복습

- 오답 횟수 많은 단어
- 오래 안 본 단어
- 평균 풀이 시간이 긴 단어
- `need_review = true`인 단어

이 기준을 합쳐 복습 카드로 노출합니다.

## 3. 패키지 구조

```text
app/
  src/main/java/com/gwon/vocablearning/
    app/
    data/local/
    data/preferences/
    data/remote/
    data/repository/
    domain/model/
    domain/service/
    ui/
    ui/theme/
    ui/viewmodel/
```

## 4. SQLite 스키마

### word_stat

- `word_id`
- `total_solved_count`
- `correct_count`
- `wrong_count`
- `total_elapsed_ms`
- `average_elapsed_ms`
- `last_solved_at`
- `need_review`

### quiz_history

- `id`
- `word_id`
- `quiz_type`
- `is_correct`
- `elapsed_ms`
- `solved_at`

## 5. 확장 전략

- `Language` enum과 파일 경로 규칙을 통해 일본어/중국어 추가 가능
- JSON 스키마는 언어 공통으로 유지
- 화면은 학년/언어 선택만 늘리면 재사용 가능
- 추후 문제 유형 추가 시 `QuizFactory`만 확장하면 됨
