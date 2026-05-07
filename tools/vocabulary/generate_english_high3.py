import argparse

from generate_vocabulary_json import copy_to_assets, generate, load_existing_catalog_from_firebase, upload_to_firebase_storage


OUTPUT_FILE = "english_high3.json"
REMOTE_PATH = f"catalog/en/{OUTPUT_FILE}"
VERSION = 4


parser = argparse.ArgumentParser()
parser.add_argument("--version", type=int, default=VERSION)
parser.add_argument("--upload", action="store_true")
parser.add_argument("--storage-path", default=REMOTE_PATH)
parser.add_argument("--update-assets", action="store_true")
parser.add_argument("--generate-word-audio", action="store_true")
parser.add_argument("--upload-word-audio", action="store_true")
parser.add_argument("--force-word-audio", action="store_true")
parser.add_argument("--replace-remote", action="store_true")
args = parser.parse_args()


existing_data = None
if args.upload and not args.replace_remote:
    existing_data = load_existing_catalog_from_firebase(args.storage_path)

output_file = generate(
    input_file="input_words_high3.txt",
    output_file=OUTPUT_FILE,
    grade="high3",
    version=args.version,
    word_id_start=600001,
    generate_word_audio=args.generate_word_audio,
    upload_word_audio=args.upload_word_audio,
    existing_data=existing_data,
    force_word_audio=args.force_word_audio,
)

if args.update_assets:
    copy_to_assets(output_file, OUTPUT_FILE)

if args.upload:
    upload_to_firebase_storage(output_file, args.storage_path, cache_control="public, max-age=300")
