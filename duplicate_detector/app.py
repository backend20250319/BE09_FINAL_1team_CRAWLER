# app.py
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from run_dedup import run_dedup_main  # 아래에서 만들 함수
import traceback

app = FastAPI()

class DedupRequest(BaseModel):
    category: str
    date: str
    period: str

@app.post("/deduplicate")
def deduplicate_news(req: DedupRequest):
    try:
        result = run_dedup_main(req.category, req.period, req.date)
        return {
            "status": "success",
            "message": "중복 제거 완료",
            "deduplicated_count": result["deduplicated_count"],
            "related_count": result["related_count"],
            "dedup_file": result["dedup_file"],
            "related_file": result["related_file"]
        }
    except Exception as e:
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))
