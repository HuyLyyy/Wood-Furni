---
description: Quy trình deploy sau khi sửa code
alwaysApply: true
---

# Deploy

- Project này deploy qua Vercel, tự động sync khi có commit mới trên GitHub.
- Sau khi sửa code xong và test ổn, LUÔN thực hiện:
  1. `git add .`
  2. `git commit -m "mô tả ngắn gọn thay đổi"`
  3. `git push`
- Không cần chạy lệnh deploy thủ công — Vercel tự build khi push lên branch chính (thường là `main`).