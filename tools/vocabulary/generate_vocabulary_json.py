import argparse
import copy
import json
import os
import re
import shutil
from pathlib import Path
from urllib.parse import quote


BASE_DIR = Path(__file__).resolve().parent
PROJECT_DIR = BASE_DIR.parents[1]
ASSET_DATA_DIR = PROJECT_DIR / "app" / "src" / "main" / "assets" / "data"
DEFAULT_STORAGE_PREFIX = "catalog/en"
DEFAULT_AUDIO_STORAGE_PREFIX = "audio/en/words"
DEFAULT_AUDIO_OUTPUT_DIR = BASE_DIR / "generated_audio" / "words"
DEFAULT_BUCKET = "vocab-learning-ff783.firebasestorage.app"
DEFAULT_CREDENTIALS_PATH = Path.home() / "key" / "vocab-learning-firebase-adminsdk.json"
WORD_AUDIO_SLUGS = {}


def normalize(word):
    return re.sub(r"\s+", " ", word.strip().lower())


def audio_slug(word):
    value = word.strip().lower()
    value = re.sub(r"[^a-z0-9]+", "-", value)
    value = re.sub(r"-+", "-", value).strip("-")
    if not value:
        raise ValueError(f"오디오 파일명을 만들 수 없는 단어입니다: {word!r}")
    return value


def to_list(value):
    return [v.strip() for v in value.split(",") if v.strip()] if value else []


def max_word_id(data):
    word_ids = [word.get("wordId", 0) for word in data.get("words", [])]
    return max(word_ids, default=0)


def firebase_download_url(destination_path):
    bucket_name = os.environ.get("FIREBASE_STORAGE_BUCKET", DEFAULT_BUCKET)
    encoded_path = quote(destination_path, safe="")
    return f"https://firebasestorage.googleapis.com/v0/b/{bucket_name}/o/{encoded_path}?alt=media"


def create_tts_mp3(text, output_path):
    try:
        from gtts import gTTS
    except ImportError as exc:
        raise RuntimeError(
            "gTTS 패키지가 필요합니다. "
            "`pip install -r tools/vocabulary/requirements.txt` 실행 후 다시 시도하세요."
        ) from exc

    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    gTTS(text=text, lang="en").save(str(output_path))
    print(f"오디오 생성 완료: {output_path}")


def prepare_word_audio(
    word,
    audio_output_dir=DEFAULT_AUDIO_OUTPUT_DIR,
    audio_storage_prefix=DEFAULT_AUDIO_STORAGE_PREFIX,
    force_word_audio=False,
):
    slug = audio_slug(word)
    local_file = Path(audio_output_dir) / f"{slug}.mp3"
    storage_path = f"{audio_storage_prefix.rstrip('/')}/{slug}.mp3"

    if local_file.exists() and local_file.stat().st_size > 0 and not force_word_audio:
        print(f"오디오 생성 건너뜀: {local_file}")
    else:
        create_tts_mp3(word, local_file)

    return local_file, storage_path


def update_word_audio(item, audio_output_dir, audio_storage_prefix, upload_word_audio, force_word_audio):
    word = item.get("word", "").strip()
    if not word:
        return

    local_audio_file, audio_storage_path = prepare_word_audio(
        word,
        audio_output_dir=audio_output_dir,
        audio_storage_prefix=audio_storage_prefix,
        force_word_audio=force_word_audio,
    )
    if upload_word_audio:
        upload_to_firebase_storage(
            local_audio_file,
            audio_storage_path,
            cache_control="public, max-age=31536000",
            content_type="audio/mpeg",
        )
        item["wordAudioUrl"] = firebase_download_url(audio_storage_path)


def load_existing_catalog_from_firebase(destination_path):
    blob = firebase_blob(destination_path)
    if not blob.exists():
        print(f"원격 JSON 없음, 새로 생성: gs://{blob.bucket.name}/{destination_path}")
        return None

    payload = blob.download_as_bytes()
    data = json.loads(payload.decode("utf-8"))
    print(f"원격 JSON 로드 완료: gs://{blob.bucket.name}/{destination_path}")
    return data


