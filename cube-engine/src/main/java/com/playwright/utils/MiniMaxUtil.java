package com.playwright.utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MiniMaxUtil {

    @Autowired
    private LogMsgUtil logInfo;

    public String waitMiniMaxHtmlDom(Page page, String userId, String aiName) {
        try {
            // 用于记录本轮和上一轮的回答 HTML
            String currentContent = "";
            String lastContent = "";

            // 超时时间：10 分钟
            long timeout = 600000;
            long startTime = System.currentTimeMillis();

            while (true) {
                // 如果超过超时，跳出
                if (System.currentTimeMillis() - startTime > timeout) {
                    System.out.println("超时，" + aiName + " 未完成回答！");
                    break;
                }

                // 检测 system-operation-box 是否出现（出现即表示 AI 说完了）
                boolean done = page.locator(".system-operation-box").count() > 0;

                // 提取当前最新的回答 HTML
                // 获取 hailuo-markdown 容器
                Locator container = page.locator("div.hailuo-markdown").last();

                // 提取所有不是 div.mb-3 的子元素（如 p、ul、ol 等）
                Locator children = container.locator("> :not(.mb-3)"); // 所有子元素排除mb-3（思考的文本）
                StringBuilder sb = new StringBuilder();

                for (int i = 0; i < children.count(); i++) {
                    Locator child = children.nth(i);
                    String className = child.getAttribute("class");

                    // 提取该子元素的 HTML
                    String outerHtml = child.innerHTML();

                    // 判断是否包含 target pending
                    if (outerHtml.contains("target pending")) {
                        sb.append(outerHtml);
                    }
                }

                currentContent = sb.toString();



                // 如果检测到“结束标志”且内容与上次一致，就退出循环
                if (done && currentContent.equals(lastContent)) {
                    logInfo.sendTaskLog(aiName + " 回答完成，正在提取内容", userId, aiName);
                    break;
                }

                // 保存当前内容，稍后对比
                lastContent = currentContent;

                // 等待 5 秒 再次检查
                page.waitForTimeout(5000);
            }

            logInfo.sendTaskLog(aiName + " 内容已提取完成", userId, aiName);

            // 简单清洗：去掉类似 <span><span>1</span></span> 这种序号
            currentContent = filterSpans(currentContent);
            return currentContent;

        } catch (Exception e) {
            e.printStackTrace();
            return "获取内容失败";
        }
    }
    public static String filterSpans(String html) {
        Document doc = Jsoup.parseBodyFragment(html);

        // 递归清理：移除所有不是 target pending 的元素
        cleanElement(doc.body());

        return doc.body().html();
    }

    // 递归清理非 "target pending" 的元素
    private static void cleanElement(Element element) {
        for (Element child : element.children()) {
            String classAttr = child.className();
            if (!"target pending".equals(classAttr)) {
                // 如果子元素不是 target pending，检查它的子元素是否有 target pending 的，有就保留递归，否则移除
                if (hasTargetPendingDescendant(child)) {
                    cleanElement(child); // 保留但继续清理子元素
                } else {
                    child.remove(); // 整个移除
                }
            } else {
                cleanElement(child); // 是目标类名，继续清理其子节点
            }
        }
    }

    // 判断某元素是否包含类名为 target pending 的后代
    private static boolean hasTargetPendingDescendant(Element element) {
        Elements descendants = element.getElementsByAttribute("class");
        for (Element el : descendants) {
            if ("target pending".equals(el.className())) {
                return true;
            }
        }
        return false;
    }


}

