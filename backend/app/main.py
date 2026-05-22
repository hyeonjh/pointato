from fastapi import FastAPI, HTTPException
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



SITE_CONFIGS = {
    "naver": {
        "site": "naver",
        "displayName": "네이버",
        "orderPageUrl": "https://shopping.naver.com/my/order",
        "maxPage": 2,
        "selectors": {
            "orderCard": "[class*=OrderProductBundle_order_card]",
            "status": "strong[class*=OrderProduct_status]",
            "deliveryBlind": ".blind",
            "date": "[class*=OrderProductItem_date]",
            "name": "[class*=OrderProductItem_name]",
            "price": "strong[class*=OrderProductItem_price]",
            "productUrl": "a[class*=OrderProductItem_thumb_area]",
            "detailUrl": "a[class*=OrderProductItem_btn_detail]",
            "reviewButton": "[data-shp-contents-id='리뷰쓰기'], [data-shp-contents-id='한달사용리뷰']",
            "reviewReward": "[class*=UserActionButtons_text]",
            "pageButtons": "a, button"
        }
    }
}


@app.get("/api/sites/{site}/config")
async def get_site_config(site: str):
    config = SITE_CONFIGS.get(site)

    if not config:
        raise HTTPException(status_code=404, detail="site config not found")

    return config