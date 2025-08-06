# main.py
from fastapi import FastAPI
from api.dedup_router import router as dedup_router

app = FastAPI()
app.include_router(dedup_router, prefix="/api/dedup", tags=["Deduplication"])