def generate(
    input_file,
    output_file,
    grade,
    version,
    word_id_start=1001,
    generate_word_audio=False,
    upload_word_audio=False,
    audio_output_dir=DEFAULT_AUDIO_OUTPUT_DIR,
    audio_storage_prefix=DEFAULT_AUDIO_STORAGE_PREFIX,
    existing_data=None,
    force_word_audio=False,
    update_existing=False,
):
    input_file = Path(input_file)
    output_file = Path(output_file)
    if not input_file.is_absolute():
        input_file = BASE_DIR / input_file
    if not output_file.is_absolute():
        output_file = BASE_DIR / output_file

    if existing_data:
        data = copy.deepcopy(existing_data)
        data["language"] = data.get("language") or "en"
        data["grade"] = grade
        data["version"] = version
        data.setdefault("words", [])
        word_map = {normalize(item.get("word", "")): item for item in data["words"] if item.get("word")}
        word_id = max(max_word_id(data) + 1, word_id_start)
        print(f"기존 단어 {len(word_map)}개 유지, 새 단어 ID 시작: {word_id}")
        if generate_word_audio or upload_word_audio:
            for item in data["words"]:
                update_word_audio(
                    item,
                    audio_output_dir=audio_output_dir,
                    audio_storage_prefix=audio_storage_prefix,
                    upload_word_audio=upload_word_audio,
                    force_word_audio=force_word_audio,
                )
    else:
        data = {
            "language": "en",
            "grade": grade,
            "version": version,
            "words": [],
        }
        word_map = {}
        word_id = word_id_start

    audio_slug_map = {}

    with open(input_file, "r", encoding="utf-8") as f:
        for line in f:
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                continue

            parts = stripped.split("|")

            if len(parts) < 10:
                continue

            word = parts[0]
            key = normalize(word)

            existing_item = word_map.get(key)
            if existing_item and not update_existing:
                continue

            word_audio_url = ""
            if generate_word_audio or upload_word_audio:
                slug = audio_slug(word)
                previous_word = audio_slug_map.get(slug) or WORD_AUDIO_SLUGS.get(slug)
                if previous_word and normalize(previous_word) != key:
                    raise RuntimeError(
                        f"오디오 파일명 충돌: {previous_word!r}와 {word!r}가 모두 {slug}.mp3로 변환됩니다."
                    )
                audio_slug_map[slug] = word
                WORD_AUDIO_SLUGS[slug] = word

            item = {
                "wordId": word_id,
                "word": word,
                "type": parts[1] or "word",
                "pos": to_list(parts[2]),
                "phonetic": parts[3],
                "meanings": to_list(parts[4]),
                "meaningsEn": to_list(parts[5]),
                "synonyms": to_list(parts[6]),
                "antonyms": to_list(parts[7]),
                "exampleSentence": parts[8],
                "exampleTranslation": parts[9],
                "examples": [
                    {
                        "sentence": parts[8],
                        "translation": parts[9],
                    }
                ],
                "wordFamily": {
                    "prefix": "",
                    "prefixMeaning": "",
                    "relatedWords": [],
                },
                "sources": [
                    {
                        "book": "",
                        "day": "",
                        "section": "",
                        "originalNo": "",
                    }
                ],
                "wordAudioUrl": word_audio_url,
                "exampleAudioUrl": "",
            }

            if existing_item:
                item["wordId"] = existing_item.get("wordId", item["wordId"])
                item["wordAudioUrl"] = existing_item.get("wordAudioUrl", "")
                item["exampleAudioUrl"] = existing_item.get("exampleAudioUrl", "")

            if generate_word_audio or upload_word_audio:
                update_word_audio(
                    item,
                    audio_output_dir=audio_output_dir,
                    audio_storage_prefix=audio_storage_prefix,
                    upload_word_audio=upload_word_audio,
                    force_word_audio=force_word_audio,
                )

            if existing_item:
                existing_item.update(item)
            else:
                data["words"].append(item)
                word_map[key] = item
                word_id += 1

    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    print(f"완료: {output_file}")
    return output_file


