# Vocabulary JSON Generator

텍스트 단어장을 JSON으로 변환하고, 필요하면 Firebase Storage에 업로드합니다.

## Generate only

```bash
python3 tools/vocabulary/generate_all.py
python3 tools/vocabulary/generate_all.py --grade middle3
python3 tools/vocabulary/generate_all.py --grade high1
python3 tools/vocabulary/generate_all.py --grade high2
python3 tools/vocabulary/generate_all.py --grade high3
python3 tools/vocabulary/generate_english_middle3.py
python3 tools/vocabulary/generate_english_high1.py
python3 tools/vocabulary/generate_english_high2.py
python3 tools/vocabulary/generate_english_high3.py
```

생성되는 단어장 JSON은 앱의 `WordDto`가 읽는 `exampleSentence`, `exampleTranslation` 필드를 포함합니다.
기본 버전은 현재 원격 매니페스트에 맞춘 `4`입니다.

로컬 앱 assets도 같이 갱신하려면 `--update-assets`를 붙입니다.

```bash
python3 tools/vocabulary/generate_all.py --update-assets
```

단어 mp3만 로컬에 생성하려면 `--generate-word-audio`를 붙입니다.
오디오 파일은 기본적으로 `tools/vocabulary/generated_audio/words`에 저장되고, 파일명은 단어를 소문자 slug로 바꾼 값입니다.

```bash
python3 tools/vocabulary/generate_all.py --grade middle3 --generate-word-audio
```

## Generate and upload

먼저 Firebase Admin SDK와 gTTS를 설치합니다.

```bash
pip install -r tools/vocabulary/requirements.txt
```

Firebase service account JSON 경로와 Storage bucket 이름을 환경변수로 설정합니다.

```bash
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/firebase-service-account.json"
export FIREBASE_STORAGE_BUCKET="vocab-learning-ff783.firebasestorage.app"
```

`GOOGLE_APPLICATION_CREDENTIALS`를 생략하면 `/Users/gwon-oseong/key/vocab-learning-firebase-adminsdk.json` 파일이 있을 때 자동으로 사용합니다.
`FIREBASE_STORAGE_BUCKET`을 생략하면 `vocab-learning-ff783.firebasestorage.app`을 기본값으로 사용합니다.

업로드까지 실행합니다.

```bash
python3 tools/vocabulary/generate_all.py --upload --upload-manifest
```

`--version`을 생략하면 기존 버전에서 자동으로 1 증가합니다. 수동으로 지정하고 싶을 때만 `--version 5`처럼 넣으면 됩니다.
출처 책/범위를 `sources[0].book`에 넣고 싶으면 `--source-book "학교"`처럼 지정합니다.
삭제할 단어가 있으면 `--delete-list delete_words_high1.txt`처럼 한 줄에 단어 하나씩 적은 파일을 넘깁니다.

단어 mp3를 생성해서 `audio/en/words/{word}.mp3` 경로로 업로드하고, JSON의 `wordAudioUrl`에 URL을 넣으려면 `--upload-word-audio`를 함께 사용합니다.
`exampleAudioUrl`은 아직 생성하지 않고 빈 문자열로 둡니다.
업로드 시에는 기존 원격 JSON을 먼저 읽고, 이미 있는 단어는 유지한 뒤 새 단어만 추가해서 다시 업로드합니다.
기본적으로 오디오 처리는 이번 입력 txt에 들어 있는 단어만 대상으로 합니다.

```bash
python3 tools/vocabulary/generate_all.py --grade middle3 --upload-word-audio --upload --upload-manifest
```

```bash
python3 tools/vocabulary/generate_all.py --grade middle3 --source-book "학교" --upload --upload-manifest
```

```bash
python3 tools/vocabulary/generate_all.py --grade high1 --delete-list delete_words_high1.txt --upload --upload-manifest
```

기존 원격 JSON에 있던 단어들까지 다시 훑어서 오디오를 검수하거나 재업로드하려면 `--check-existing-word-audio`를 함께 붙입니다.

```bash
python3 tools/vocabulary/generate_all.py --grade middle3 --upload-word-audio --check-existing-word-audio --upload --upload-manifest
```

기존 단어도 입력 txt 기준으로 덮어쓰고 싶다면 `--update-existing`를 붙입니다.
이 옵션은 단어, 품사, 뜻, 예문, 출처 같은 JSON 필드는 갱신하지만 `wordAudioUrl`과 `exampleAudioUrl`은 유지합니다.

```bash
python3 tools/vocabulary/generate_all.py --grade middle3 --update-existing --upload --upload-manifest
```

기존 원격 JSON을 무시하고 완전히 새로 교체해야 할 때만 `--replace-remote`를 붙입니다.

```bash
python3 tools/vocabulary/generate_all.py --grade middle3 --replace-remote --upload-word-audio --upload --upload-manifest
```

기존 로컬 mp3를 무시하고 단어 음성을 다시 만들려면 `--force-word-audio`를 함께 붙입니다.

```bash
python3 tools/vocabulary/generate_all.py --grade middle3 --upload-word-audio --force-word-audio --upload --upload-manifest
```

중3만 생성/업로드하려면:

```bash
python3 tools/vocabulary/generate_all.py --grade middle3 --upload --upload-manifest
```

고1만 생성/업로드하려면:

```bash
python3 tools/vocabulary/generate_all.py --grade high1 --upload --upload-manifest
```

고2/고3도 같은 방식입니다.

```bash
python3 tools/vocabulary/generate_all.py --grade high2 --upload --upload-manifest
python3 tools/vocabulary/generate_all.py --grade high3 --upload --upload-manifest
```

기본 업로드 경로는 앱의 원격 경로와 같은 `catalog/en/english_middle3.json`, `catalog/en/english_high1.json`, `catalog/en/english_high2.json`, `catalog/en/english_high3.json`입니다.
`--upload-manifest`를 붙이면 루트 `version.json`도 업로드합니다. `version.json`은 앱이 원격 버전을 판단하는 기준이라, 특정 학년만 생성하더라도 중3/고1/고2/고3 항목을 함께 포함합니다.

현재 생성되는 `version.json` 형식:

```json
{
  "version": 4,
  "files": [
    {
      "path": "catalog/en/english_middle3.json",
      "version": 4
    },
    {
      "path": "catalog/en/english_high1.json",
      "version": 4
    },
    {
      "path": "catalog/en/english_high2.json",
      "version": 4
    },
    {
      "path": "catalog/en/english_high3.json",
      "version": 4
    }
  ]
}
```

다른 경로로 올리려면 `--storage-path`를 사용합니다.

```bash
python3 tools/vocabulary/generate_english_middle3.py --upload --storage-path catalog/en/english_middle3.json
```
