# -*- coding: utf-8 -*-
"""给 README 截运行页：等页面把项目配置与开场白渲染出来再截，避免截到「加载中…」。

用法：
    python scripts/shoot-run-page.py <projectId> <输出文件> [追问]

不带追问时只截「开场白 + 输入框」的初始态；带追问时在输入框里发一句话，
等流式回答落定再截（需要真实模型密钥，否则截到的是模型调用失败）。
"""
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

BASE = "http://localhost:8080"


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        raise SystemExit(2)

    project_id = sys.argv[1]
    out = Path(sys.argv[2]).resolve()
    question = sys.argv[3] if len(sys.argv) > 3 else None
    out.parent.mkdir(parents=True, exist_ok=True)

    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(viewport={"width": 1000, "height": 760}, device_scale_factor=2)
        page.goto("%s/run/%s" % (BASE, project_id), wait_until="networkidle")

        # 开场白是页面拉到 /runtime 之后才 push 的，等它出现再截
        page.wait_for_selector("#messages .msg.bot", timeout=15000)

        if question:
            page.fill("#question", question)
            page.click("#send")
            # 等回答不再增长：连续两次读到同样的文本就认为流结束
            last, stable = "", 0
            for _ in range(120):
                page.wait_for_timeout(1000)
                text = page.eval_on_selector_all(
                    "#messages .msg.bot", "els => els.map(e => e.textContent).join('|')")
                if text and text == last:
                    stable += 1
                    if stable >= 3:
                        break
                else:
                    stable = 0
                last = text

        page.wait_for_timeout(500)
        page.screenshot(path=str(out))

        # 截图没法用断言回头看，所以把「这一屏上到底有什么」打出来：
        # 标题、副标题、每条消息的首行。截图内容与这里打印的不一致，就是截错了。
        print("标题：%s" % page.inner_text("#title"))
        print("副标题：%s" % page.inner_text("#subtitle"))
        for i, text in enumerate(page.eval_on_selector_all(
                "#messages .msg", "els => els.map(e => e.className + ' :: ' + e.textContent)"), 1):
            one_line = " ".join(text.split())
            print("消息%d：%s" % (i, one_line[:160]))
        print("截图尺寸：%s" % page.viewport_size)
        print("已截图：%s" % out)
        browser.close()


if __name__ == "__main__":
    main()
