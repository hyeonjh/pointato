import json
from pathlib import Path

from fastapi import FastAPI, HTTPException
from playwright.async_api import async_playwright

app = FastAPI()

PROFILE_DIR = "./profiles/naver"

BASE_DIR = Path(__file__).resolve().parent.parent
CONFIG_DIR = BASE_DIR / "configs" / "sites"


@app.get("/")
async def home():
    return {"message": "Pointato local agent running"}

@app.get("/naver/orders")
async def naver_orders():
    async with async_playwright() as p:
        context = await p.chromium.launch_persistent_context(
            PROFILE_DIR,
            headless=True
        )

        page = await context.new_page()

        try:
            await page.goto("https://order.pay.naver.com/home")
            await page.wait_for_timeout(3000)

            current_url = page.url

            if "nid.naver.com" in current_url:
                return {
                    "success": False,
                    "status": "login_required",
                    "message": "네이버 로그인이 필요합니다.",
                    "login_url": current_url
                }

            text = await page.locator("body").inner_text()

            return {
                "success": True,
                "status": "success",
                "url": current_url,
                "raw_text": text[:5000]
            }

        except Exception as e:
            return {
                "success": False,
                "status": "error",
                "message": str(e)
            }

        finally:
            await context.close()



@app.get("/api/sites/{site}/config")
async def get_site_config(site: str):
    config_path = CONFIG_DIR / f"{site}.json"

    if not config_path.exists():
        raise HTTPException(status_code=404, detail="site config not found")

    try:
        with config_path.open("r", encoding="utf-8") as f:
            return json.load(f)
    except json.JSONDecodeError:
        raise HTTPException(status_code=500, detail="invalid site config json")