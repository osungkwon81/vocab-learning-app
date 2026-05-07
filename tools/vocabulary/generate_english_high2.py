import argparse

from generate_vocabulary_json import copy_to_assets, generate, upload_to_firebase_storage


OUTPUT_FILE = "english_high2.json"
REMOTE_PATH = f"catalog/en/{OUTPUT_FILE}"
VERSION = 4


parser = argparse.ArgumentParser()
parser.add_argument("--version", type=int, default=VERSION)
parser.add_argument("--upload", action="store_true")
parser.add_argument("--storage-path", default=REMOTE_PATH)
parser.add_argument("--update-assets", action="store_true")
args = parser.parse_args()


output_file = generate(
    input_file="input_words_high2.txt",
    output_file=OUTPUT_FILE,
    grade="high2",
    version=args.version,
    word_id_start=500001,
)

if args.update_assets:
    copy_to_assets(output_file, OUTPUT_FILE)

if args.upload:
    upload_to_firebase_storage(output_file, args.storage_path, cache_control="public, max-age=300")
