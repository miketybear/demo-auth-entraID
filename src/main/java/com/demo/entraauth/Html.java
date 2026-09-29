package com.demo.entraauth;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

/** Tiny HTML helper so the demo needs no JSP/template engine. */
final class Html {
    private Html() {}

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '&': b.append("&amp;"); break;
                case '"': b.append("&quot;"); break;
                case '\'': b.append("&#39;"); break;
                default: b.append(c);
            }
        }
        return b.toString();
    }

    static void page(HttpServletResponse resp, String title, String body) throws IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter w = resp.getWriter();
        w.println("<!doctype html><html><head><meta charset='utf-8'><title>" + esc(title) + "</title>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:Segoe UI,Arial,sans-serif;max-width:900px;margin:40px auto;padding:0 16px;color:#222}"
                + "a.btn,button{display:inline-block;background:#0067b8;color:#fff;padding:10px 18px;border:0;border-radius:4px;text-decoration:none;font-size:15px;cursor:pointer}"
                + "a.btn.grey{background:#666}table{border-collapse:collapse;width:100%;font-size:13px}"
                + "td,th{border:1px solid #ddd;padding:6px;text-align:left;vertical-align:top;word-break:break-all}"
                + "th{background:#f3f3f3}.err{background:#fde7e9;border:1px solid #d13438;padding:12px}"
                + ".ok{background:#dff6dd;border:1px solid #107c10;padding:12px}code{background:#f3f3f3;padding:1px 4px}</style>"
                + "</head><body>");
        w.println(body);
        w.println("</body></html>");
    }
}
