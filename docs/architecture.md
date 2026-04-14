# 아키텍처 설계

## 1. 데이터 책임 분리

- JSON: 단어 원본 데이터
- Firebase Storage: `version.json`, 학년별 JSON, mp3 파일 배포
- 앱 내부 저장소: 내려받은 JSON/mp3 캐시
- Room(SQLite): 정답/오답/풀이시간/복습 통계
- DataStore: 선택 학년, 원격 버전 정보

## 2. 주요 흐름

### 앱 시작

1. 저장된 학년 선택값을 읽음
2. 로컬 JSON 또는 번들 샘플 JSON을 로드
3. `DEFAULT_STORAGE_BASE_URL`이 설정되어 있으면 `version.json` 확인
4. 원격 버전이 더 높으면 변경된 학년별 JSON만 다운로드
5. 화면 상태를 새 데이터로 갱신

### 단어 학습

1. 선택 학년의 단어 목록 로드
2. 단어 카드에서 뜻/예문 표시
3. 단어 음성 재생 시 로컬 캐시가 없으면 다운로드 후 재생
4. 예문 음성도 동일하되, 최초 재생 시점에만 필요 다운로드

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

