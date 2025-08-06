# config.py
from pathlib import Path
import os
from dotenv import load_dotenv

load_dotenv()

BASE_DIR = Path(os.getenv("BASE_STORAGE_DIR", "./default_storage"))

def get_date_folder(date, period):
    return f"{date}_{period}"

def get_file_path(period, date, category):
    return BASE_DIR / period / get_date_folder(date, period) / "detail" / f"naver_news_{category}_{period}_detailed.csv"

def get_dedup_dir(period, date):
    return BASE_DIR / period / get_date_folder(date, period) / "deduplicated-related"

def get_dedup_file_path(category, date, period):
    return get_dedup_dir(period, date) / f"deduplicated_{category}_{date}_{period}.csv"

def get_related_file_path(category, date, period):
    return get_dedup_dir(period, date) / f"related_{category}_{date}_{period}.csv"

def get_log_file_path(category, date, period):
    return get_dedup_dir(period, date) / f"logs_{category}_{date}_{period}.txt"
