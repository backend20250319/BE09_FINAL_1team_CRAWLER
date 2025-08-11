# 모든 카테고리를 순서대로 처리하는 스크립트
import os
import sys
import time
from pathlib import Path

# 현재 디렉토리를 Python 경로에 추가
current_dir = Path(__file__).parent
sys.path.append(str(current_dir))

from config import ALL_CATEGORIES, PERIOD, DATE, THRESHOLD_TITLE, get_file_path, get_dedup_dir
from run_dedup import process_single_category

def main():
    print("모든 카테고리 중복 제거 시작!")
    print(f"날짜: {DATE}")
    print(f"시간대: {PERIOD}")
    print(f"총 카테고리 수: {len(ALL_CATEGORIES)}")
    print("=" * 50)
    
    total_start_time = time.time()
    success_count = 0
    failed_categories = []
    
    for i, category in enumerate(ALL_CATEGORIES, 1):
        print(f"\n[{i}/{len(ALL_CATEGORIES)}] {category} 카테고리 처리 중...")
        print("-" * 30)
        
        try:
            # 파일 존재 여부 확인
            file_path = get_file_path(PERIOD, DATE, category)
            if not file_path.exists():
                print(f"파일이 존재하지 않습니다: {file_path}")
                failed_categories.append(category)
                continue
            
            # 단일 카테고리 처리
            start_time = time.time()
            process_single_category(category, PERIOD, DATE, THRESHOLD_TITLE)
            end_time = time.time()
            
            print(f"{category} 완료! (소요시간: {end_time - start_time:.2f}초)")
            success_count += 1
            
        except Exception as e:
            print(f"{category} 처리 중 오류 발생: {str(e)}")
            failed_categories.append(category)
            continue
    
    total_end_time = time.time()
    
    print("\n" + "=" * 50)
    print("전체 처리 완료!")
    print(f"성공: {success_count}/{len(ALL_CATEGORIES)} 카테고리")
    print(f"실패: {len(failed_categories)} 카테고리")
    if failed_categories:
        print(f"실패한 카테고리: {', '.join(failed_categories)}")
    print(f"총 소요시간: {total_end_time - total_start_time:.2f}초")

if __name__ == "__main__":
    main()
