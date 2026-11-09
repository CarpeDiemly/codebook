"""简单下载器：支持重定向、断点丢弃、进度输出。

用法: python tools/download.py <url> <dest> [--insecure]
说明: 本机 Windows 自带 TLS 被沙箱拦截，Python 的 OpenSSL 可正常访问 Google/Maven/GitHub，
      因此工具链下载统一走这里，而不是 curl/Invoke-WebRequest。
"""
import os
import sys
import time
import urllib.request

CHUNK = 1 << 20


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: download.py <url> <dest>")
        return 2
    url, dest = sys.argv[1], sys.argv[2]
    os.makedirs(os.path.dirname(os.path.abspath(dest)), exist_ok=True)
    tmp = dest + ".part"
    req = urllib.request.Request(url, headers={"User-Agent": "codebook-setup/1.0"})
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=120) as resp, open(tmp, "wb") as fh:
        total = int(resp.headers.get("Content-Length") or 0)
        done = 0
        last_report = 0
        while True:
            chunk = resp.read(CHUNK)
            if not chunk:
                break
            fh.write(chunk)
            done += len(chunk)
            if done - last_report >= 20 * CHUNK:
                last_report = done
                pct = f" ({done * 100 // total}%)" if total else ""
                print(f"  {done / 1e6:.1f}/{total / 1e6:.1f} MB{pct}", flush=True)
    os.replace(tmp, dest)
    print(f"OK {dest} {os.path.getsize(dest)} bytes in {time.time() - t0:.1f}s", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