def write_manifest(output_file, version, files):
    output_file = Path(output_file)
    if not output_file.is_absolute():
        output_file = BASE_DIR / output_file

    data = {
        "version": version,
        "files": [
            {
                "path": item["path"],
                "version": item["version"],
            }
            for item in files
        ],
    }

    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print(f"매니페스트 완료: {output_file}")
    return output_file


def copy_to_assets(local_file, output_name):
    target = ASSET_DATA_DIR / "en" / output_name
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(local_file, target)
    print(f"assets 갱신 완료: {target}")
    return target


def firebase_blob(destination_path):
    credentials_path = os.environ.get("GOOGLE_APPLICATION_CREDENTIALS")
    if not credentials_path and DEFAULT_CREDENTIALS_PATH.exists():
        credentials_path = str(DEFAULT_CREDENTIALS_PATH)
    bucket_name = os.environ.get("FIREBASE_STORAGE_BUCKET", DEFAULT_BUCKET)

    if not credentials_path:
        raise RuntimeError("GOOGLE_APPLICATION_CREDENTIALS 환경변수에 Firebase service account JSON 경로를 설정하세요.")

    try:
        import firebase_admin
        from firebase_admin import credentials, storage
    except ImportError as exc:
        raise RuntimeError(
            "firebase-admin 패키지가 필요합니다. "
            "`pip install -r tools/vocabulary/requirements.txt` 실행 후 다시 시도하세요."
        ) from exc

    if not firebase_admin._apps:
        cred = credentials.Certificate(credentials_path)
        firebase_admin.initialize_app(cred, {"storageBucket": bucket_name})

    return storage.bucket().blob(destination_path)


def upload_to_firebase_storage(local_file, destination_path, cache_control=None, content_type="application/json"):
    blob = firebase_blob(destination_path)
    if cache_control:
        blob.cache_control = cache_control
    blob.upload_from_filename(str(local_file), content_type=content_type)
    print(f"업로드 완료: gs://{blob.bucket.name}/{destination_path}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--grade", required=True)
    parser.add_argument("--version", type=int, default=1)
    parser.add_argument("--word-id-start", type=int, default=1001)
    parser.add_argument("--upload", action="store_true")
    parser.add_argument("--storage-prefix", default=os.environ.get("FIREBASE_STORAGE_PREFIX", DEFAULT_STORAGE_PREFIX))
    parser.add_argument("--storage-path")
    parser.add_argument("--update-assets", action="store_true")
    parser.add_argument("--generate-word-audio", action="store_true")
    parser.add_argument("--upload-word-audio", action="store_true")
    parser.add_argument("--force-word-audio", action="store_true")
    parser.add_argument("--audio-output-dir", default=DEFAULT_AUDIO_OUTPUT_DIR)
    parser.add_argument("--audio-storage-prefix", default=DEFAULT_AUDIO_STORAGE_PREFIX)
    parser.add_argument("--replace-remote", action="store_true")
    parser.add_argument("--update-existing", action="store_true")
    args = parser.parse_args()

    storage_path = args.storage_path or f"{args.storage_prefix}/{args.output}"
    existing_data = None
    if args.upload and not args.replace_remote:
        existing_data = load_existing_catalog_from_firebase(storage_path)

    output_file = generate(
        input_file=BASE_DIR / args.input,
        output_file=BASE_DIR / args.output,
        grade=args.grade,
        version=args.version,
        word_id_start=args.word_id_start,
        generate_word_audio=args.generate_word_audio,
        upload_word_audio=args.upload_word_audio,
        audio_output_dir=args.audio_output_dir,
        audio_storage_prefix=args.audio_storage_prefix,
        existing_data=existing_data,
        force_word_audio=args.force_word_audio,
        update_existing=args.update_existing,
    )
    if args.update_assets:
        copy_to_assets(output_file, args.output)
    if args.upload:
        upload_to_firebase_storage(output_file, storage_path, cache_control="public, max-age=300")


if __name__ == "__main__":
    main()
