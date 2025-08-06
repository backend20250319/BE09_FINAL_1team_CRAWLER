# api/dedup_router.py
from fastapi import APIRouter
from api.dedup_schema import DedupRequest, DedupResponse
from service.dedup_executor import run_dedup_pipeline

router = APIRouter()

@router.post("/run", response_model=DedupResponse)
def run_dedup(request: DedupRequest):
    result = run_dedup_pipeline(request)
    return DedupResponse(
        message="중복 제거 완료",
        result=result,
        dedupFile="",
        relatedFile="",
        logFile="",
        count=0
    )

@router.get("/status")
def get_status():
    return {"status": "중복 제거 서비스 정상 작동 중"}
