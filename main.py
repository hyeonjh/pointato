from fastapi import FastAPI
from playwright.async_api import async_playwright

app = FastAPI()

PROFILE_DIR = "./profiles/naver"

@app.get("/")
async def home():
    return {"message": "Pointato local agent running"}

@app.get("/naver/orders")
async def naver_orders():

    async with async_playwright() as p:

        context = await p.chromium.launch_persistent_context(
            PROFILE_DIR,
            headless=False
        )

        page = await context.new_page()

        await page.goto("https://order.pay.naver.com/home")

        await page.wait_for_timeout(3000)

        current_url = page.url

        if "nid.naver.com" in current_url:
            return {
                "success": False,
                "status": "login_required",
                "message": "브라우저에서 네이버 로그인 후 다시 호출하세요.",
                "url": current_url
            }

        text = await page.locator("body").inner_text()

        await context.close()

        return {
            "success": True,
            "status": "done",
            "url": current_url,
            "raw_text": text[:5000]
        }