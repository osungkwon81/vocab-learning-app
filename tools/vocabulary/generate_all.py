import argparse

from generate_vocabulary_json import copy_to_assets, generate, upload_to_firebase_storage, write_manifest


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
parser.add_argument("--version", type=int, default=4)
parser.add_argument("--upload", action="store_true")
parser.add_argument("--upload-manifest", action="store_true")
parser.add_argument("--update-assets", action="store_true")
parser.add_argument("--generate-word-audio", action="store_true")
parser.add_argument("--upload-word-audio", action="store_true")
args = parser.parse_args()


selected_vocabularies = [
    vocabulary
    for vocabulary in VOCABULARIES
    if args.grade == "all" or vocabulary["grade"] == args.grade
]
manifest_files = []

for vocabulary in selected_vocabularies:
    output_file = generate(
        input_file=vocabulary["input_file"],
        output_file=vocabulary["output_file"],
        grade=vocabulary["grade"],
        version=args.version,
        word_id_start=vocabulary["word_id_start"],
        generate_word_audio=args.generate_word_audio,
        upload_word_audio=args.upload_word_audio,
    )
    if args.update_assets:
        copy_to_assets(output_file, vocabulary["output_file"])

    if args.upload:
        remote_path = f"catalog/en/{vocabulary['output_file']}"
        upload_to_firebase_storage(output_file, remote_path, cache_control="public, max-age=300")

manifest_files = [
    {
        "path": f"catalog/en/{vocabulary['output_file']}",
        "version": args.version,
    }
    for vocabulary in VOCABULARIES
]
manifest_file = write_manifest(
    output_file="version.json",
    version=args.version,
    files=manifest_files,
)

if args.upload_manifest:
    upload_to_firebase_storage(manifest_file, "version.json", cache_control="no-cache")
