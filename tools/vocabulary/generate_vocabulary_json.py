import argparse
import json
import os
import re
import shutil
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parent
PROJECT_DIR = BASE_DIR.parents[1]
ASSET_DATA_DIR = PROJECT_DIR / "app" / "src" / "main" / "assets" / "data"
DEFAULT_STORAGE_PREFIX = "catalog/en"
DEFAULT_BUCKET = "vocab-learning-ff783.firebasestorage.app"
DEFAULT_CREDENTIALS_PATH = Path.home() / "key" / "vocab-learning-firebase-adminsdk.json"


def normalize(word):
    return re.sub(r"\s+", " ", word.strip().lower())


def to_list(value):
    return [v.strip() for v in value.split(",") if v.strip()] if value else []


def generate(input_file, output_file, grade, version, word_id_start=1001):
    input_file = Path(input_file)
    output_file = Path(output_file)
    if not input_file.is_absolute():
        input_file = BASE_DIR / input_file
    if not output_file.is_absolute():
        output_file = BASE_DIR / output_file

    data = {
        "language": "en",
        "grade": grade,
        "version": version,
        "words": [],
    }

    word_map = {}
    word_id = word_id_start

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

            # 중복 제거
            if key in word_map:
                continue

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
                "wordAudioUrl": "",
                "exampleAudioUrl": "",
            }

            data["words"].append(item)
            word_map[key] = True
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


def upload_to_firebase_storage(local_file, destination_path, cache_control=None):
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

    bucket = storage.bucket()
    blob = bucket.blob(destination_path)
    if cache_control:
        blob.cache_control = cache_control
    blob.upload_from_filename(str(local_file), content_type="application/json")
    print(f"업로드 완료: gs://{bucket_name}/{destination_path}")


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
    args = parser.parse_args()

    output_file = generate(
        input_file=BASE_DIR / args.input,
        output_file=BASE_DIR / args.output,
        grade=args.grade,
        version=args.version,
        word_id_start=args.word_id_start,
    )
    if args.update_assets:
        copy_to_assets(output_file, args.output)
    if args.upload:
        storage_path = args.storage_path or f"{args.storage_prefix}/{args.output}"
        upload_to_firebase_storage(output_file, storage_path, cache_control="public, max-age=300")


if __name__ == "__main__":
    main()
