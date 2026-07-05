import argparse
import json
from pathlib import Path

from generate_vocabulary_json import (
    copy_to_assets,
    generate,
    load_existing_catalog_from_firebase,
    load_existing_manifest_from_firebase,
    upload_to_firebase_storage,
    write_manifest,
)


VOCABULARIES = [
    {
        "input_file": "input_words_middle3.txt",
        "output_file": "english_middle3.json",
        "grade": "middle3",
        "word_id_start": 300001,
    },
    {
        "input_file": "input_words_high1.txt",
        "output_file": "english_high1.json",
        "grade": "high1",
        "word_id_start": 400001,
    },
    {
        "input_file": "input_words_high2.txt",
        "output_file": "english_high2.json",
        "grade": "high2",
        "word_id_start": 500001,
    },
    {
        "input_file": "input_words_high3.txt",
        "output_file": "english_high3.json",
        "grade": "high3",
        "word_id_start": 600001,
    },
]


parser = argparse.ArgumentParser()
parser.add_argument("--grade", choices=["all", "middle3", "high1", "high2", "high3"], default="all")
parser.add_argument("--version", type=int)
parser.add_argument("--upload", action="store_true")
parser.add_argument("--upload-manifest", action="store_true")
parser.add_argument("--update-assets", action="store_true")
parser.add_argument("--generate-word-audio", action="store_true")
parser.add_argument("--upload-word-audio", action="store_true")
parser.add_argument("--force-word-audio", action="store_true")
parser.add_argument("--check-existing-word-audio", action="store_true")
parser.add_argument("--source-book", default="")
parser.add_argument("--delete-list")
parser.add_argument("--replace-remote", action="store_true")
parser.add_argument("--update-existing", action="store_true")
args = parser.parse_args()


def load_local_manifest_version():
    manifest_path = Path(__file__).resolve().parent / "version.json"
    if not manifest_path.exists():
        return None

    with open(manifest_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    version = data.get("version")
    return version if isinstance(version, int) else None


def resolve_version():
    if args.version is not None:
        return args.version

    if args.upload_manifest and not args.replace_remote:
        try:
            remote_manifest = load_existing_manifest_from_firebase()
            remote_version = remote_manifest.get("version") if remote_manifest else None
            if isinstance(remote_version, int):
                return remote_version + 1
        except Exception as exc:
            print(f"원격 매니페스트 버전 조회 실패, 로컬 버전으로 대체: {exc}")

    local_version = load_local_manifest_version()
    if isinstance(local_version, int):
        return local_version + 1

    return 1


def load_delete_words(path):
    if not path:
        return []

    delete_path = Path(path)
    if not delete_path.is_absolute():
        delete_path = Path(__file__).resolve().parent / delete_path

    with open(delete_path, "r", encoding="utf-8") as f:
        return [
            line.strip()
            for line in f
            if line.strip() and not line.lstrip().startswith("#")
        ]


resolved_version = resolve_version()
delete_words = load_delete_words(args.delete_list)
print(f"사용 버전: {resolved_version}")


selected_vocabularies = [
    vocabulary
    for vocabulary in VOCABULARIES
    if args.grade == "all" or vocabulary["grade"] == args.grade
]
manifest_files = []

for vocabulary in selected_vocabularies:
    remote_path = f"catalog/en/{vocabulary['output_file']}"
    existing_data = None
    if args.upload and not args.replace_remote:
        existing_data = load_existing_catalog_from_firebase(remote_path)

    output_file = generate(
        input_file=vocabulary["input_file"],
        output_file=vocabulary["output_file"],
        grade=vocabulary["grade"],
        version=resolved_version,
        word_id_start=vocabulary["word_id_start"],
        generate_word_audio=args.generate_word_audio,
        upload_word_audio=args.upload_word_audio,
        existing_data=existing_data,
        force_word_audio=args.force_word_audio,
        update_existing=args.update_existing,
        check_existing_word_audio=args.check_existing_word_audio,
        source_book=args.source_book,
        delete_words=delete_words,
    )
    if args.update_assets:
        copy_to_assets(output_file, vocabulary["output_file"])

    if args.upload:
        upload_to_firebase_storage(output_file, remote_path, cache_control="public, max-age=300")

manifest_files = [
    {
        "path": f"catalog/en/{vocabulary['output_file']}",
        "version": resolved_version,
    }
    for vocabulary in VOCABULARIES
]
manifest_file = write_manifest(
    output_file="version.json",
    version=resolved_version,
    files=manifest_files,
)

if args.upload_manifest:
    upload_to_firebase_storage(manifest_file, "version.json", cache_control="no-cache")
