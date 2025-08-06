# service/dedup_executor.py
from duplicate_detector.run_dedup import run_dedup_main

def run_dedup_pipeline(request):
    return run_dedup_main(
        category=request.category,
        period=request.period,
        date=request.date,
        threshold_title=request.thresholdTitle,
        threshold_content=request.thresholdContent,
        threshold_related_min=request.thresholdRelatedMin
    )
