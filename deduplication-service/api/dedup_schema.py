# api/dedup_schema.py
from pydantic import BaseModel

class DedupRequest(BaseModel):
    category: str
    period: str  # "am" or "pm"
    date: str    # "YYYY-MM-DD"
    thresholdTitle: float = 0.3
    thresholdContent: float = 0.8
    thresholdRelatedMin: float = 0.4

class DedupResponse(BaseModel):
    message: str
    result: str
    dedupFile: str = None
    relatedFile: str = None
    logFile: str = None
    count: int = None
